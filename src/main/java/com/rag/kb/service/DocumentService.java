package com.rag.kb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.mapper.DocChunkMapper;
import com.rag.kb.mapper.DocumentMapper;
import com.rag.kb.model.entity.DocChunk;
import com.rag.kb.model.entity.Document;
import com.rag.kb.service.TextChunker.Chunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final DocChunkMapper docChunkMapper;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Path.of(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create upload dir", e);
        }
    }

    public Document upload(MultipartFile file, Long kbId, Long userId) throws IOException {
        String filename = file.getOriginalFilename();
        String fileType = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();

        // 保存文件
        String storageName = UUID.randomUUID() + "_" + filename;
        Path targetPath = Path.of(uploadDir, storageName);
        file.transferTo(targetPath);

        // 创建文档记录
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setUserId(userId);
        doc.setOriginalName(filename);
        doc.setFileType(fileType);
        doc.setFilePath(targetPath.toString());
        doc.setFileSize(file.getSize());
        doc.setStatus(0); // 待处理
        documentMapper.insert(doc);

        // 处理文档（异常捕获，不影响上传结果）
        try {
            processDocument(doc);
        } catch (Exception e) {
            log.warn("文档处理失败: {}. 文件已保存，稍后可重试。", e.getMessage());
            doc.setStatus(3); // 失败
            doc.setErrorMsg(e.getMessage());
            documentMapper.updateById(doc);
        }

        return doc;
    }

    @Transactional
    protected void processDocument(Document doc) throws Exception {
        doc.setStatus(1); // 处理中
        documentMapper.updateById(doc);

        File file = new File(doc.getFilePath());
        String text = documentParser.parse(file, doc.getFileType());

        // 切片
        List<Chunk> chunks = textChunker.split(text, doc.getOriginalName());
        if (chunks.isEmpty()) {
            doc.setStatus(2);
            doc.setChunkCount(0);
            documentMapper.updateById(doc);
            return;
        }

        // 批量 Embedding（在文本前加上文档名，让向量包含文档身份信息）
        String sourceTag = "【" + doc.getOriginalName() + "】";
        List<String> texts = chunks.stream()
                .map(chunk -> sourceTag + "\n" + chunk.getContent())
                .toList();
        List<float[]> embeddings = embeddingService.embedBatch(texts);

        // 批量入库
        List<DocChunk> entities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            float[] embedding = embeddings.get(i);
            if (embedding == null) continue; // 跳过失败的 embedding

            Chunk chunk = chunks.get(i);
            DocChunk entity = new DocChunk();
            entity.setDocId(doc.getId());
            entity.setKbId(doc.getKbId());
            entity.setChunkIndex(chunk.getIndex());
            entity.setContent(sourceTag + "\n" + chunk.getContent());
            entity.setEmbedding(objectMapper.writeValueAsString(embedding));
            docChunkMapper.insert(entity);
            entities.add(entity);
        }

        // 加载到内存向量库（带 kbId 分区）
        for (DocChunk e : entities) {
            float[] emb = objectMapper.readValue(e.getEmbedding(), float[].class);
            vectorStore.store(e.getId(), emb, e.getContent(),
                    doc.getId(), e.getChunkIndex(), doc.getOriginalName(), doc.getKbId());
        }

        doc.setStatus(2); // 完成
        doc.setChunkCount(entities.size());
        documentMapper.updateById(doc);
    }

    public List<Document> listByKb(Long kbId) {
        return documentMapper.selectList(
                Wrappers.lambdaQuery(Document.class)
                        .eq(Document::getKbId, kbId)
                        .orderByDesc(Document::getCreatedAt));
    }

    public List<Document> listByKbAndUser(Long kbId, Long userId) {
        return documentMapper.selectList(
                Wrappers.lambdaQuery(Document.class)
                        .eq(Document::getKbId, kbId)
                        .eq(Document::getUserId, userId)
                        .orderByDesc(Document::getCreatedAt));
    }

    public Document getById(Long id) {
        return documentMapper.selectById(id);
    }

    public Document getByIdAndUser(Long id, Long userId) {
        return documentMapper.selectOne(
                Wrappers.lambdaQuery(Document.class)
                        .eq(Document::getId, id)
                        .eq(Document::getUserId, userId));
    }

    public void delete(Long id, Long userId) {
        Document doc = getByIdAndUser(id, userId);
        if (doc != null) {
            File file = new File(doc.getFilePath());
            if (file.exists()) file.delete();
            docChunkMapper.delete(
                    Wrappers.lambdaQuery(DocChunk.class)
                            .eq(DocChunk::getDocId, id));
            vectorStore.removeByDocId(id);
            documentMapper.deleteById(id);
        }
    }

    public void loadKbChunksToMemory(Long kbId) {
        List<DocChunk> chunks = docChunkMapper.selectList(
                Wrappers.lambdaQuery(DocChunk.class)
                        .eq(DocChunk::getKbId, kbId));
        vectorStore.storeFromDb(chunks);
    }
}
