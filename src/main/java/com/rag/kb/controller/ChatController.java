package com.rag.kb.controller;

import com.rag.kb.model.dto.ApiResult;
import com.rag.kb.model.dto.ChatRequest;
import com.rag.kb.model.dto.ChatResponse;
import com.rag.kb.model.entity.Conversation;
import com.rag.kb.model.entity.Message;
import com.rag.kb.service.ChatService;
import com.rag.kb.service.DocumentService;
import com.rag.kb.service.VoiceService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final DocumentService documentService;
    private final VoiceService voiceService;

    @GetMapping("/{kbId}")
    public String chatPage(@PathVariable Long kbId,
                           @RequestParam(required = false) Long convId,
                           HttpSession session,
                           Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";

        model.addAttribute("kbId", kbId);
        model.addAttribute("convId", convId);
        model.addAttribute("username", session.getAttribute("username"));

        // 加载该知识库的文档到内存（忽略失败，不影响页面加载）
        try {
            documentService.loadKbChunksToMemory(kbId);
        } catch (Exception e) {
            // 加载失败不影响页面展示，只是对话时检索不到内容
        }

        // 对话历史列表
        List<Conversation> convs = chatService.listConversations(userId, kbId);
        model.addAttribute("conversations", convs);

        // 如果指定了 convId，加载消息
        if (convId != null) {
            List<Message> messages = chatService.getMessages(convId);
            model.addAttribute("messages", messages);
            model.addAttribute("currentConvId", convId);
        }

        return "chat";
    }

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
    public ApiResult<List<Message>> getMessages(@PathVariable Long convId) {
        return ApiResult.success(chatService.getMessages(convId));
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
