package com.rag.kb.controller;

import com.rag.kb.model.dto.ApiResult;
import com.rag.kb.model.entity.KnowledgeBase;
import com.rag.kb.service.KbService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/kb")
@RequiredArgsConstructor  // 构造函数注入依赖
public class KbController {

    private final KbService kbService;

    @GetMapping("/list")
    public String listPage(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return "redirect:/login";
        List<KnowledgeBase> list = kbService.listByUser(userId);
        model.addAttribute("kbList", list);
        model.addAttribute("username", session.getAttribute("username"));
        return "kb-list";
    }

    @PostMapping("/api/create")
    @ResponseBody
    public ApiResult<KnowledgeBase> create(@RequestParam String name,
                                           @RequestParam(required = false) String description,
                                           HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");
        try {
            KnowledgeBase kb = kbService.create(userId, name, description);
            return ApiResult.success(kb);
        } catch (Exception e) {
            return ApiResult.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ApiResult<Void> delete(@PathVariable Long id) {
        try {
            kbService.delete(id);
            return ApiResult.success(null);
        } catch (Exception e) {
            return ApiResult.error(500, "删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) return "redirect:/login";
        KnowledgeBase kb = kbService.getById(id);
        model.addAttribute("kb", kb);
        model.addAttribute("username", session.getAttribute("username"));
        return "kb-detail";
    }
}
