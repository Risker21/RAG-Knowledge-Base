package com.rag.kb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.stt.provider:whisper}")
    private String provider;

    @Value("${app.stt.api-key:}")
    private String apiKey;

    @Value("${app.stt.app-id:}")
    private String appId;

    @Value("${app.stt.access-token:}")
    private String accessToken;

    @Value("${app.stt.base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${app.stt.model:whisper-1}")
    private String model;

    public String transcribe(byte[] audioData, String filename) throws Exception {
        if ("volcengine".equalsIgnoreCase(provider)) {
            return transcribeVolc(audioData, filename);
        }
        return transcribeWhisper(audioData, filename);
    }

    private String transcribeWhisper(byte[] audioData, String filename) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Whisper API Key 未配置");
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(audioData) {
            @Override public String getFilename() {
                return filename != null ? filename : "recording.webm";
            }
        });
        body.add("model", model);
        body.add("language", "zh");

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        String url = baseUrl.replaceAll("/+$", "") + "/v1/audio/transcriptions";
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
            JsonNode json = objectMapper.readTree(resp.getBody());
            if (json.has("text")) {
                return json.get("text").asText();
            }
        }
        log.warn("Whisper 返回异常: status={}, body={}", resp.getStatusCode(), resp.getBody());
        return null;
    }

    @SuppressWarnings("unchecked")
    private String transcribeVolc(byte[] audioData, String filename) throws Exception {
        if (appId == null || appId.isBlank() || accessToken == null || accessToken.isBlank()) {
            log.warn("火山引擎 ASR AppID/AccessToken 未配置");
            return null;
        }

        // 构建 JSON 请求体（火山 ASR HTTP API 使用 base64 音频）
        String ext = filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1) : "webm";

        Map<String, Object> user = new HashMap<>();
        user.put("uid", "anonymous");

        Map<String, Object> audio = new HashMap<>();
        audio.put("format", ext);
        audio.put("rate", 16000);
        audio.put("channel", 1);
        audio.put("bits", 16);
        audio.put("data", Base64.getEncoder().encodeToString(audioData));

        Map<String, Object> request = new HashMap<>();
        request.put("model_name", "volc-engine-16k");
        request.put("enable_punctuation", 2);
        request.put("enable_itn", 1);

        Map<String, Object> body = new HashMap<>();
        body.put("user", user);
        body.put("audio", audio);
        body.put("request", request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Appid", appId);
        headers.set("X-Access-Token", accessToken);

        HttpEntity<String> httpEntity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        String url = "https://openspeech.bytedance.com/api/v1/asr";

        log.info("火山 ASR 请求: format={}, size={}B", ext, audioData.length);
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, httpEntity, String.class);
        log.info("火山 ASR 响应: status={}, body={}", resp.getStatusCode(), resp.getBody());

        if (resp.getBody() != null) {
            JsonNode json = objectMapper.readTree(resp.getBody());
            int code = json.path("code").asInt(-1);
            if (code == 0) {
                JsonNode result = json.get("result");
                if (result != null && result.isArray() && result.size() > 0) {
                    String text = result.get(0).path("text").asText();
                    if (!text.isEmpty()) return text;
                }
            } else {
                throw new Exception("火山 ASR 错误 code=" + code + ", msg=" + json.path("message").asText());
            }
        }
        throw new Exception("火山 ASR 返回空结果");
    }
}
