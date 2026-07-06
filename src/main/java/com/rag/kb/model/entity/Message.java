package com.rag.kb.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Integer role;        // 0=用户 1=AI
    private String content;
    private String referencesJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
