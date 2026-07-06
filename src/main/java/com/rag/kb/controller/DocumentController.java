package com.rag.kb.controller;

import com.rag.kb.model.dto.ApiResult;
import com.rag.kb.model.entity.Document;
import com.rag.kb.service.DocumentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Controller
@RequestMapping("/doc")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/api/upload")
    @ResponseBody
    public ApiResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam("kbId") Long kbId,
                                                  HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");
        try {
            Document doc = documentService.upload(file, kbId, userId);
            Map<String, Object> data = new HashMap<>();
            data.put("id", doc.getId());
            data.put("filename", doc.getOriginalName());
            data.put("status", doc.getStatus());
            data.put("chunkCount", doc.getChunkCount());
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(500, "上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/api/list/{kbId}")
    @ResponseBody
    public ApiResult<List<Document>> list(@PathVariable Long kbId) {
        return ApiResult.success(documentService.listByKb(kbId));
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ApiResult<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ApiResult.success(null);
    }
}
