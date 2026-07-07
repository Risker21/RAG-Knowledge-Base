package com.rag.kb.service;

import com.rag.kb.service.VectorStore.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptTemplate {

    public String buildSystemPrompt(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名专业的知识库问答助手。请根据提供的文档摘录，用中文准确、简洁地回答用户的问题。\n\n");
        sb.append("回答规则：\n");
        sb.append("1. 只能使用提供的文档摘录中的信息进行回答\n");
        sb.append("2. 如果文档中没有相关信息，请用友好的语气说明，例如：\"抱歉，当前文档中未找到相关信息，请尝试其他问题或上传相关文档。\"\n");
        sb.append("3. 在回答的末尾标注来源，格式为 [来源N]，其中 N 为文档编号\n");
        sb.append("4. 回答要简洁准确，避免冗长\n\n");
        sb.append("=== 文档摘录 ===\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("[来源").append(i + 1).append("]\n");
            sb.append("来源：").append(r.getMeta()).append("\n");
            sb.append(r.getText()).append("\n\n");
        }

        return sb.toString();
    }

    public String buildUserMessage(String question) {
        return "Question: " + question + "\n\nPlease answer based on the document excerpts above.";
    }
}
