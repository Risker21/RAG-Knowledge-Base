# 文件预览与持久化存储设计方案

## 1. 目标描述
实现在知识库管理页面对已上传文件（PDF、图片等）的在线预览功能，并确保文件在服务器上持久化存储，不受容器重启影响。

## 2. 核心方案：Spring Boot 流式转发
采用后端接口读取文件并流式输出到浏览器的方式，以确保文件访问的权限安全性。

### 2.1 后端实现 (Backend)
- **控制器**：在 `DocumentController` 增加 `GET /doc/api/preview/{id}` 接口。
- **业务逻辑**：
  1. 根据 ID 查询文档信息，并校验 `userId` 是否匹配（防止越权访问）。
  2. 根据文件后缀名设置 HTTP Response 的 `Content-Type`（例如：`.pdf` -> `application/pdf`, `.png` -> `image/png`）。
  3. 使用 `Resource` 或 `InputStream` 将磁盘文件写入 Response 输出流。
  4. 设置 `Content-Disposition: inline` 确保浏览器尝试在页面内打开而非直接强制下载。

### 2.2 前端实现 (Frontend)
- **交互设计**：在 `KbDetailView.vue` 的文档列表操作列增加“预览”按钮（图标形式）。
- **打开方式**：点击按钮后，前端构建完整的 API URL，并使用 `window.open(previewUrl, '_blank')` 在新标签页打开。
- **支持范围**：
  - **原生支持**：PDF、JPG、PNG、GIF、WebP、TXT。
  - **回退方案**：对于 docx/pptx 等浏览器无法直接预览的文件，接口将触发下载，或由浏览器提示无法打开。

### 2.3 存储持久化 (DevOps)
- **挂载方式**：将 `docker-compose.yml` 中的 `backend` 服务挂载方式从匿名卷改为 **绑定挂载 (Bind Mount)**。
- **路径配置**：
  - **本地/服务器**：映射 `./uploads` 目录到容器内部。
  - **效果**：上传的文件将直接存储在项目根目录的 `uploads` 文件夹下，方便备份且容器删除后数据依然存在。

## 3. 任务清单 (Tasks)
- [ ] 修改 `docker-compose.yml` 实现 `./uploads` 绑定挂载。
- [ ] 在 `DocumentController` 实现预览流输出接口。
- [ ] 在 `KbDetailView.vue` 增加预览按钮及跳转逻辑。
- [ ] 验证服务器环境下的文件存取与预览效果。
