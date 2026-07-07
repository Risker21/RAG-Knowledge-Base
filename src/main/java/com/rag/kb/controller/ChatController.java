package com.rag.kb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.kb.model.dto.ApiResult;
import com.rag.kb.model.dto.ChatRequest;
import com.rag.kb.model.dto.ChatResponse;
import com.rag.kb.model.entity.Conversation;
import com.rag.kb.model.entity.Message;
import com.rag.kb.service.ChatService;
import com.rag.kb.service.SseService;
import com.rag.kb.service.VoiceService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final VoiceService voiceService;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    @PostMapping("/api/start")
    @ResponseBody
    public ApiResult<Map<String, Object>> startConversation(@RequestParam Long kbId,
                                                             @RequestParam(required = false) String title,
                                                             HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");
        Conversation conv = chatService.createConversation(userId, kbId, title);
        Map<String, Object> data = new HashMap<>();
        data.put("id", conv.getId());
        data.put("title", conv.getTitle());
        return ApiResult.success(data);
    }

    @PostMapping("/api/ask")
    @ResponseBody
    public ApiResult<ChatResponse> ask(@RequestBody ChatRequest request, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");
        try {
            ChatResponse resp = chatService.ask(
                    request.getConversationId(),
                    request.getKbId(),
                    request.getQuestion());
            return ApiResult.success(resp);
        } catch (Exception e) {
            return ApiResult.error(500, "回答失败: " + e.getMessage());
        }
    }

    @GetMapping("/api/ask/stream")
    public SseEmitter askStream(@SessionAttribute("userId") Long userId,
                                @RequestParam Long conversationId,
                                @RequestParam Long kbId,
                                @RequestParam String question) {
        if (userId == null) return null;
        String convKey = userId + ":" + conversationId;
        SseEmitter emitter = sseService.createEmitter(convKey);

        CompletableFuture.runAsync(() -> {
            try {
                ChatResponse response = chatService.askStream(conversationId, kbId, question,
                        token -> sendSseToken(convKey, token));

                Map<String, Object> doneMsg = new HashMap<>();
                doneMsg.put("type", "done");
                doneMsg.put("references", response.getReferences());
                sseService.send(convKey, objectMapper.writeValueAsString(doneMsg));
                sseService.complete(convKey);
            } catch (Exception e) {
                log.error("SSE stream error", e);
                sseService.error(convKey, e.getMessage());
            }
        });

        return emitter;
    }

    private void sendSseToken(String convKey, String token) {
        try {
            Map<String, String> msg = new HashMap<>();
            msg.put("type", "token");
            msg.put("content", token);
            sseService.send(convKey, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("Failed to send SSE token", e);
        }
    }

    @GetMapping("/api/conversations/{kbId}")
    @ResponseBody
    public ApiResult<List<Conversation>> getConversations(@PathVariable Long kbId, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");
        return ApiResult.success(chatService.listConversations(userId, kbId));
    }

    @DeleteMapping("/api/conversations/{convId}")
    @ResponseBody
    public ApiResult<Void> deleteConversation(@PathVariable Long convId, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");
        try {
            chatService.deleteConversation(convId, userId);
            return ApiResult.success(null);
        } catch (Exception e) {
            return ApiResult.error(500, e.getMessage());
        }
    }

    @GetMapping("/api/messages/{convId}")
    @ResponseBody
    public ApiResult<List<Message>> getMessages(@PathVariable Long convId, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");
        try {
            return ApiResult.success(chatService.getMessages(convId, userId));
        } catch (RuntimeException e) {
            return ApiResult.error(403, e.getMessage());
        }
    }

    @PostMapping("/api/voice/transcribe")
    @ResponseBody
    public Map<String, Object> transcribeVoice(@RequestParam("audio") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            String text = voiceService.transcribe(file.getBytes(), file.getOriginalFilename());
            if (text == null) {
                result.put("code", 400);
                result.put("message", "语音识别未配置或服务不可用，请检查 application.yml 配置");
            } else {
                result.put("code", 200);
                result.put("data", text);
            }
        } catch (Exception e) {
            log.error("语音识别失败", e);
            result.put("code", 500);
            result.put("message", "语音识别失败: " + e.getMessage());
        }
        return result;
    }
}
