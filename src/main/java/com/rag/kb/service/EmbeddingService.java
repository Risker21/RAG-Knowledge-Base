package com.rag.kb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.base-url}")
    private String baseUrl;

    @Value("${app.openai.embedding-model}")
    private String model;

    @Value("${app.embedding.cache-ttl-hours:24}")
    private int cacheTtlHours;

    private static final String CACHE_PREFIX = "embedding:";

    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? null : results.get(0);
    }

    public List<float[]> embedBatch(List<String> texts) {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("你的KEY")) {
            log.warn("火山引擎 API Key 未配置，跳过 Embedding");
            List<float[]> nulls = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) nulls.add(null);
            return nulls;
        }

        List<float[]> results = new ArrayList<>();
        List<String> cacheMissTexts = new ArrayList<>();
        List<Integer> cacheMissIndices = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String cacheKey = CACHE_PREFIX + model + ":" + hashText(text);
            String cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                try {
                    results.add(objectMapper.readValue(cached, float[].class));
                    log.debug("Embedding 缓存命中: {}", text.substring(0, Math.min(30, text.length())));
                } catch (Exception e) {
                    results.add(null);
                    cacheMissTexts.add(text);
                    cacheMissIndices.add(i);
                }
            } else {
                results.add(null);
                cacheMissTexts.add(text);
                cacheMissIndices.add(i);
            }
        }

        if (!cacheMissTexts.isEmpty()) {
            log.debug("Embedding 缓存未命中 {} 条，调用 API", cacheMissTexts.size());
            List<float[]> apiResults = callEmbeddingApi(cacheMissTexts);

            for (int i = 0; i < cacheMissIndices.size(); i++) {
                int originalIndex = cacheMissIndices.get(i);
                float[] vec = apiResults.get(i);
                results.set(originalIndex, vec);

                if (vec != null) {
                    String text = cacheMissTexts.get(i);
                    String cacheKey = CACHE_PREFIX + model + ":" + hashText(text);
                    try {
                        redisTemplate.opsForValue().set(cacheKey,
                                objectMapper.writeValueAsString(vec),
                                cacheTtlHours, TimeUnit.HOURS);
                    } catch (Exception e) {
                        log.warn("缓存 Embedding 失败: {}", e.getMessage());
                    }
                }
            }
        }

        return results;
    }

    private List<float[]> callEmbeddingApi(List<String> texts) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            List<Map<String, String>> inputList = new ArrayList<>();
            for (String text : texts) {
                Map<String, String> item = new HashMap<>();
                item.put("type", "text");
                item.put("text", text);
                inputList.add(item);
            }
            body.put("input", inputList);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = baseUrl + "/embeddings/multimodal";
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode dataNode = root.get("data");

            List<float[]> results = new ArrayList<>();
            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    results.add(parseEmbedding(item.get("embedding")));
                }
            } else {
                results.add(parseEmbedding(dataNode.get("embedding")));
                while (results.size() < texts.size()) results.add(null);
            }
            return results;
        } catch (Exception e) {
            log.warn("批量 Embedding API 调用失败: {}", e.getMessage());
            List<float[]> nulls = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) nulls.add(null);
            return nulls;
        }
    }

    private String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 32);
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    private float[] parseEmbedding(JsonNode arr) {
        float[] vec = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            vec[i] = (float) arr.get(i).asDouble();
        }
        return vec;
    }
}
