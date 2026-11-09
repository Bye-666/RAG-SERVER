# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 🚀 快速启动开发团队

**新会话启动团队协作模式**（复制粘贴以下内容）：

```
启动 RAG-SERVER 开发团队：
- 阅读 .claude/team-config.md 了解团队规则
- 阅读 .claude/agents/task-architect.md 了解你的职责
- 阅读 PROGRESS.md 了解当前进度
- 以 TaskArchitect 身份开始工作
```

**团队配置**：
- `.claude/team-config.md` - 团队协作规则和流程
- `.claude/agents/task-architect.md` - TaskArchitect 职责定义
- `.claude/agents/backend-dev.md` - BackendDev 职责定义
- `.claude/agents/code-reviewer.md` - CodeReviewer 职责定义
- `.claude/TEAM_PROMPT.txt` - 4 行启动命令（快速复制）

**状态追踪**：
- `PROGRESS.md` - 开发进度看板（最重要）
- `docs/TECH_NOTES.md` - 技术难点记录

---

## 项目概述

RAG-SERVER 是基于 Spring Boot 3.4 和 Spring AI 的企业级 RAG（检索增强生成）框架，支持混合检索（Dense向量 + Sparse BM25）和 MCP 协议集成。

**技术栈**：
- Spring Boot 3.4.1 (Java 17+)
- Spring AI 1.0.0（ChatClient/EmbeddingClient 抽象 + MCP Server）
- 阿里云 DashScope（LLM + Embedding）
- Milvus 2.5.3（向量数据库，支持混合检索）
- MySQL 8.0（关系型数据库，元数据存储）
- Redis 7.2（缓存，Embedding 结果缓存）
- Apache PDFBox 3.0.8（文档处理）

## 常用命令

### 构建与运行
```bash
# 编译项目
mvn clean compile

# 运行应用（开发环境）
mvn spring-boot:run

# 打包为可执行 JAR
mvn clean package

# 运行打包后的应用
java -jar target/rag-server-1.0.0.jar

# 指定配置文件运行
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### 测试
```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=DashScopeChatClientTest

# 运行测试并生成覆盖率报告
mvn clean test jacoco:report

# 跳过测试进行构建
mvn clean package -DskipTests
```

### Docker 相关
```bash
# 启动所有服务（推荐）
docker-compose up -d

# 启动核心服务
docker-compose up -d milvus mysql redis

# 查看服务状态
docker-compose ps

# 查看特定服务日志
docker-compose logs -f milvus
docker-compose logs -f mysql
docker-compose logs -f redis

# 停止所有服务
docker-compose down

# 停止并清理所有数据卷（危险操作！）
docker-compose down -v

# 重启特定服务
docker-compose restart milvus
```

### 数据库管理
```bash
# 连接 MySQL
docker-compose exec mysql mysql -uraguser -pragpassword rag_server

# 查看表结构
docker-compose exec mysql mysql -uraguser -pragpassword -e "USE rag_server; SHOW TABLES;"

# 连接 Redis
docker-compose exec redis redis-cli

# 查看 Redis 缓存键
docker-compose exec redis redis-cli KEYS "rag:embeddings*"

# 清空 Redis 缓存
docker-compose exec redis redis-cli FLUSHDB
```

### 端点访问
**应用端点**：
- 健康检查：`http://localhost:8080/actuator/health`
- 应用信息：`http://localhost:8080/actuator/info`
- 性能指标：`http://localhost:8080/actuator/metrics`
- Dashboard：`http://localhost:8080/dashboard`（阶段 H 实现后）

**管理界面**：
- Milvus Attu（可视化管理）：`http://localhost:8000`
- MinIO Console：`http://localhost:9001`（minioadmin/minioadmin）

**数据库连接**：
- MySQL：`localhost:3306`（raguser/ragpassword，数据库：rag_server）
- Redis：`localhost:6379`
- Milvus：`localhost:19530`

## 架构设计要点

### 分层架构

```
Controller Layer (REST API + MCP Tools)
    ↓
Service Layer (业务编排：RagService, IngestionService, DocumentService)
    ↓
Component Layer
    ├─ Spring AI 层：ChatClient, EmbeddingClient (模型抽象)
    └─ 纯 Java 层：HybridRetriever, MilvusHybridStore, RecursiveSplitter (核心逻辑)
    ↓
External Services (DashScope API, Milvus Server, MySQL Database, Redis Cache)
```

