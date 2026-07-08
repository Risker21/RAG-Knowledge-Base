package com.rag.kb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> pendingBuffers = new ConcurrentHashMap<>();

    @Value("${app.sse.timeout-ms:300000}")
    private long timeoutMs;

    @Value("${app.sse.batch-size:50}")
    private int batchSize;

    public SseEmitter createEmitter(String conversationId) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        emitters.put(conversationId, emitter);
        emitter.onCompletion(() -> {
            emitters.remove(conversationId);
            pendingBuffers.remove(conversationId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(conversationId);
            pendingBuffers.remove(conversationId);
            log.warn("SSE 连接超时: {}", conversationId);
        });
        emitter.onError(e -> {
            emitters.remove(conversationId);
            pendingBuffers.remove(conversationId);
            log.debug("SSE 连接错误: {}", conversationId);
        });
        return emitter;
    }

    public void send(String conversationId, String data) {
        SseEmitter emitter = emitters.get(conversationId);
        if (emitter == null) {
            return;
        }

        StringBuilder buffer = pendingBuffers.computeIfAbsent(conversationId, k -> new StringBuilder());
        buffer.append(data);

        if (buffer.length() >= batchSize) {
            flushBuffer(conversationId, buffer);
        }
    }

    public void flush(String conversationId) {
        StringBuilder buffer = pendingBuffers.get(conversationId);
        if (buffer != null && buffer.length() > 0) {
            flushBuffer(conversationId, buffer);
        }
    }

    private void flushBuffer(String conversationId, StringBuilder buffer) {
        SseEmitter emitter = emitters.get(conversationId);
        if (emitter == null) {
            buffer.setLength(0);
            return;
        }

        try {
            String content = buffer.toString();
            buffer.setLength(0);
            emitter.send(SseEmitter.event().data(content));
        } catch (Exception e) {
            emitters.remove(conversationId);
            pendingBuffers.remove(conversationId);
            log.debug("发送 SSE 数据失败: {}", e.getMessage());
        }
    }

    public void complete(String conversationId) {
        flush(conversationId);
        SseEmitter emitter = emitters.remove(conversationId);
        pendingBuffers.remove(conversationId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    public void error(String conversationId, String error) {
        flush(conversationId);
        SseEmitter emitter = emitters.remove(conversationId);
        pendingBuffers.remove(conversationId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(error));
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    public int activeConnections() {
        return emitters.size();
    }
}
