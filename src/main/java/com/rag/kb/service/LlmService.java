package com.rag.kb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.base-url}")
    private String baseUrl;

    @Value("${app.openai.chat-model}")
    private String model;

    public String chat(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("你的KEY")) {
            return "【提示】OpenRouter API Key 未配置，无法调用 AI 模型。请在 application.yml 中设置 app.openrouter.api-key 后重启。";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            List<Map<String, String>> messages = buildMessages(systemPrompt, userMessage);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.3);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.get("choices").get(0).get("message").get("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("LLM API call failed: " + e.getMessage(), e);
        }
    }

    public String chatStream(String systemPrompt, String userMessage, Consumer<String> onToken) {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("你的KEY")) {
            String msg = "API Key 未配置";
            onToken.accept(msg);
            return msg;
        }
        try {
            List<Map<String, String>> messages = buildMessages(systemPrompt, userMessage);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", 0.3);
            body.put("stream", true);

            byte[] requestBody = objectMapper.writeValueAsBytes(body);

            HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/chat/completions").toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(0);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody);
            }

            StringBuilder fullText = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) break;
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode choice = node.path("choices").get(0);
                            if (choice == null) continue;
                            String finish = choice.path("finish_reason").asText();
                            if ("stop".equals(finish)) break;
                            String token = choice.path("delta").path("content").asText();
                            if (token != null && !token.isEmpty()) {
                                fullText.append(token);
                                onToken.accept(token);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            return fullText.toString();
        } catch (Exception e) {
            log.error("Streaming LLM call failed", e);
            String error = "LLM 调用失败: " + e.getMessage();
            onToken.accept(error);
            return error;
        }
    }

    private List<Map<String, String>> buildMessages(String systemPrompt, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        return messages;
    }
}
