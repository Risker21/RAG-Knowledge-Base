package com.rag.kb.controller;

import com.rag.kb.model.dto.ApiResult;
import com.rag.kb.model.entity.KnowledgeBase;
import com.rag.kb.service.KbService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/kb")
public class KbController {

    private final KbService kbService;

    @GetMapping("/api/list")
    public ApiResult<List<KnowledgeBase>> listApi(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");
        return ApiResult.success(kbService.listByUser(userId));
    }

    @PostMapping("/api/create")
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
    public ApiResult<Void> delete(@PathVariable Long id) {
        try {
            kbService.delete(id);
            return ApiResult.success(null);
        } catch (Exception e) {
            return ApiResult.error(500, "删除失败: " + e.getMessage());
        }
    }
}
