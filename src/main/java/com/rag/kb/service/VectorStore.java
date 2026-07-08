package com.rag.kb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.model.entity.DocChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class VectorStore {

    /** kbId -> (chunkId -> vector) */
    private final Map<Long, Map<Long, float[]>> kbVectors = new ConcurrentHashMap<>();
    /** chunkId -> 预计算的向量范数 */
    private final Map<Long, Double> vectorNorms = new ConcurrentHashMap<>();
    private final Map<Long, String> chunkTexts = new ConcurrentHashMap<>();
    private final Map<Long, String> chunkMeta = new ConcurrentHashMap<>();
    /** chunkId -> docId，用于文档级删除 */
    private final Map<Long, Long> chunkDocMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void store(Long chunkId, float[] vector, String text, Long docId, int chunkIndex, String filename, Long kbId) {
        kbVectors.computeIfAbsent(kbId, k -> new ConcurrentHashMap<>()).put(chunkId, vector);
        vectorNorms.put(chunkId, computeNorm(vector));
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
                vectorNorms.put(chunk.getId(), computeNorm(vec));
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

        double queryNorm = computeNorm(queryVec);
        if (queryNorm == 0) {
            return Collections.emptyList();
        }

        PriorityQueue<SearchResult> minHeap = new PriorityQueue<>(topK,
                Comparator.comparingDouble(SearchResult::getScore));

        for (Map.Entry<Long, float[]> entry : vectors.entrySet()) {
            Long chunkId = entry.getKey();
            float[] vec = entry.getValue();
            Double vecNorm = vectorNorms.get(chunkId);

            if (vecNorm == null || vecNorm == 0) {
                continue;
            }

            double score = dotProduct(queryVec, vec) / (queryNorm * vecNorm);

            if (score >= threshold) {
                SearchResult result = new SearchResult(chunkId, score,
                        chunkTexts.get(chunkId), chunkMeta.get(chunkId));

                if (minHeap.size() < topK) {
                    minHeap.offer(result);
                } else if (score > minHeap.peek().getScore()) {
                    minHeap.poll();
                    minHeap.offer(result);
                }
            }
        }

        List<SearchResult> results = new ArrayList<>(minHeap);
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results;
    }

    public void removeByDocId(Long docId) {
        List<Long> toRemove = chunkDocMap.entrySet().stream()
                .filter(e -> e.getValue().equals(docId))
                .map(Map.Entry::getKey)
                .toList();

        for (Long chunkId : toRemove) {
            chunkTexts.remove(chunkId);
            chunkMeta.remove(chunkId);
            chunkDocMap.remove(chunkId);
            vectorNorms.remove(chunkId);
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

    private double computeNorm(float[] vec) {
        double sum = 0;
        for (float v : vec) {
            sum += (double) v * v;
        }
        return Math.sqrt(sum);
    }

    private double dotProduct(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
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
