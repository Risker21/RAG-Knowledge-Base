package com.rag.kb.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("doc_chunk")
public class DocChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Long kbId;
    private Integer chunkIndex;
    private String content;
    private String embedding;   // JSON float 数组
}
