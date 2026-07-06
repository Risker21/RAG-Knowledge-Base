package com.rag.kb.model.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChatResponse {
    private String answer;
    private List<Map<String, Object>> references;
    private Long conversationId;
}
