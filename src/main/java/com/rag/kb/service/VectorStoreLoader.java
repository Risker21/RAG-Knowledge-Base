package com.rag.kb.service;

import com.rag.kb.mapper.DocChunkMapper;
import com.rag.kb.model.entity.DocChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreLoader implements CommandLineRunner {

    private final DocChunkMapper docChunkMapper;
    private final VectorStore vectorStore;

    @Override
    public void run(String... args) {
        List<DocChunk> allChunks = docChunkMapper.selectList(null);
        if (!allChunks.isEmpty()) {
            vectorStore.storeFromDb(allChunks);
            log.info("启动后自动加载 {} 个文档切片到内存向量库（来自 {} 个知识库）",
                    allChunks.size(),
                    allChunks.stream().map(DocChunk::getKbId).distinct().count());
        } else {
            log.info("数据库中没有文档切片，向量库为空");
        }
    }
}
