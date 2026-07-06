package com.rag.kb.model.dto;

import lombok.Data;

@Data
public class RegisterDto {
    private String username;
    private String password;
    private String confirmPassword;
}