### 重要设计决策

1. **Spring AI 的使用边界**：
   - ✅ 使用：`ChatClient` 和 `EmbeddingClient` 接口（统一模型抽象，便于切换提供商）
   - ❌ 不使用：`VectorStore` 接口（无法表达混合检索，强行适配会失去 Milvus 能力）
   - ❌ 不使用：`DocumentReader` 接口（PDFBox 提供更精细的控制）

2. **混合检索流程**：
   - Dense 检索（语义相似度）+ Sparse 检索（BM25 关键词）→ RRF 融合 → 可选 Rerank
   - 两路检索并行执行，任一路失败可降级
   - 配置在 `application.yaml` 的 `rag.retrieval` 部分

3. **文档摄取 Pipeline**：
   ```
   PDF → PdfLoader → RecursiveSplitter → ChunkRefiner 
       → MetadataEnricher → BatchEmbedding → MilvusHybridStore
   ```
   - SHA256 去重（`IngestionHistory` 表）
   - 支持批量 embedding（batch_size=16）
   - 元数据增强（规则模式 + 可选 LLM 模式）

### 核心组件

#### MilvusHybridStore (`retrieval/milvus/MilvusHybridStore.java`)
- 直接使用 Milvus SDK（不通过 Spring AI VectorStore）
- Schema：`dense_vector` (1024维) + `sparse_vector` (BM25) + `metadata` (JSON)
- 索引：Dense 用 AUTOINDEX/COSINE，Sparse 用 SPARSE_INVERTED_INDEX/IP
- 方法：`batchUpsert()`, `hybridSearch()`, `deleteBySourcePath()`

#### HybridRetriever (`retrieval/HybridRetriever.java`)
- 协调 Dense + Sparse 检索
- RRF 融合算法（k=60）
- 可选 Rerank（LLM 精排）

#### DashScopeChatClient (`ai/dashscope/DashScopeChatClient.java`)
- 实现 Spring AI 的 `ChatClient` 接口
- 限流（令牌桶，默认 10 QPS）
- 重试机制（3次，指数退避）

#### DashScopeEmbeddingClient (`ai/dashscope/DashScopeEmbeddingClient.java`)
- 实现 Spring AI 的 `EmbeddingClient` 接口
- 批量处理支持（batch_size=16）
- Redis 缓存集成（`@Cacheable`，TTL=1小时）

#### IngestionPipeline (`ingestion/IngestionPipeline.java`)
- 协调摄取流程各阶段
- 事务管理（`@Transactional`）
- 错误处理与历史记录

#### RagService (`service/RagService.java`)
- RAG 查询核心服务
- 流式输出支持（SSE）
- 性能监控（各阶段耗时统计）

## MCP 协议集成

### MCP 协议概述

**MCP (Model Context Protocol)** 是一种标准化的 AI Agent 工具接口协议，由 Anthropic 提出。RAG-SERVER 通过 Spring AI MCP Server 实现了 MCP 协议支持，让 Claude Desktop、Claude Code CLI 等客户端可以直接调用 RAG 功能。

**架构特点**：
- 基于 **Spring AI MCP Server** (`spring-ai-starter-mcp-server-webmvc:1.0.0`)
- 使用 **Function Calling** 机制自动注册 MCP Tools
- **stdio 模式**通信，无需 HTTP 端口
- 统一的异常处理和参数验证

### 可用的 MCP Tools

RAG-SERVER 提供 4 个 MCP Tools，涵盖查询、摄取、管理等核心功能：

#### 1. ragQuery - 查询知识库

**功能**：使用混合检索（Dense + Sparse）查询 RAG 知识库并生成答案。

**参数**：
- `question` (必需): 用户问题
- `topK` (可选): 检索文档数量，范围 1-50，默认 10
- `enableRerank` (可选): 是否启用 LLM 重排序，默认 false

**返回**：生成的答案 + 性能统计（检索耗时、LLM 耗时）

**示例**：
```json
{
  "question": "什么是 RAG？",
  "topK": 5,
  "enableRerank": false
}
```

#### 2. ragIngest - 上传文档到知识库

**功能**：将文档（PDF）摄取到 RAG 知识库，自动完成解析、切分、向量化、存储。

