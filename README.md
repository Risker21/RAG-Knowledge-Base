# 墨韵 - RAG 知识库问答系统

![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-green?logo=spring)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.7-red?logo=mybatis)
![MySQL](https://img.shields.io/badge/MySQL-8.0-orange?logo=mysql)
![Redis](https://img.shields.io/badge/Redis-7.0-red?logo=redis)
![License](https://img.shields.io/badge/License-MIT-yellow)

纯 Java + Spring Boot 3 + MyBatis-Plus + 火山引擎 Ark API 实现的 RAG 问答系统。
不需要 Docker、Python、向量数据库，开箱即跑。

## 启动步骤

### 1. 环境要求

- JDK 17+
- MySQL 8+
- Redis（用于 Session 持久化）
- Maven 3.6+

### 2. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS rag_kb DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE rag_kb;
SOURCE src/main/resources/schema.sql;
```

### 3. 配置凭据

复制 `application-secrets.yml.example` 为 `application-secrets.yml`，填入实际值：

```yaml
spring:
  datasource:
    password: your_mysql_password

app:
  openai:
    api-key: your_volcengine_ark_api_key
  stt:
    app-id: your_volcengine_app_id
    access-token: your_volcengine_access_token
```

> `application-secrets.yml` 已被 `.gitignore` 忽略，不会提交到仓库。

### 4. 构建 & 运行

```bash
mvn clean package -DskipTests
java -jar target/rag-kb-1.0.0.jar
```

或者 IDEA：直接运行 `RagKbApplication.java`

### 5. 访问

浏览器打开 `http://localhost:8080`

---

## 使用流程

```
注册账号 → 创建知识库 → 上传文档(PDF/TXT/MD/DOCX/PPTX/HTML/CSV)
→ 等待 embedding 处理完成 → 进入问答 → 提问
```

## API 概览

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/login` | 登录页面 |
| GET | `/register` | 注册页面 |
| POST | `/login` | 表单登录（含验证码） |
| POST | `/api/auth/login` | JSON 登录 |
| POST | `/api/auth/register` | JSON 注册 |
| GET | `/api/auth/logout` | 退出登录 |
| GET | `/api/captcha/image` | 获取验证码图片 |

### 知识库

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/kb/list` | 知识库列表页 |
| POST | `/kb/api/create` | 创建知识库 |
| DELETE | `/kb/api/{id}` | 删除知识库（级联删除文档、对话） |
| GET | `/kb/{id}` | 知识库详情 / 文档管理页 |

### 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/doc/api/upload` | 上传文档（支持 PDF/TXT/MD/DOCX/PPTX/HTML/CSV） |
| GET | `/doc/api/list/{kbId}` | 文档列表 |
| DELETE | `/doc/api/{id}` | 删除文档 |

### 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/chat/{kbId}` | 聊天页面 |
| POST | `/chat/api/start` | 创建新对话 |
| POST | `/chat/api/ask` | 提问（RAG 问答） |
| GET | `/chat/api/conversations/{kbId}` | 对话列表 |
| DELETE | `/chat/api/conversations/{convId}` | 删除对话 |
| GET | `/chat/api/messages/{convId}` | 历史消息 |
| POST | `/chat/api/voice/transcribe` | 语音转文字 |

## 关键配置

| 配置项 | 文件 | 说明 |
|--------|------|------|
| 数据库密码 | `application-secrets.yml` | MySQL root 密码 |
| 火山引擎 API Key | `application-secrets.yml` | 调用 AI 的凭证 |
| 火山引擎 App ID | `application-secrets.yml` | STT 语音识别 |
| 火山引擎 Access Token | `application-secrets.yml` | STT 语音识别 |
| 上传目录 | `application.yml` `app.upload-dir` | 文档存储位置 |
| 嵌入模型 | `application.yml` `app.openai.embedding-model` | 文本向量化（doubao-embedding-vision-251215） |
| 对话模型 | `application.yml` `app.openai.chat-model` | 问答生成（doubao-seed-1-8-251228） |
| 相似度阈值 | `VectorStore.search()` 第 3 参数 | 默认 0.3 |
| 检索条数 | `VectorStore.search()` 第 2 参数 | 默认 Top-5 |

## 项目结构

```
rag-kb/
├── pom.xml                              # Maven 依赖
└── src/main/
    ├── java/com/rag/kb/
    │   ├── RagKbApplication.java        # 启动类
    │   ├── config/
    │   │   ├── MyBatisPlusConfig.java   # 自动填充 created_at
    │   │   └── RestTemplateConfig.java  # HTTP 客户端配置
    │   ├── controller/
    │   │   ├── AuthController.java      # 登录/注册
    │   │   ├── CaptchaController.java   # 验证码
    │   │   ├── KbController.java        # 知识库管理
    │   │   ├── DocController.java       # 文档上传/管理
    │   │   └── ChatController.java      # 对话/问答
    │   ├── service/
    │   │   ├── UserService.java         # 用户认证
    │   │   ├── KbService.java           # 知识库 CRUD
    │   │   ├── DocumentService.java     # 文档处理流水线
    │   │   ├── DocumentParser.java      # Tika 文档解析
    │   │   ├── TextChunker.java         # 文本切片
    │   │   ├── EmbeddingService.java    # 火山引擎嵌入 API
    │   │   ├── VectorStore.java         # 内存向量库（余弦检索）
    │   │   ├── VectorStoreLoader.java   # 启动时加载向量到内存
    │   │   ├── LlmService.java          # 对话模型 API
    │   │   ├── ChatService.java         # RAG 问答编排
    │   │   ├── PromptTemplate.java      # Prompt 模板构建
    │   │   ├── CaptchaService.java      # 验证码生成
    │   │   └── VoiceService.java        # 语音转文字
    │   ├── model/
    │   │   ├── entity/
    │   │   │   ├── User.java
    │   │   │   ├── KnowledgeBase.java
    │   │   │   ├── Document.java
    │   │   │   ├── DocChunk.java
    │   │   │   ├── Conversation.java
    │   │   │   └── Message.java
    │   │   ├── dto/
    │   │   │   ├── ApiResult.java
    │   │   │   ├── ChatRequest.java
    │   │   │   ├── ChatResponse.java
    │   │   │   ├── LoginDto.java
    │   │   │   └── RegisterDto.java
    │   │   └── enums/
    │   │       └── DocStatus.java       # 文档状态枚举
    │   └── mapper/
    │       ├── UserMapper.java
    │       ├── KbMapper.java
    │       ├── DocumentMapper.java
    │       ├── DocChunkMapper.java
    │       ├── ConversationMapper.java
    │       └── MessageMapper.java
    └── resources/
        ├── application.yml              # 公共配置
        ├── application-secrets.yml      # 敏感配置（已 gitignore）
        ├── application-secrets.yml.example  # 配置模板
        ├── schema.sql                   # 建表 SQL
        ├── static/css/
        │   ├── style.css                # 墨韵山水主题样式
        │   └── chat-enhance.css         # 聊天页增强样式
        └── templates/
            ├── login.html               # 登录页
            ├── register.html            # 注册页
            ├── kb-list.html             # 知识库列表
            ├── kb-detail.html           # 文档管理
            └── chat.html                # 聊天页
```

## 技术架构

```
[Thymeleaf 前端] ←→ [Controller] ←→ [Service]
                                        │
                         ┌──────────────┼──────────────┐
                         ▼              ▼              ▼
                      MySQL        火山引擎 Ark    内存向量库
                   (数据存储)       (AI 能力)      (余弦检索)
                         │              │
                         ▼              ▼
                      Redis           Whisper API
                   (Session 持久化)   (语音识别)
```

### RAG 详细流程

```
用户输入问题
     ↓
[1] EmbeddingService.embed(问题)
    → POST /api/v3/embeddings/multimodal  (doubao-embedding-vision-251215)
    → 返回 512 维 float[] 向量
     ↓
[2] VectorStore.search(queryVec, topK=5, threshold=0.3)
    → 遍历内存中所有文档块向量
    → 计算余弦相似度，筛选 top-5
     ↓
[3] 判空分支
    ├── 有结果 → PromptTemplate 构建 RAG prompt（基于文档回答）
    └── 无结果 → 使用通用对话 prompt（AI 自由回答）
     ↓
[4] LlmService.chat(systemPrompt, userMessage)
    → POST /api/v3/chat/completions  (doubao-seed-1-8-251228)
     ↓
[5] 返回回答 + 引用来源 → 前端渲染（Markdown + 可点击引用弹窗）
```

### 文档处理流水线

```
文件上传
     ↓
[1] DocumentParser.parse(file, fileType)
    → Apache Tika 解析为纯文本
    → 支持 PDF/TXT/MD/DOCX/PPTX/HTML/CSV
     ↓
[2] TextChunker.split(text)
    → Markdown 标题 + 段落分割
    → 目标块大小 ~500 字符
     ↓
[3] EmbeddingService.embedBatch(chunks)
    → 批量调用火山引擎多模态嵌入 API
     ↓
[4] 双写存储
    ├── MySQL: DocChunk 表（content + embedding JSON）
    └── 内存: VectorStore（ConcurrentHashMap）
```

## 功能清单

- ✅ 用户注册/登录（Session 认证 + 验证码）
- ✅ 知识库 CRUD（级联删除）
- ✅ 文档上传（PDF/TXT/MD/DOCX/PPTX/HTML/CSV）
- ✅ Apache Tika 智能解析（自动识别文件类型）
- ✅ Markdown 标题 + 段落分块策略
- ✅ 火山引擎多模态嵌入 API（doubao-embedding-vision）
- ✅ 内存向量库 + MySQL 双写
- ✅ RAG 问答（文档命中时基于文档，未命中时自由对话）
- ✅ Markdown 渲染 + 代码高亮（marked.js）
- ✅ 引用来源显示 & 点击弹窗
- ✅ 对话创建/切换/删除
- ✅ 历史消息加载
- ✅ 语音转文字输入（Whisper API）
- ✅ Redis 分布式 Session

## 常见问题

**Q: 启动报错？**
检查 MySQL 和 Redis 有没有启动，凭据配没配对，API Key 是否有效。

**Q: 上传文档后 chunk_count 一直为 0？**
查看控制台日志是否有 embedding API 报错。可能是 key 不对或模型已退役。

**Q: Maven 打包报错 Unable to rename？**
先手动删了 `target/` 再重新打包，或者用 `mvn spring-boot:run` 直接运行。

**Q: Embedding API 报 401？**
API Key 不对或没有开通方舟模型访问权限。检查 `application-secrets.yml` 中的配置。

**Q: 回答完全不基于文档内容？**
检查控制台日志中 `RAG 检索到 X 条结果`。如果为 0，可能是相似度阈值 0.3 太高，可以改为 0.2。
