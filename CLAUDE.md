# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

RAG-SERVER 是基于 Spring Boot 3.2 和 Spring AI 的企业级 RAG（检索增强生成）框架，支持混合检索（Dense向量 + Sparse BM25）和 MCP 协议集成。

**技术栈**：
- Spring Boot 3.2.1 (Java 17+)
- Spring AI 1.0.0-M3（仅用于 ChatClient/EmbeddingClient 抽象）
- 阿里云 DashScope（LLM + Embedding）
- Milvus 2.5+（向量数据库，支持混合检索）
- Apache PDFBox 3.0.8（文档处理）
- H2/SQLite（元数据存储）

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
# 启动 Milvus（开发环境）
docker-compose up -d milvus

# 查看服务状态
docker-compose ps

# 停止所有服务
docker-compose down
```

### 端点访问
- 健康检查：`http://localhost:8080/actuator/health`
- 应用信息：`http://localhost:8080/actuator/info`
- 性能指标：`http://localhost:8080/actuator/metrics`
- Dashboard：`http://localhost:8080/dashboard`（阶段 H 实现后）

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
External Services (DashScope API, Milvus Server, H2 Database)
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
- Schema：`dense_vector` (2048维) + `sparse_vector` (BM25) + `metadata` (JSON)
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

#### IngestionPipeline (`ingestion/IngestionPipeline.java`)
- 协调摄取流程各阶段
- 事务管理（`@Transactional`）
- 错误处理与历史记录

## 配置管理

### 环境变量
```bash
# 必需：DashScope API 密钥
export DASHSCOPE_API_KEY="sk-xxx"

# 可选：Milvus 地址（默认 http://localhost:19530）
export MILVUS_URI="http://milvus-server:19530"
```

### 配置文件层次
- `application.yaml`：通用配置（所有环境共享）
- `application-dev.yaml`：开发环境配置
- `application-prod.yaml`：生产环境配置
- `application-test.yaml`：测试环境配置

### 关键配置项
- `spring.ai.dashscope.*`：DashScope 客户端配置（API key、模型名、QPS 限流）
- `milvus.*`：Milvus 连接和 Collection 配置
- `rag.chunking.*`：文档切分策略（chunk-size=512, overlap=128）
- `rag.retrieval.*`：检索参数（top-k、是否启用 Rerank）

## 开发工作流

### 任务驱动开发
项目采用 53 个任务的阶段化开发（见 `DEV_SPEC.md`）：
- 阶段 A：Spring Boot 初始化
- 阶段 B：DashScope 集成
- 阶段 C：Milvus 集成
- 阶段 D：文档摄取 Pipeline
- 阶段 E：RAG 检索与生成
- 阶段 F：MCP 协议集成
- 阶段 G：元数据与管理
- 阶段 H：Dashboard
- 阶段 I：评估与优化
- 阶段 J：端到端验收

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
1. 确认 Milvus 已启动：`docker-compose ps`
2. 检查端口映射：`19530` 是否被占用
3. 查看日志：`docker-compose logs milvus`

### Embedding 性能慢
1. 检查是否启用批量处理（batch_size=16）
2. 调整 QPS 限流参数（`spring.ai.dashscope.qps`）
3. 考虑启用缓存（`config/CacheConfig.java`）

## 相关文档

- [DEV_SPEC.md](DEV_SPEC.md)：完整的 53 个开发任务和架构设计
- [README.md](README.md)：项目概述和快速开始
- [temp_progress_update.txt](temp_progress_update.txt)：开发进度更新（临时文件）