**参数**：
- `filePath` (必需): 文档文件路径（绝对路径）
- `collectionName` (可选): Collection 名称，默认 "rag_collection"
- `metadata` (可选): 自定义元数据（预留，当前版本未实现）

**返回**：
```json
{
  "success": true,
  "message": "Document ingested successfully",
  "filePath": "/path/to/doc.pdf",
  "fileName": "doc.pdf",
  "chunksProcessed": 42,
  "durationMs": 5230
}
```

#### 3. ragListDocs - 列出知识库文档

**功能**：查询已摄取的文档列表，支持按状态过滤。

**参数**：
- `status` (可选): 过滤状态，可选值 "SUCCESS" 或 "FAILED"，默认返回所有

**返回**：
```json
{
  "success": true,
  "message": "Success",
  "documents": [
    {
      "id": "abc123",
      "source": "report.pdf",
      "filePath": "/docs/report.pdf",
      "status": "SUCCESS",
      "chunkCount": 25,
      "processedAt": "2026-11-09T12:00:00Z"
    }
  ]
}
```

#### 4. ragDeleteDoc - 删除文档

**功能**：从知识库删除文档，联动清理向量数据和数据库记录。

**参数**：
- `idOrPath` (必需): 文档 ID（文件哈希）或文件路径

**返回**：
```json
{
  "success": true,
  "message": "Document deleted successfully",
  "idOrPath": "abc123"
}
```

### MCP Server 启动

MCP Server 随 Spring Boot 应用自动启动，使用 **stdio 模式**（标准输入输出）通信。

**配置**（`application.yaml`）：
```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        transport: stdio
```

**启动日志**：
```
注册 MCP Tool: ragQuery
注册 MCP Tool: ragIngest
注册 MCP Tool: ragListDocs
注册 MCP Tool: ragDeleteDoc
MCP Server 已启动（stdio 模式）
```

### 异常处理机制

MCP Tools 使用统一的异常处理体系，所有错误返回格式一致：

**异常类型**：
- `VALIDATION_ERROR`: 参数验证失败（空值、范围越界、格式错误）
- `SERVICE_ERROR`: 服务调用失败（摄取失败、检索失败）
- `RESOURCE_NOT_FOUND`: 资源不存在（文档不存在、Collection 不存在）
- `INTERNAL_ERROR`: 内部错误（未预期的异常）

**错误响应格式**：
```
Error [ERROR_CODE]: 错误描述信息
```

**示例**：
```
Error [VALIDATION_ERROR]: Required parameter 'question' is missing or empty
Error [RESOURCE_NOT_FOUND]: Document not found: abc123
Error [SERVICE_ERROR]: Ingestion failed: File not found
```

### 测试 MCP 功能

#### 方式 1: 通过集成测试

```bash
# 运行 MCP 集成测试（需要先修复其他测试类的编译错误）
mvn test -Dtest=RagMcpToolsIntegrationTest

# 测试覆盖：
# - ragQuery: 7 个测试（正常+参数验证+异常）
# - ragIngest: 6 个测试（正常+参数验证+异常）
# - ragListDocs: 6 个测试（正常+过滤+异常）
# - ragDeleteDoc: 6 个测试（正常+参数验证+异常）
```

#### 方式 2: 通过 Claude Desktop 集成

1. 配置 Claude Desktop 的 MCP 客户端
2. 添加 RAG-SERVER 作为 MCP 工具提供者
3. 在对话中直接调用 ragQuery、ragIngest 等工具

**注意**：完整的 MCP 协议测试（Claude Desktop 集成）将在项目完成后进行。

### 架构说明

**MCP 集成在分层架构中的位置**：

```
Claude Desktop / Claude Code CLI (MCP 客户端)
            ↓ (stdio / MCP 协议)
    Spring AI MCP Server
            ↓ (Function Calling)
    RagMcpTools (4 个 @Bean Function)
            ↓
Service Layer (RagService, IngestionService, DocumentService)
            ↓
Component Layer (HybridRetriever, MilvusHybridStore, etc.)
```

**技术实现**：
1. **自动注册**：Spring AI 自动扫描 `@Bean Function<?, ?>` 注册为 MCP Tools
2. **参数绑定**：使用 `record` 类型定义请求参数，自动 JSON 反序列化
3. **类型安全**：Function 签名明确输入输出类型，编译时检查
4. **异常转换**：业务异常自动转换为统一的 MCP 错误响应

