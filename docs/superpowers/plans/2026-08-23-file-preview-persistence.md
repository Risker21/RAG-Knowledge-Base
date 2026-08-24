# 文件预览与持久化存储实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现知识库文件的在线预览功能，并将上传文件持久化存储在项目根目录的 `uploads` 文件夹中。

**Architecture:** 
- 后端通过 `DocumentController` 提供流式转发接口，校验权限后将文件流返回浏览器。
- 前端在文档列表中增加预览按钮，通过 `window.open` 在新标签页调用预览接口。
- Docker 配置修改为绑定挂载（Bind Mount），确保 `./uploads` 目录的持久化。

**Tech Stack:** Java 17, Spring Boot 3, Vue 3, Docker Compose

---

### Task 1: Docker 挂载与持久化配置

**Files:**
- Modify: `docker-compose.yml:30-57`

- [ ] **Step 1: 修改后端服务的卷挂载方式**

将原有的匿名卷修改为当前目录下的 `./uploads` 绑定挂载。

```yaml
  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/rag_kb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      APP_OPENAI_API_KEY: ${ARK_API_KEY}
      APP_STT_APP_ID: ${STT_APP_ID}
      APP_STT_ACCESS_TOKEN: ${STT_ACCESS_TOKEN}
    volumes:
      - ./uploads:/app/uploads  # 修改此处：从匿名卷改为绑定挂载，路径对应容器内的 WORKDIR
      - maven-repo:/root/.m2/repository
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 40s
    restart: unless-stopped
```

- [ ] **Step 2: 验证配置正确性**

运行：`docker-compose config`
预期：输出中 `backend.volumes` 包含 `./uploads:/app/uploads`。

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "deploy: update backend volume to bind mount for persistence"
```

---

### Task 2: 后端预览接口实现

**Files:**
- Modify: `src/main/java/com/rag/kb/controller/DocumentController.java`
- Modify: `src/main/java/com/rag/kb/service/DocumentService.java`

- [ ] **Step 1: 在 DocumentService 中增加获取文件资源的方法**

```java
public org.springframework.core.io.Resource getResource(Long id, Long userId) {
    Document doc = getByIdAndUser(id, userId);
    if (doc == null) return null;
    java.io.File file = new java.io.File(doc.getFilePath());
    if (!file.exists()) return null;
    return new org.springframework.core.io.FileSystemResource(file);
}
```

- [ ] **Step 2: 在 DocumentController 中增加预览接口**

```java
@GetMapping("/api/preview/{id}")
public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> preview(@PathVariable Long id, jakarta.servlet.http.HttpSession session) {
    Long userId = (Long) session.getAttribute("userId");
    if (userId == null) return org.springframework.http.ResponseEntity.status(401).build();

    org.springframework.core.io.Resource resource = documentService.getResource(id, userId);
    if (resource == null) return org.springframework.http.ResponseEntity.notFound().build();

    String filename = resource.getFilename();
    String contentType = "application/octet-stream";
    if (filename != null) {
        if (filename.endsWith(".pdf")) contentType = "application/pdf";
        else if (filename.endsWith(".png")) contentType = "image/png";
        else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) contentType = "image/jpeg";
        else if (filename.endsWith(".gif")) contentType = "image/gif";
        else if (filename.endsWith(".txt")) contentType = "text/plain";
    }

    return org.springframework.http.ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .body(resource);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/rag/kb/controller/DocumentController.java src/main/java/com/rag/kb/service/DocumentService.java
git commit -m "feat: add document preview streaming API"
```

---

### Task 3: 前端预览功能实现

**Files:**
- Modify: `frontend/src/views/KbDetailView.vue`

- [ ] **Step 1: 在表格操作列增加预览按钮**

在 `el-table-column`（或现有的操作区域）中增加预览按钮。

```html
<template #default="scope">
  <el-button 
    type="primary" 
    link 
    icon="View" 
    @click="handlePreview(scope.row)"
  >预览</el-button>
  <el-button 
    type="danger" 
    link 
    icon="Delete" 
    @click="handleDelete(scope.row.id)"
  >删除</el-button>
</template>
```

- [ ] **Step 2: 实现 handlePreview 逻辑**

```typescript
const handlePreview = (doc: any) => {
  const previewUrl = `/doc/api/preview/${doc.id}`;
  window.open(previewUrl, '_blank');
};
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/KbDetailView.vue
git commit -m "feat: add preview button to document list"
```

---

### Task 4: 服务器同步与验证

- [ ] **Step 1: 提示用户同步代码并重启**

提醒用户在服务器执行：
```bash
git pull
docker-compose down
docker-compose up -d --build
```

- [ ] **Step 2: 验证功能**
1. 上传一个 PDF 文件。
2. 在列表点击“预览”。
3. 检查新标签页是否正确显示 PDF 内容。
4. 检查服务器项目根目录下的 `uploads` 文件夹是否出现了新上传的文件。
