package com.rag.kb.controller;

import com.rag.kb.model.dto.ApiResult;
import com.rag.kb.model.entity.Document;
import com.rag.kb.service.DocumentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/doc")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/api/upload")
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
    public ApiResult<List<Document>> list(@PathVariable Long kbId, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");
        return ApiResult.success(documentService.listByKbAndUser(kbId, userId));
    }

    @GetMapping("/api/preview/{id}")
    public ResponseEntity<Resource> preview(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        Document doc = documentService.getByIdAndUser(id, userId);
        if (doc == null) return ResponseEntity.notFound().build();

        Resource resource = documentService.getResource(id, userId);
        if (resource == null) return ResponseEntity.notFound().build();

        String contentType = "application/octet-stream";
        String fileType = doc.getFileType().toLowerCase();
        if ("pdf".equals(fileType)) {
            contentType = "application/pdf";
        } else if (List.of("jpg", "jpeg", "png", "gif", "webp").contains(fileType)) {
            contentType = "image/" + (fileType.equals("jpg") ? "jpeg" : fileType);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getOriginalName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/api/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ApiResult.error(401, "未登录");
        try {
            documentService.delete(id, userId);
            return ApiResult.success(null);
        } catch (Exception e) {
            return ApiResult.error(403, "无权删除此文档");
        }
    }
}