## 配置管理

### 环境变量
```bash
# 必需：DashScope API 密钥
export DASHSCOPE_API_KEY="sk-xxx"

# 可选：数据库配置（默认值已在 application.yaml 中配置）
export MILVUS_URI="http://localhost:19530"
export MYSQL_HOST="localhost"
export MYSQL_PORT="3306"
export MYSQL_USER="raguser"
export MYSQL_PASSWORD="ragpassword"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
```

### 配置文件层次
- `application.yaml`：通用配置（所有环境共享）
- `application-dev.yaml`：开发环境配置
- `application-prod.yaml`：生产环境配置
- `application-test.yaml`：测试环境配置

### 关键配置项
- `spring.ai.dashscope.*`：DashScope 客户端配置（API key、模型名、QPS 限流）
- `spring.ai.mcp.server.*`：MCP Server 配置（enabled、transport 模式）
- `spring.datasource.*`：MySQL 数据源配置（URL、用户名、密码、连接池）
- `spring.data.redis.*`：Redis 缓存配置（host、port、连接池）
- `spring.cache.*`：Spring Cache 配置（类型、TTL、键前缀）
- `milvus.*`：Milvus 连接和 Collection 配置
- `rag.chunking.*`：文档切分策略（chunk-size=512, overlap=128）
- `rag.retrieval.*`：检索参数（top-k、是否启用 Rerank）

### 重要配置说明
**MCP Server 配置**：
- 传输模式：stdio（标准输入输出）
- 自动启动：随 Spring Boot 应用启动
- Tools 注册：自动扫描 @Bean Function
- 用途：为 Claude Desktop/CLI 提供 RAG 工具调用能力

**缓存配置**：
- 缓存类型：Redis（主方案）
- TTL：1小时（3600000ms）
- 键前缀：`rag:`
- 用途：缓存 Embedding 结果，减少 API 调用

**数据库配置**：
- 开发环境：`hibernate.ddl-auto: update`（自动更新表结构）
- 生产环境：应改为 `validate` 并使用 Flyway 管理迁移

**Docker 服务端口**：
- 应用：8080
- MySQL：3306
- Redis：6379
- Milvus：19530
- Milvus Metrics：9091
- Attu：8000
- MinIO API：9000
- MinIO Console：9001

## 开发工作流

### 首次启动流程
1. **启动 Docker 服务**：
   ```bash
   docker-compose up -d
   # 等待所有服务就绪（约 1-2 分钟）
   docker-compose ps  # 确认所有服务状态为 Up
   ```

2. **配置环境变量**：
   ```bash
   export DASHSCOPE_API_KEY="sk-xxx"
   ```

3. **启动应用**：
   ```bash
   mvn spring-boot:run
   # 首次启动会自动创建数据库表和 Milvus Collection
   ```

4. **验证服务**：
   - 访问健康检查：http://localhost:8080/actuator/health
   - 访问 Milvus 管理界面：http://localhost:8000

### 日常开发流程
1. 确保 Docker 服务运行：`docker-compose ps`
2. 修改代码
3. 重启应用：`mvn spring-boot:run`
4. 运行测试：`mvn test`
5. 提交代码（参考 Git 工作流程）

### 任务驱动开发
项目采用 53 个任务的阶段化开发（见 `DEV_SPEC.md` 和 `PROGRESS.md`）：
- ✅ 阶段 A：Spring Boot 初始化
- ✅ 阶段 B：DashScope 集成
- ✅ 阶段 C：Milvus 集成
- ✅ 阶段 D：文档摄取 Pipeline
- ✅ 阶段 E：RAG 检索与生成
- ⏳ 阶段 F：MCP 协议集成（进行中）
- ⏳ 阶段 G：元数据与管理
- ⏳ 阶段 H：Dashboard
- ⏳ 阶段 I：评估与优化
- ⏳ 阶段 J：端到端验收

**查看当前进度**：阅读 [PROGRESS.md](PROGRESS.md)

每个阶段完成后需验证对应的验收标准。

### 添加新功能
1. 在对应的 Service/Component 层添加实现
2. 遵循依赖注入原则（构造器注入）
3. 使用 Lombok 注解（`@Service`, `@Component`, `@Data`, `@Slf4j`）
4. 编写单元测试（Mock 外部依赖）
5. 必要时添加集成测试（使用 Testcontainers）

