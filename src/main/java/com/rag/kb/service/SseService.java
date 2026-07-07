package com.rag.kb.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String conversationId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(conversationId, emitter);
        emitter.onCompletion(() -> emitters.remove(conversationId));
        emitter.onTimeout(() -> emitters.remove(conversationId));
        emitter.onError(e -> emitters.remove(conversationId));
        return emitter;
    }

    public void send(String conversationId, String data) {
        SseEmitter emitter = emitters.get(conversationId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(data));
            } catch (Exception e) {
                emitters.remove(conversationId);
            }
        }
    }

    public void complete(String conversationId) {
        SseEmitter emitter = emitters.remove(conversationId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    public void error(String conversationId, String error) {
        SseEmitter emitter = emitters.remove(conversationId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(error));
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }
}
