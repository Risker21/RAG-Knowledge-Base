package com.rag.kb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.mapper.ConversationMapper;
import com.rag.kb.mapper.DocumentMapper;
import com.rag.kb.mapper.MessageMapper;
import com.rag.kb.model.dto.ChatResponse;
import com.rag.kb.model.entity.Conversation;
import com.rag.kb.model.entity.Document;
import com.rag.kb.model.entity.Message;
import com.rag.kb.service.VectorStore.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final LlmService llmService;
    private final PromptTemplate promptTemplate;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final DocumentMapper documentMapper;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    private static final Pattern SOURCE_PATTERN = Pattern.compile("\\[来源(\\d+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```\\w*\\s*([\\s\\S]*?)```", Pattern.DOTALL);

    public Conversation createConversation(Long userId, Long kbId, String title) {
        Conversation conv = new Conversation();
        conv.setUserId(userId);
        conv.setKbId(kbId);
        conv.setTitle(title != null ? title : "New Chat");
        conversationMapper.insert(conv);
        return conv;
    }

    public List<Conversation> listConversations(Long userId, Long kbId) {
        return conversationMapper.selectList(
                lambdaQuery(Conversation.class)
                        .eq(Conversation::getUserId, userId)
                        .eq(Conversation::getKbId, kbId)
                        .orderByDesc(Conversation::getCreatedAt));
    }

    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null)
            throw new RuntimeException("对话不存在");
        if (!conv.getUserId().equals(userId))
            throw new RuntimeException("无权删除此对话");
        messageMapper.delete(lambdaQuery(Message.class)
                .eq(Message::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    public Conversation getConversationById(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    public Conversation getConversationByIdAndUser(Long conversationId, Long userId) {
        return conversationMapper.selectOne(
                lambdaQuery(Conversation.class)
                        .eq(Conversation::getId, conversationId)
                        .eq(Conversation::getUserId, userId));
    }

    public List<Message> getMessages(Long conversationId, Long userId) {
        Conversation conv = getConversationByIdAndUser(conversationId, userId);
        if (conv == null) {
            throw new RuntimeException("无权访问此对话");
        }
        return messageMapper.selectList(
                lambdaQuery(Message.class)
                        .eq(Message::getConversationId, conversationId)
                        .orderByAsc(Message::getCreatedAt));
    }

    public ChatResponse ask(Long conversationId, Long kbId, String question) {
        float[] queryVec = embeddingService.embed(question);

        if (queryVec == null) {
            ChatResponse resp = new ChatResponse();
            resp.setAnswer("⚠️ 嵌入向量服务不可用（API Key 未配置或调用失败），无法检索知识库内容。请检查 application.yml 中的 app.openai.api-key 配置后重启。");
            resp.setReferences(new ArrayList<>());
            resp.setConversationId(conversationId);
            saveMessages(conversationId, question, resp.getAnswer(), resp.getReferences());
            return resp;
        }

        List<SearchResult> results = new ArrayList<>();
        RAGContext ctx = buildRAGContext(kbId, question, queryVec, results);

        String answer = llmService.chat(ctx.systemPrompt, ctx.userMessage);

        answer = validateAndCleanAnswer(answer, results);

        return finalizeResponse(conversationId, question, answer, results);
    }

    public ChatResponse askStream(Long conversationId, Long kbId, String question, Consumer<String> onToken) {
        float[] queryVec = embeddingService.embed(question);

        if (queryVec == null) {
            String msg = "⚠️ 嵌入向量服务不可用";
            onToken.accept(msg);
            ChatResponse resp = new ChatResponse();
            resp.setAnswer(msg);
            resp.setReferences(new ArrayList<>());
            resp.setConversationId(conversationId);
            saveMessages(conversationId, question, msg, resp.getReferences());
            return resp;
        }

        List<SearchResult> results = new ArrayList<>();
        RAGContext ctx = buildRAGContext(kbId, question, queryVec, results);

        String answer = llmService.chatStream(ctx.systemPrompt, ctx.userMessage, onToken);

        answer = validateAndCleanAnswer(answer, results);

        return finalizeResponse(conversationId, question, answer, results);
    }

    private String validateAndCleanAnswer(String answer, List<SearchResult> results) {
        if (answer == null || answer.trim().isEmpty()) {
            return "😔 AI 服务返回内容为空，请稍后重试";
        }

        String cleaned = answer.trim();

        cleaned = cleanHtmlTags(cleaned);

        cleaned = fixCodeBlocks(cleaned);

        cleaned = validateSourceReferences(cleaned, results.size());

        if (cleaned.length() > 10000) {
            log.warn("回答内容过长，截断至 10000 字符");
            cleaned = cleaned.substring(0, 10000) + "\n\n（内容过长已截断）";
        }

        return cleaned;
    }

    private String cleanHtmlTags(String content) {
        if (content.contains("<span") || content.contains("<div") || content.contains("<p")) {
            log.debug("检测到 HTML 标签，尝试清理");
            String textOnly = HTML_TAG_PATTERN.matcher(content).replaceAll("");
            textOnly = textOnly.replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                    .replaceAll("&amp;", "&").replaceAll("&quot;", "\"");
            if (!textOnly.isEmpty() && textOnly.length() < content.length()) {
                return textOnly;
            }
        }
        return content;
    }

    private String fixCodeBlocks(String content) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        if (matcher.find()) {
            return content;
        }
        if (content.contains("import java.") || content.contains("public class")
                || content.contains("function ") || content.contains("def ")
                || content.contains("console.log")) {
            log.debug("检测到代码内容但缺少代码块标记，尝试包裹");
            content = "```java\n" + content + "\n```";
        }
        return content;
    }

    private String validateSourceReferences(String content, int maxSourceIndex) {
        Matcher matcher = SOURCE_PATTERN.matcher(content);
        boolean hasInvalidSource = false;

        while (matcher.find()) {
            int sourceIndex = Integer.parseInt(matcher.group(1));
            if (sourceIndex > maxSourceIndex) {
                log.warn("检测到无效的来源引用 [来源{}]，最大有效来源为 {}", sourceIndex, maxSourceIndex);
                hasInvalidSource = true;
                break;
            }
        }

        if (hasInvalidSource) {
            content = SOURCE_PATTERN.matcher(content).replaceAll("[来源]");
        }

        if (maxSourceIndex > 0 && !SOURCE_PATTERN.matcher(content).find()) {
            log.debug("检索到 {} 条结果但回答中未标注来源", maxSourceIndex);
        }

        return content;
    }

    private RAGContext buildRAGContext(Long kbId, String question, float[] queryVec, List<SearchResult> results) {
        List<SearchResult> rawResults = vectorStore.search(kbId, queryVec, 5, 0.3);

        for (SearchResult r : rawResults) {
            if (r.getScore() >= 0.3) {
                results.add(r);
            } else {
                log.debug("跳过低相似度结果 (score={}): {}", r.getScore(),
                        r.getText().substring(0, Math.min(50, r.getText().length())));
            }
        }

        log.debug("RAG 检索到 {} 条有效结果", results.size());

        List<Document> kbDocs = documentMapper.selectList(
                lambdaQuery(Document.class).eq(Document::getKbId, kbId));
        String docContext = buildDocContext(kbDocs);

        String systemPrompt;
        String userMessage;

        if (results.isEmpty()) {
            if (kbDocs.isEmpty()) {
                systemPrompt = "你是一个友好的 AI 助手。请用中文简洁准确地回答用户的问题。"
                        + "\n注意：当前知识库为空，请基于你的训练知识回答，但请明确说明这是基于你的知识，不是来自知识库。";
            } else {
                systemPrompt = "你是知识库问答助手。当前知识库包含以下文档：\n"
                        + docContext
                        + "\n\n重要提示：当前未检索到与问题直接相关的文档片段。"
                        + "如果用户的问题可以从上述文档列表中推断，请基于文档列表回答；"
                        + "否则请明确说明文档中没有相关信息，并基于你的知识提供参考回答，但要清楚区分哪些是来自文档的，哪些是来自你的知识。";
            }
            userMessage = question;
        } else {
            systemPrompt = promptTemplate.buildSystemPrompt(results) + "\n\n当前知识库中的文档列表：\n" + docContext;
            userMessage = promptTemplate.buildUserMessage(question);
        }

        RAGContext ctx = new RAGContext();
        ctx.systemPrompt = systemPrompt;
        ctx.userMessage = userMessage;
        return ctx;
    }

    private static class RAGContext {
        String systemPrompt;
        String userMessage;
    }

    private ChatResponse finalizeResponse(Long conversationId, String question, String answer,
            List<SearchResult> results) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            Map<String, Object> ref = new HashMap<>();
            ref.put("index", i + 1);
            ref.put("source", r.getMeta());
            ref.put("snippet", r.getText().length() > 200 ? r.getText().substring(0, 200) + "..." : r.getText());
            ref.put("score", Math.round(r.getScore() * 100) / 100.0);
            references.add(ref);
        }

        String finalAnswer = answer;

        if (results.isEmpty() && !answer.contains("未找到") && !answer.contains("暂无")) {
            finalAnswer = answer + "\n\n💡 提示：以上回答基于 AI 的训练知识，可能不完全准确。建议上传相关文档以获取更精确的答案。";
        }

        saveMessages(conversationId, question, finalAnswer, references);

        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null && "New Chat".equals(conv.getTitle())) {
            conv.setTitle(question.length() > 50 ? question.substring(0, 50) + "..." : question);
            conversationMapper.updateById(conv);
        }

        ChatResponse resp = new ChatResponse();
        resp.setAnswer(finalAnswer);
        resp.setReferences(references);
        resp.setConversationId(conversationId);
        return resp;
    }

    /** 构建文档列表上下文文本 */
    private String buildDocContext(List<Document> docs) {
        if (docs.isEmpty())
            return "（当前知识库暂无文档）";
        return docs.stream()
                .map(d -> "  - " + d.getOriginalName()
                        + "（" + formatFileSize(d.getFileSize()) + "）"
                        + (d.getChunkCount() != null ? "，" + d.getChunkCount() + " 个切片" : ""))
                .collect(Collectors.joining("\n"));
    }

    private String formatFileSize(Long size) {
        if (size == null)
            return "未知大小";
        if (size < 1024)
            return size + " B";
        if (size < 1024 * 1024)
            return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024));
    }

    private void saveMessages(Long conversationId, String question, String answer,
            List<Map<String, Object>> references) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null
                && (conv.getTitle() == null || conv.getTitle().isEmpty() || "New Chat".equals(conv.getTitle()))) {
            conv.setTitle(question.length() > 30 ? question.substring(0, 30) + "..." : question);
            conversationMapper.updateById(conv);
        }

        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setRole(0);
        userMsg.setContent(question);
        messageMapper.insert(userMsg);

        Message aiMsg = new Message();
        aiMsg.setConversationId(conversationId);
        aiMsg.setRole(1);
        aiMsg.setContent(answer);
        try {
            aiMsg.setReferencesJson(
                    objectMapper.writeValueAsString(references != null ? references : new ArrayList<>()));
        } catch (JsonProcessingException e) {
            aiMsg.setReferencesJson("[]");
        }
        messageMapper.insert(aiMsg);
    }
}