### 测试策略
- **单元测试**：Service、Retriever、Transformer（Mock 外部依赖）
- **集成测试**：ChatClient、EmbeddingClient、MilvusHybridStore（Testcontainers 启动真实 Milvus）
- **E2E 测试**：完整摄取→查询流程、MCP 对接
- **质量目标**：覆盖率 ≥ 80%，RAG Hit@5 ≥ 85%，Query P99 < 2s

**注意**：集成测试需要 Docker 环境，因为 Testcontainers 会启动临时容器。

## 代码风格

- 使用构造器注入，避免 `@Autowired` 字段注入
- Service 层方法带事务注解（`@Transactional`）
- 异常使用自定义业务异常（见 `exception/` 包）
- 日志使用 Lombok 的 `@Slf4j`
- 配置类使用 `@ConfigurationProperties` 绑定 YAML 配置

## Git 工作流程

### 提交流程（重要）
当发现现有功能存在问题时：
1. 修复问题
2. **等待用户确认修复效果**
3. 确认无误后再进行 git 提交

**重要**：不要在修复后直接提交，始终等待用户确认。

### 提交规范
- 使用中文或英文提交信息（项目采用中文）
- 格式：`<type>: <description>`
- Type：`feat`, `fix`, `refactor`, `docs`, `test`, `chore`
- 示例：`feat: 添加混合检索功能` 或 `fix: 修复 BM25 编码器空指针异常`

### 分支策略
- 主分支：`main`
- 不直接推送到 main，创建 PR 进行 code review

## 常见问题

### Maven 依赖问题
部分依赖（Spring AI、MCP SDK）在国内 Maven 镜像不可用，需要：
1. 配置 `~/.m2/settings.xml` 使用 Spring Milestones 仓库
2. 或使用 VPN/代理访问国外镜像

### Milvus 连接失败
1. 确认 Milvus 及依赖服务已启动：`docker-compose ps`
   - 应该看到 milvus、etcd、minio 都是 Up 状态
2. 检查端口映射：`19530` 是否被占用
3. 查看日志：`docker-compose logs milvus`
4. 等待 Milvus 完全启动（首次启动需要 1-2 分钟）
5. 重启服务：`docker-compose restart milvus`

### MySQL 连接失败
1. 检查 MySQL 是否已启动：`docker-compose ps mysql`
2. 验证数据库已创建：`docker-compose exec mysql mysql -uraguser -pragpassword -e "SHOW DATABASES;"`
3. 查看 MySQL 日志：`docker-compose logs mysql`
4. 检查 application.yaml 中的数据源配置是否正确

### Redis 缓存问题
1. 检查 Redis 是否已启动：`docker-compose ps redis`
2. 测试连接：`docker-compose exec redis redis-cli ping`（应返回 PONG）
3. 查看缓存键：`docker-compose exec redis redis-cli KEYS "*"`
4. 清空缓存重试：`docker-compose exec redis redis-cli FLUSHDB`

### Embedding 性能慢
1. 检查是否启用批量处理（batch_size=16）
2. 确认 Redis 缓存已启用（`spring.cache.type=redis`）
3. 查看缓存命中情况：检查应用日志
4. 调整 QPS 限流参数（`spring.ai.dashscope.qps`）

### Docker 容器无法启动
1. 检查 Docker 是否运行：`docker info`
2. 检查端口是否被占用：
   ```bash
   netstat -an | grep 19530  # Milvus
   netstat -an | grep 3306   # MySQL
   netstat -an | grep 6379   # Redis
   ```
3. 清理并重启：
   ```bash
   docker-compose down
   docker-compose up -d
   ```
4. 查看详细日志：`docker-compose logs -f`

## 相关文档

- [DEV_SPEC.md](DEV_SPEC.md)：完整的 53 个开发任务和架构设计
- [README.md](README.md)：项目概述和快速开始
- [PROGRESS.md](PROGRESS.md)：开发进度更新和技术决策记录
- [docker-compose.yml](docker-compose.yml)：Docker 服务编排配置
- [config/mysql/init.sql](config/mysql/init.sql)：MySQL 初始化脚本（4 张表）
- [config/redis/redis.conf](config/redis/redis.conf)：Redis 配置文件
