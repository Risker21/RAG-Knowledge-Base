# 墨韵 - RAG 知识库问答系统

![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-green?logo=spring)
![Vue.js](https://img.shields.io/badge/Vue-3.4-blue?logo=vue.js)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.7-red?logo=mybatis)
![MySQL](https://img.shields.io/badge/MySQL-8.0-orange?logo=mysql)
![Redis](https://img.shields.io/badge/Redis-7.0-red?logo=redis)
![License](https://img.shields.io/badge/License-MIT-yellow)

基于 Spring Boot 3 + Vue 3 + 火山引擎 Ark API 构建的企业级 RAG（检索增强生成）知识库问答系统，支持 **SSE 流式输出**、多格式文档解析、代码高亮渲染，提供 Docker 一键部署方案。

---

## 🌟 核心特性

| 特性 | 说明 |
|------|------|
| **SSE 流式问答** | AI 回答逐字输出，打字机效果，体验流畅自然 |
| **RAG 智能检索** | 基于向量相似度的文档检索，精准定位相关知识 |
| **多格式文档支持** | PDF/TXT/MD/DOCX/PPTX/HTML/CSV 自动解析 |
| **代码高亮渲染** | Markdown 渲染 + highlight.js 代码高亮 |
| **引用来源追溯** | 每条回答自动标记来源，点击查看详情 |
| **Docker 一键部署** | `docker-compose up` 启动全部服务 |
| **纯内存向量库** | 不依赖第三方向量数据库，启动时从 MySQL 加载 |
| **Redis 分布式会话** | 支持多实例部署的会话管理 |
| **语音输入** | Whisper API 语音转文字 |

---

## 🚀 快速开始

### 方式一：Docker 部署（推荐）

```bash
# 1. 配置凭据
cp .env.example .env
# 编辑 .env 文件，填入你的 API Key 和数据库密码

# 2. 一键启动所有服务
docker-compose up --build

# 访问 http://localhost
```

> `.env` 文件已被 `.gitignore` 忽略，不会提交到仓库，确保敏感信息安全。

### 方式二：本地开发

**环境要求**：JDK 17+、MySQL 8+、Redis 7+、Maven 3.9+、Node.js 20+

```bash
# 1. 创建数据库
mysql -u root -p < src/main/resources/schema.sql

# 2. 配置凭据
cp src/main/resources/application-secrets.yml.example src/main/resources/application-secrets.yml
# 编辑 application-secrets.yml 填入你的 API Key

# 3. 启动后端
mvn spring-boot:run

# 4. 新开终端，启动前端
cd frontend
npm install
npm run dev

# 访问 http://localhost:3000
```

---

## 📖 使用流程

```
注册账号 → 创建知识库 → 上传文档(PDF/TXT/MD/DOCX/PPTX/HTML/CSV)
→ 等待 embedding 处理完成 → 进入问答 → 提问（流式输出）
```

### 详细步骤

1. **注册登录**：访问首页，注册新账号并登录
2. **创建知识库**：点击「+ 新建」按钮，输入知识库名称和描述
3. **上传文档**：进入知识库详情页，拖拽或点击上传文档
4. **等待处理**：文档上传后自动进行解析、分块、向量化，可在文档列表查看进度
5. **开始问答**：点击「开始对话」进入聊天页面，输入问题即可获得基于文档的回答

---

## 🛠️ 技术栈

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 前端 | Vue 3 + TypeScript | 3.4 | 单页应用框架 |
| 前端构建 | Vite | 5.2 | 构建工具 |
| 状态管理 | Pinia | 2.1 | 状态管理 |
| 路由 | Vue Router | 4.3 | 路由管理 |
| HTTP 客户端 | Axios | 1.7 | API 调用 |
| Markdown | marked + highlight.js | 最新 | 文档渲染与代码高亮 |
| 后端框架 | Spring Boot | 3.2.5 | REST API 框架 |
| ORM | MyBatis-Plus | 3.5.7 | 数据访问 |
| 数据库 | MySQL | 8.0 | 数据存储 |
| 缓存/Session | Redis | 7.0 | 会话管理 |
| AI 模型 | 火山引擎 Ark API | - | 大语言模型 + 嵌入 |
| 文档解析 | Apache Tika | 2.9.2 | 多格式文档提取 |
| 部署 | Docker / Docker Compose | - | 容器化部署 |

---

## 🔌 API 接口

### 认证接口

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| POST | `/api/auth/login` | 用户登录 | `username`, `password`, `captcha` |
| POST | `/api/auth/register` | 用户注册 | `username`, `password`, `confirmPassword` |
| GET | `/api/auth/logout` | 退出登录 | - |
| GET | `/api/captcha/image` | 获取验证码 | - |

### 知识库接口

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `/kb/api/list` | 获取知识库列表 | - |
| POST | `/kb/api/create` | 创建知识库 | `name`, `description` |
| DELETE | `/kb/api/{id}` | 删除知识库（级联） | `id` |

### 文档接口

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| POST | `/doc/api/upload` | 上传文档 | `file`, `kbId` |
| GET | `/doc/api/list/{kbId}` | 获取文档列表 | `kbId` |
| DELETE | `/doc/api/{id}` | 删除文档 | `id` |

### 对话接口

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| POST | `/chat/api/start` | 创建对话 | `kbId`, `title` |
| GET | `/chat/api/ask/stream` | **SSE 流式问答**（核心） | `conversationId`, `kbId`, `question` |
| POST | `/chat/api/ask` | 非流式问答 | `conversationId`, `kbId`, `question` |
| GET | `/chat/api/conversations/{kbId}` | 获取对话列表 | `kbId` |
| DELETE | `/chat/api/conversations/{convId}` | 删除对话 | `convId` |
| GET | `/chat/api/messages/{convId}` | 获取历史消息 | `convId` |
| POST | `/chat/api/voice/transcribe` | 语音转文字 | `audio` |

---

## ⚙️ 关键配置

| 配置项 | 位置 | 默认值 | 说明 |
|--------|------|--------|------|
| 数据库密码 | `application-secrets.yml` / `.env` | - | MySQL root 密码 |
| 火山引擎 API Key | `application-secrets.yml` / `.env` | - | AI 调用凭证 |
| 火山引擎 App ID | `application-secrets.yml` / `.env` | - | STT 语音识别 |
| 上传目录 | `application.yml` | `./uploads` | 文档存储位置 |
| 嵌入模型 | `app.openai.embedding-model` | `doubao-embedding-vision-251215` | 向量化模型 |
| 对话模型 | `app.openai.chat-model` | `doubao-seed-1-8-251228` | 大语言模型 |
| 相似度阈值 | `VectorStore.search()` | `0.3` | 检索匹配阈值 |
| 检索条数 | `VectorStore.search()` | `5` | Top-K 检索数量 |

> **优先级**：环境变量 > `application-secrets.yml` > `application.yml`

---

## 📁 项目结构

```
rag-kb/
├── frontend/                            # Vue 3 SPA 前端
│   ├── src/
│   │   ├── api/                         # Axios API 层
│   │   │   ├── client.ts                # Axios 实例 + 拦截器
│   │   │   ├── auth.ts / kb.ts / doc.ts / chat.ts / captcha.ts
│   │   ├── types/index.ts               # TypeScript 类型定义
│   │   ├── router/index.ts              # 路由配置（5 个页面）
│   │   ├── stores/
│   │   │   ├── user.ts                  # 用户状态（Pinia）
│   │   │   └── chat.ts                  # 对话状态
│   │   ├── views/
│   │   │   ├── LoginView.vue            # 登录页
│   │   │   ├── RegisterView.vue         # 注册页
│   │   │   ├── KbListView.vue           # 知识库列表
│   │   │   ├── KbDetailView.vue         # 文档管理 + 拖拽上传
│   │   │   └── ChatView.vue             # 聊天页（SSE 流式）
│   │   ├── components/
│   │   │   ├── AppHeader.vue            # 顶部导航
│   │   │   ├── KbCard.vue               # 知识库卡片
│   │   │   └── MessageBubble.vue        # 消息气泡（Markdown 渲染）
│   │   └── styles/main.css              # 墨韵主题样式
│   ├── Dockerfile                       # Nginx 容器化
│   ├── nginx.conf                       # SPA 路由代理
│   └── package.json
├── src/main/java/com/rag/kb/
│   ├── RagKbApplication.java            # Spring Boot 启动类
│   ├── config/
│   │   ├── CorsConfig.java              # CORS 跨域配置
│   │   ├── MyBatisPlusConfig.java       # MyBatis-Plus 配置
│   │   └── RestTemplateConfig.java      # HTTP 客户端配置
│   ├── controller/
│   │   ├── AuthController.java          # 注册/登录
│   │   ├── CaptchaController.java       # 验证码
│   │   ├── KbController.java            # 知识库 CRUD
│   │   ├── DocumentController.java      # 文档管理
│   │   └── ChatController.java          # 对话/SSE 流式
│   ├── service/
│   │   ├── UserService / KbService / DocumentService  # 业务服务
│   │   ├── DocumentParser / TextChunker / EmbeddingService  # 文档处理
│   │   ├── LlmService / ChatService / PromptTemplate  # AI 服务
│   │   ├── SseService / VectorStore / VectorStoreLoader  # 检索服务
│   │   ├── CaptchaService / VoiceService  # 辅助服务
│   ├── model/
│   │   ├── entity/                      # 数据库实体
│   │   ├── dto/                         # 数据传输对象
│   │   └── enums/                       # 枚举定义
│   └── mapper/                          # MyBatis Mapper 接口
├── src/main/resources/
│   ├── application.yml                  # 公共配置
│   ├── application-secrets.yml          # 敏感配置（已 gitignore）
│   ├── application-secrets.yml.example  # 配置模板
│   └── schema.sql                       # 建表 SQL
├── Dockerfile                           # Spring Boot 多阶段构建
├── docker-compose.yml                   # Docker Compose 编排
├── .env.example                         # 环境变量模板
└── pom.xml                              # Maven 依赖管理
```

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Vue 3 SPA 前端                            │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌───────────────┐  │
│  │ 登录/注册 │  │知识库管理│  │文档上传 │  │  SSE 流式聊天  │  │
│  └────┬────┘  └────┬────┘  └────┬────┘  └───────┬───────┘  │
└───────┼────────────┼────────────┼────────────────┼───────────┘
        │            │            │                │
        └────────────┼────────────┼────────────────┘
                     ▼            ▼
              ┌──────────────────────────────┐
              │      Spring Boot 后端         │
              │  Controller → Service → Mapper│
              └──────────────────┬───────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         ▼                       ▼                       ▼
    ┌─────────┐           ┌──────────────┐      ┌─────────────┐
    │  MySQL  │           │ 火山引擎 Ark │      │ 内存向量库   │
    │(数据存储)│           │   (AI 能力)   │      │ (余弦检索)  │
    └────┬────┘           └───────┬────────┘      └─────────────┘
         │                       │
         ▼                       ▼
    ┌─────────┐           ┌──────────────┐
    │  Redis  │           │  Whisper API │
    │(会话管理)│           │  (语音识别)   │
    └─────────┘           └──────────────┘
```

### RAG + SSE 流式流程

```
用户输入问题
     ↓
[1] EmbeddingService.embed(问题)          ← POST /api/v3/embeddings/multimodal
     ↓
[2] VectorStore.search(topK=5, 阈值=0.3)  ← 余弦相似度检索文档片段
     ↓
[3] PromptTemplate 构建 System Prompt     ← 拼接文档上下文
     ↓
[4] LlmService.chatStream()               ← stream=true，逐 token 回调
     ↓
[5] SseService → SSE (text/event-stream)  ← 前端逐 token 渲染（打字机效果）
     ↓
[6] 完成后发送引用来源数据                  ← 前端渲染引用弹窗
```

### 文档处理流水线

```
上传文件 → DocumentParser(Tika) → TextChunker(~500字符/块)
→ EmbeddingService(批量向量化)
→ 双写: MySQL(doc_chunk表) + 内存(VectorStore)
```

---

## ❓ 常见问题

**Q: `docker-compose up` 后访问不了？**

首次启动 MySQL 初始化需要 10-20 秒，后端需要等待数据库就绪。可以通过 `docker-compose logs backend` 查看启动日志，等待出现 "Started RagKbApplication" 后再访问。

**Q: 启动报错？**

检查以下几点：
1. MySQL、Redis 是否已启动
2. `application-secrets.yml` 或环境变量中的凭据是否正确
3. 端口是否被占用（默认使用 8080、3306、6379）

**Q: 上传文档后 chunk_count 一直为 0？**

查看后端日志是否有 embedding API 报错。可能原因：
- API Key 不正确
- 模型已退役（检查火山引擎控制台）
- 网络问题导致 API 调用失败

**Q: 回答完全不基于文档？**

检查日志中 `RAG 检索到 X 条结果`。如果为 0，可降低相似度阈值（在 `VectorStore.search()` 方法中调整第三个参数，默认 0.3 → 0.2）。

**Q: Maven 打包报错 Unable to rename？**

Windows 文件锁定问题。先手动删除 `target/` 目录，或使用 `mvn spring-boot:run` 直接运行。

**Q: AI 返回的代码显示乱码（HTML 实体编码）？**

已修复。当前版本会自动解码 HTML 实体，确保代码正确渲染。

**Q: AI 回答时头像和内容消失？**

已修复。消息渲染结构已优化，避免重复嵌套导致的样式冲突。

---

## 📝 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

---

*墨韵 - 让知识触手可及*
