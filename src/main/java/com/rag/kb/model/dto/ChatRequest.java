package com.rag.kb.model.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long conversationId;
    private Long kbId;
    private String question;
}
