# RAG 知识库问答系统

纯 Java + Spring Boot 3 + MyBatis-Plus + 火山引擎 Ark API 实现的 RAG 问答系统。
不需要 Docker、Python、向量数据库，开箱即跑。

## 启动步骤

### 1. 创建数据库

打开 MySQL，执行：

```sql
CREATE DATABASE IF NOT EXISTS rag_kb DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE rag_kb;
-- 然后执行 src/main/resources/schema.sql 里的建表语句
```

或者命令行：
```bash
mysql -u root -p < src/main/resources/schema.sql
```

### 2. 配置火山引擎 API Key

打开 `src/main/resources/application.yml`，找到：

```yaml
app:
  openai:
    api-key: ***    # 火山引擎方舟的 API Key
```

### 3. 修改数据库密码（如果需要）

```yaml
spring:
  datasource:
    username: root
    password: root   # 改成你的 MySQL 密码
```

### 4. 构建 & 运行

需要 JDK 17+。

```bash
# 打包（跳过测试）
mvn clean package -DskipTests

# 运行
java -jar target/rag-kb-1.0.0.jar
```

或者 IDEA：打开项目 → 运行 `RagKbApplication.java`

### 5. 访问

浏览器打开 `http://localhost:8080`

---

## 使用流程

```
注册账号 → 创建知识库 → 上传文档(PDF/TXT/MD)
→ 等待 embedding 处理完成 → 进入问答 → 提问
```

## 关键配置

| 配置项 | 位置 | 说明 |
|--------|------|------|
| 火山引擎 API Key | `app.openai.api-key` | 调用 AI 的凭证 |
| 上传目录 | `app.upload-dir` | 文档存储位置 |
| 嵌入模型 | `app.openai.embedding-model` | 文本向量化（当前：doubao-embedding-vision-251215） |
| 对话模型 | `app.openai.chat-model` | 问答生成（当前：doubao-seed-1-8-251228） |
| 嵌入端点 | `app.openai.embedding-url` | 火山引擎多模态嵌入 API |
| 相似度阈值 | VectorStore.search() 第 3 参数 | 默认 0.3 |
| 检索条数 | VectorStore.search() 第 2 参数 | 默认 Top-5 |

## 项目结构

```
rag-kb/
├── pom.xml                         # Maven 依赖
├── src/main/java/com/rag/kb/
│   ├── RagKbApplication.java       # 启动类
│   ├── config/                     # 配置（RestTemplate、MyBatis-Plus）
│   ├── controller/                 # 4 个控制器
│   ├── service/                    # 核心业务逻辑
│   │   ├── ChatService.java        # 问答编排（embedding → 检索 → prompt → LLM）
│   │   ├── DocumentService.java    # 文档上传、解析、切片、入库
│   │   ├── EmbeddingService.java   # 火山引擎多模态嵌入 API 调用
│   │   ├── LlmService.java         # 对话模型 API 调用
│   │   ├── VectorStore.java        # 内存向量库（余弦相似度检索）
│   │   └── PromptTemplate.java     # System prompt 模板构建
│   ├── model/entity/               # 6 个数据库实体
│   ├── model/dto/                  # 5 个数据传输对象
│   └── mapper/                     # 6 个 MyBatis 接口
└── src/main/resources/
    ├── application.yml             # 配置文件
    ├── schema.sql                  # 建表 SQL
    ├── static/css/style.css        # 样式
    └── templates/                  # Thymeleaf 页面
```

## 技术架构

```
[Thymeleaf 前端] ←→ [Controller] ←→ [Service]
                                        │
                         ┌──────────────┼──────────────┐
                         ▼              ▼              ▼
                      MySQL       火山引擎 Ark     内存向量库
                    (数据存储)     (AI 能力)       (余弦检索)
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

## 功能清单

- ✅ 用户注册/登录（Session 认证）
- ✅ 知识库 CRUD
- ✅ PDF/TXT/MD 文档上传、切片、向量化
- ✅ 多模态嵌入 API（火山引擎特有格式）
- ✅ 内存向量库 + MySQL 双写
- ✅ RAG 问答（文档命中时基于文档，未命中时自由对话）
- ✅ Markdown 渲染（marked.js 库）
- ✅ 引用来源显示 & 点击弹窗
- ✅ 对话创建/切换/删除
- ✅ 历史消息加载

## 常见问题

**Q: 启动报错？**
检查 MySQL 有没有启动，用户名密码对不对，API Key 是否有效。

**Q: 上传文档后 chunk_count 一直为 0？**
查看控制台日志是否有 embedding API 报错。可能是嵌入模型 key 配置不对或模型已退役。

**Q: Maven 打包报错 Unable to rename？**
Windows 文件锁定导致。先手动删了 `target/*.jar` 再重新打包，或者用 `mvn compile` + `spring-boot:run` 绕过 repackage。

**Q: Embedding API 报 401？**
API Key 不对或者没有开通方舟模型访问权限。检查 `app.openai.api-key` 配置。

**Q: 回答完全不基于文档内容？**
检查控制台日志中 `RAG 检索到 X 条结果` 的数值。如果为 0 且不是通用问题，可能是相似度阈值 0.3 太高，可以改为 0.2。
