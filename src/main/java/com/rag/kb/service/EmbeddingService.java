package com.rag.kb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.base-url}")
    private String baseUrl;

    @Value("${app.openai.embedding-model}")
    private String model;

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
                // 批量返回：data 是数组
                for (JsonNode item : dataNode) {
                    results.add(parseEmbedding(item.get("embedding")));
                }
            } else {
                // 单条返回：data 是对象
                results.add(parseEmbedding(dataNode.get("embedding")));
                // 补齐 null（理论上不会发生）
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

    private float[] parseEmbedding(JsonNode arr) {
        float[] vec = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            vec[i] = (float) arr.get(i).asDouble();
        }
        return vec;
    }
}
