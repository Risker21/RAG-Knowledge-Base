package com.rag.kb.service;

import com.rag.kb.service.VectorStore.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptTemplate {

    public String buildSystemPrompt(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名专业知识基础助理。你可以根据提供的文档摘录回答用户的问题。\n\n");
        sb.append("RULES:\n");
        sb.append("1. Only use information from the provided document excerpts\n");
        sb.append("2. If the answer is not in the excerpts, say 'The document does not contain this information'\n");
        sb.append("3. Mark sources at the end of each answer like [Source N]\n");
        sb.append("4. Be concise and accurate\n\n");
        sb.append("=== DOCUMENT EXCERPTS ===\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("[Source ").append(i + 1).append("]\n");
            sb.append("From: ").append(r.getMeta()).append("\n");
            sb.append(r.getText()).append("\n\n");
        }

        return sb.toString();
    }

    public String buildUserMessage(String question) {
        return "Question: " + question + "\n\nPlease answer based on the document excerpts above.";
    }
}
