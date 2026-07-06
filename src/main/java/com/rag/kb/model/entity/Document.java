package com.rag.kb.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long userId;
    @TableField("filename")
    private String originalName;
    private String fileType;
    private String filePath;
    private Long fileSize;
    private Integer status;      // 0=待处理 1=处理中 2=已完成 3=失败
    private Integer chunkCount;
    private String errorMsg;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
