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
        // 1. 做 Embedding
        float[] queryVec = embeddingService.embed(question);

        if (queryVec == null) {
            ChatResponse resp = new ChatResponse();
            resp.setAnswer("⚠️ 嵌入向量服务不可用（API Key 未配置或调用失败），无法检索知识库内容。请检查 application.yml 中的 app.openai.api-key 配置后重启。");
            resp.setReferences(new ArrayList<>());
            resp.setConversationId(conversationId);
            saveMessages(conversationId, question, resp.getAnswer(), resp.getReferences());
            return resp;
        }

        // 2-4. 检索 + 构建 Prompt
        List<SearchResult> results = new ArrayList<>();
        RAGContext ctx = buildRAGContext(kbId, question, queryVec, results);

        // 5. 调用 LLM
        String answer = llmService.chat(ctx.systemPrompt, ctx.userMessage);

        // 6-9. 构建引用 + 保存
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

        return finalizeResponse(conversationId, question, answer, results);
    }

    private RAGContext buildRAGContext(Long kbId, String question, float[] queryVec, List<SearchResult> results) {
        results.addAll(vectorStore.search(kbId, queryVec, 5, 0.3));
        log.debug("RAG 检索到 {} 条结果", results.size());

        List<Document> kbDocs = documentMapper.selectList(
                lambdaQuery(Document.class).eq(Document::getKbId, kbId));
        String docContext = buildDocContext(kbDocs);

        String systemPrompt;
        String userMessage;

        if (results.isEmpty()) {
            if (kbDocs.isEmpty()) {
                systemPrompt = "You are a helpful AI assistant. Answer the user's question concisely and accurately in Chinese.";
            } else {
                systemPrompt = "你是知识库问答助手。当前知识库包含以下文档：\n"
                        + docContext
                        + "\n用户的问题可能是关于这些文档内容的。请根据你对这些技术领域的了解，尽力回答用户的问题。"
                        + "如果用户询问文档概况，请基于上述文档列表回答。";
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
        // 构建引用列表
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

        // 保存消息
        saveMessages(conversationId, question, finalAnswer, references);

        // 更新对话标题
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
