package com.rag.kb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
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

    @Value("${app.openai.max-retries:3}")
    private int maxRetries;

    @Value("${app.openai.retry-delay-ms:1000}")
    private long retryDelayMs;

    public String chat(String systemPrompt, String userMessage) {
        if (!validateApiKey()) {
            return "【提示】AI API Key 未配置，无法调用 AI 模型。请在 application.yml 中设置 app.openai.api-key 后重启。";
        }

        int attempt = 0;
        while (attempt < maxRetries) {
            attempt++;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);

                List<Map<String, String>> messages = buildMessages(systemPrompt, userMessage);

                Map<String, Object> body = new HashMap<>();
                body.put("model", model);
                body.put("messages", messages);
                body.put("temperature", 0.1);
                body.put("top_p", 0.9);

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(
                        baseUrl + "/chat/completions", request, String.class);

                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.get("choices").get(0).get("message").get("content").asText();
                return sanitizeResponse(content);

            } catch (HttpClientErrorException e) {
                log.warn("LLM API 客户端错误 (第{}次尝试): {} - {}", attempt, e.getStatusCode(), e.getMessage());
                if (e.getStatusCode().value() == 429) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                return handleError("AI 服务请求被拒绝: " + e.getMessage());

            } catch (HttpServerErrorException e) {
                log.warn("LLM API 服务端错误 (第{}次尝试): {} - {}", attempt, e.getStatusCode(), e.getMessage());
                if (attempt < maxRetries) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                return handleError("AI 服务暂时不可用，请稍后重试");

            } catch (ResourceAccessException | SocketTimeoutException e) {
                log.warn("LLM API 网络超时 (第{}次尝试): {}", attempt, e.getMessage());
                if (attempt < maxRetries) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                return handleError("网络连接超时，请检查网络连接或稍后重试");

            } catch (Exception e) {
                log.error("LLM API 调用失败 (第{}次尝试)", attempt, e);
                if (attempt < maxRetries) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                return handleError("AI 服务调用失败: " + e.getMessage());
            }
        }
        return handleError("AI 服务调用失败，请稍后重试");
    }

    public String chatStream(String systemPrompt, String userMessage, Consumer<String> onToken) {
        if (!validateApiKey()) {
            String msg = "API Key 未配置";
            onToken.accept(msg);
            return msg;
        }

        int attempt = 0;
        while (attempt < maxRetries) {
            attempt++;
            try {
                List<Map<String, String>> messages = buildMessages(systemPrompt, userMessage);

                Map<String, Object> body = new HashMap<>();
                body.put("model", model);
                body.put("messages", messages);
                body.put("temperature", 0.1);
                body.put("top_p", 0.9);
                body.put("stream", true);

                byte[] requestBody = objectMapper.writeValueAsBytes(body);

                HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/chat/completions").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(300000);
                conn.setRequestProperty("Connection", "keep-alive");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody);
                }

                StringBuilder fullText = new StringBuilder();
                boolean connectionError = false;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    int consecutiveEmptyLines = 0;
                    while ((line = reader.readLine()) != null) {
                        consecutiveEmptyLines = line.isEmpty() ? consecutiveEmptyLines + 1 : 0;
                        if (consecutiveEmptyLines > 100) {
                            log.warn("流式响应长时间无数据，断开连接");
                            break;
                        }

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
                            } catch (Exception jsonError) {
                                log.debug("解析 SSE 数据失败: {}", jsonError.getMessage());
                            }
                        }
                    }
                } catch (SocketTimeoutException e) {
                    log.warn("流式响应读取超时");
                    connectionError = true;
                }

                String result = sanitizeResponse(fullText.toString());
                if (!result.isEmpty()) {
                    return result;
                }

                if (connectionError && attempt < maxRetries) {
                    waitBeforeRetry(attempt);
                    continue;
                }

                if (result.isEmpty()) {
                    String errorMsg = "AI 服务返回内容为空";
                    onToken.accept(errorMsg);
                    return errorMsg;
                }

                return result;

            } catch (HttpClientErrorException e) {
                log.warn("流式 LLM API 客户端错误 (第{}次尝试): {}", attempt, e.getMessage());
                if (e.getStatusCode().value() == 429 && attempt < maxRetries) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                String error = handleError("AI 服务请求被拒绝");
                onToken.accept(error);
                return error;

            } catch (HttpServerErrorException e) {
                log.warn("流式 LLM API 服务端错误 (第{}次尝试): {}", attempt, e.getMessage());
                if (attempt < maxRetries) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                String error = handleError("AI 服务暂时不可用，请稍后重试");
                onToken.accept(error);
                return error;

            } catch (ResourceAccessException | SocketTimeoutException e) {
                log.warn("流式 LLM API 网络错误 (第{}次尝试): {}", attempt, e.getMessage());
                if (attempt < maxRetries) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                String error = handleError("网络连接超时，请检查网络连接");
                onToken.accept(error);
                return error;

            } catch (Exception e) {
                log.error("流式 LLM API 调用失败 (第{}次尝试)", attempt, e);
                if (attempt < maxRetries) {
                    waitBeforeRetry(attempt);
                    continue;
                }
                String error = handleError("AI 服务调用失败: " + e.getMessage());
                onToken.accept(error);
                return error;
            }
        }

        String error = handleError("AI 服务调用失败，请稍后重试");
        onToken.accept(error);
        return error;
    }

    private boolean validateApiKey() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.contains("你的KEY");
    }

    private void waitBeforeRetry(int attempt) {
        try {
            long delay = retryDelayMs * (long) Math.pow(2, attempt - 1);
            log.info("等待 {}ms 后进行第 {} 次重试", delay, attempt + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String handleError(String message) {
        log.error("LLM 错误处理: {}", message);
        return "😔 " + message;
    }

    private String sanitizeResponse(String content) {
        if (content == null) return "";
        String sanitized = content.trim();
        sanitized = sanitized.replaceAll("\\[DONE\\]", "");
        sanitized = sanitized.replaceAll("^\\s*\\{\\s*\\}\\s*$", "");
        sanitized = sanitized.replaceAll("^\\s*\\[\\s*\\]\\s*$", "");
        return sanitized;
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
