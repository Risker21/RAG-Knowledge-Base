package com.rag.kb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.model.entity.DocChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VectorStore {

    /** kbId -> (chunkId -> vector) */
    private final Map<Long, Map<Long, float[]>> kbVectors = new ConcurrentHashMap<>();
    private final Map<Long, String> chunkTexts = new ConcurrentHashMap<>();
    private final Map<Long, String> chunkMeta = new ConcurrentHashMap<>();
    /** chunkId -> docId，用于文档级删除 */
    private final Map<Long, Long> chunkDocMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void store(Long chunkId, float[] vector, String text, Long docId, int chunkIndex, String filename, Long kbId) {
        kbVectors.computeIfAbsent(kbId, k -> new ConcurrentHashMap<>()).put(chunkId, vector);
        chunkTexts.put(chunkId, text);
        chunkMeta.put(chunkId, filename + ":" + chunkIndex);
        chunkDocMap.put(chunkId, docId);
    }

    public void storeFromDb(List<DocChunk> chunks) {
        int loaded = 0;
        for (DocChunk chunk : chunks) {
            try {
                float[] vec = objectMapper.readValue(chunk.getEmbedding(), float[].class);
                Long kbId = chunk.getKbId();
                kbVectors.computeIfAbsent(kbId, k -> new ConcurrentHashMap<>()).put(chunk.getId(), vec);
                chunkTexts.put(chunk.getId(), chunk.getContent());
                chunkMeta.put(chunk.getId(), chunk.getDocId() + ":" + chunk.getChunkIndex());
                chunkDocMap.put(chunk.getId(), chunk.getDocId());
                loaded++;
            } catch (Exception e) {
                // skip malformed
            }
        }
        log.info("从数据库加载 {} 个文档切片到内存向量库", loaded);
    }

    public List<SearchResult> search(Long kbId, float[] queryVec, int topK, double threshold) {
        Map<Long, float[]> vectors = kbVectors.get(kbId);
        if (vectors == null || vectors.isEmpty()) {
            return Collections.emptyList();
        }
        return vectors.entrySet().stream()
                .map(e -> new SearchResult(e.getKey(), cosineSimilarity(queryVec, e.getValue()),
                        chunkTexts.get(e.getKey()), chunkMeta.get(e.getKey())))
                .filter(r -> r.score >= threshold)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .collect(Collectors.toList());
    }

    public void removeByDocId(Long docId) {
        // 通过 chunkDocMap 找到所有属于该文档的 chunkId
        List<Long> toRemove = chunkDocMap.entrySet().stream()
                .filter(e -> e.getValue().equals(docId))
                .map(Map.Entry::getKey)
                .toList();

        for (Long chunkId : toRemove) {
            chunkTexts.remove(chunkId);
            chunkMeta.remove(chunkId);
            chunkDocMap.remove(chunkId);
            for (Map<Long, float[]> kbMap : kbVectors.values()) {
                kbMap.remove(chunkId);
            }
        }
        kbVectors.values().removeIf(Map::isEmpty);
        log.debug("从向量库移除文档 docId={} 的 {} 个切片", docId, toRemove.size());
    }

    public int totalChunks() {
        return chunkTexts.size();
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static class SearchResult {
        private Long chunkId;
        private double score;
        private String text;
        private String meta;

        public SearchResult(Long chunkId, double score, String text, String meta) {
            this.chunkId = chunkId;
            this.score = score;
            this.text = text;
            this.meta = meta;
        }

        public Long getChunkId() { return chunkId; }
        public double getScore() { return score; }
        public String getText() { return text; }
        public String getMeta() { return meta; }
    }
}
