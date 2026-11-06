# RAG-SERVER

基于 Spring Boot 3.2 和 Spring AI 1.0.0-M3 的企业级 RAG（检索增强生成）框架

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## ✨ 核心特性

- 🚀 **企业级架构**：基于 Spring Boot 生态，依赖注入、配置管理开箱即用
- 🎯 **混合检索**：Dense 向量（语义） + Sparse BM25（关键词） + RRF 融合
- 🔌 **MCP 协议**：标准化 AI Agent 工具接口，与 Claude Desktop 无缝集成
- 📊 **完整可观测**：Actuator 监控 + Dashboard 管理界面
- 🧩 **灵活扩展**：模型层抽象，便于切换 LLM/Embedding 提供商
- 📄 **智能文档处理**：PDF 解析、递归切分、元数据增强
- 💾 **高效向量存储**：Milvus 2.5+ 支持，自动创建索引和 Schema

## 项目状态

🚧 **开发中**

### 开发进度

- ✅ **阶段 A**: Spring Boot 工程初始化
- ✅ **阶段 B**: DashScope 集成（Chat + Embedding）
- ✅ **阶段 C**: Milvus 混合检索集成
- ✅ **阶段 D**: 文档摄取 Pipeline
- ✅ **阶段 E**: RAG 检索与生成
- ⏳ **阶段 F**: MCP 协议集成（进行中）
- ⏳ **阶段 G-J**: 元数据管理、Dashboard、评估优化

查看详细任务：[DEV_SPEC.md](DEV_SPEC.md)

## 🛠 技术栈

| 类别 | 技术 | 版本 | 用途 |
|-----|------|------|------|
| **框架** | Spring Boot | 3.2.1 | 服务框架、依赖注入、配置管理 |
| **AI 抽象** | Spring AI | 1.0.0-M3 | ChatClient/EmbeddingClient 统一接口 |
| **LLM** | 阿里云 DashScope | qwen3.7-flash | 对话生成、文本理解 |
| **Embedding** | DashScope Embedding | qwen3.7-text-embedding | 文本向量化（1024 维） |
| **向量库** | Milvus | 2.5+ | 混合检索（Dense + Sparse） |
| **数据库** | H2 / SQLite | - | 元数据存储、摄取历史 |
| **文档处理** | Apache PDFBox | 3.0.8 | PDF 解析、图片提取 |
| **测试** | Testcontainers | 1.19.3 | 集成测试（Milvus 容器） |

## 🚀 快速开始

### 前置要求

- **JDK 17+**（推荐使用 Eclipse Temurin 或 Oracle JDK）
- **Maven 3.8+**
- **Docker**（用于运行 Milvus）
- **DashScope API Key**（从[阿里云百炼平台](https://bailian.console.aliyun.com/)获取）

### 安装步骤

#### 1. 克隆仓库

```bash
git clone <repository-url>
cd RAG-SERVER
```

#### 2. 配置环境变量

```bash
# Linux/Mac
export DASHSCOPE_API_KEY="sk-your-api-key-here"

# Windows (PowerShell)
$env:DASHSCOPE_API_KEY="sk-your-api-key-here"

# Windows (CMD)
set DASHSCOPE_API_KEY=sk-your-api-key-here
```

#### 3. 启动 Milvus 向量数据库

```bash
docker-compose up -d
```

验证 Milvus 已启动：
```bash
docker-compose ps
# 应该看到 milvus 容器状态为 Up
```

#### 4. 编译并运行应用

```bash
# 编译项目
mvn clean compile

# 运行应用（开发环境）
mvn spring-boot:run

# 或使用生产配置
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

#### 5. 验证服务

应用启动后访问以下端点：
- **健康检查**：http://localhost:8080/actuator/health
- **应用信息**：http://localhost:8080/actuator/info
- **性能指标**：http://localhost:8080/actuator/metrics

## 📁 项目结构

```
RAG-SERVER/
├── src/main/java/com/ragserver/
│   ├── RagServerApplication.java           # 应用主类
│   ├── ai/dashscope/                       # DashScope 客户端实现
│   │   ├── DashScopeChatClient.java        # Chat 客户端（限流+重试）
│   │   ├── DashScopeEmbeddingClient.java   # Embedding 客户端
│   │   └── DashScopeVisionClient.java      # Vision 客户端
│   ├── config/                             # 配置类
│   ├── controller/                         # REST 控制器
│   ├── service/                            # 业务服务层
│   │   ├── RagService.java                 # RAG 核心服务
│   │   ├── IngestionService.java           # 文档摄取服务
│   │   └── DocumentService.java            # 文档管理服务
│   ├── retrieval/                          # 检索层（纯 Java 实现）
│   │   ├── HybridRetriever.java            # 混合检索协调器
│   │   ├── milvus/MilvusHybridStore.java   # Milvus 存储（直接 SDK）
│   │   ├── BM25Encoder.java                # BM25 稀疏向量编码
│   │   └── RerankerService.java            # 重排序服务
│   ├── ingestion/                          # 摄取层
│   │   ├── IngestionPipeline.java          # 摄取流程编排
│   │   ├── loader/PdfLoader.java           # PDF 加载器
│   │   ├── splitter/RecursiveSplitter.java # 递归切分器
│   │   └── transformer/                    # 转换器（精炼、增强）
│   ├── entity/                             # JPA 实体
│   └── repository/                         # 数据访问层
├── src/main/resources/
│   ├── application.yaml                    # 通用配置
│   ├── application-dev.yaml                # 开发环境配置
│   ├── application-prod.yaml               # 生产环境配置
│   └── prompts/                            # Prompt 模板
├── src/test/                               # 测试代码
├── data/                                   # 运行时数据（gitignore）
│   ├── documents/                          # 文档存储
│   ├── images/                             # 图片存储
│   └── db/                                 # H2 数据库文件
├── docker-compose.yml                      # Docker 编排配置
├── pom.xml                                 # Maven 依赖管理
├── CLAUDE.md                               # Claude Code 工作指南
├── DEV_SPEC.md                             # 开发规范（53 个任务）
└── README.md                               # 本文件
```

## 📖 使用指南

### 文档摄取

```bash
# 通过 REST API 上传文档
curl -X POST http://localhost:8080/api/ingest \
  -F "file=@/path/to/document.pdf" \
  -F "collection=my_knowledge_base"
```

### RAG 查询

```bash
# 查询知识库
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "如何配置 Milvus？",
    "collection": "my_knowledge_base",
    "topK": 5,
    "enableRerank": false
  }'
```

### 文档管理

```bash
# 列出所有文档
curl http://localhost:8080/api/documents

# 删除指定文档
curl -X DELETE http://localhost:8080/api/documents/{documentId}

# 查看统计信息
curl http://localhost:8080/api/stats/overview
```

## 🔧 开发指南

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=HybridRetrieverTest

# 跳过测试构建
mvn clean package -DskipTests

# 生成测试覆盖率报告
mvn clean test jacoco:report
# 报告位置: target/site/jacoco/index.html
```

### 代码风格

- 使用 **构造器注入**（避免 `@Autowired` 字段注入）
- Service 层方法添加 `@Transactional` 注解
- 使用 Lombok 注解（`@Data`, `@Slf4j`, `@RequiredArgsConstructor`）
- 遵循 Spring Boot 命名约定

### 配置管理

配置文件优先级：`application-{profile}.yaml` > `application.yaml`

关键配置项：
- `spring.ai.dashscope.*`：DashScope API 配置
- `milvus.*`：Milvus 连接配置
- `rag.chunking.*`：文档切分策略
- `rag.retrieval.*`：检索参数（top-k、Rerank 开关）

### 添加新功能

1. 在对应的 Service/Component 层添加实现
2. 编写单元测试（Mock 外部依赖）
3. 必要时添加集成测试（使用 Testcontainers）
4. 更新相关文档

## 🏗 架构设计

### 核心设计原则

1. **Spring AI 使用边界**：
   - ✅ 使用 `ChatClient` 和 `EmbeddingClient`（统一抽象）
   - ❌ 不使用 `VectorStore`（无法支持混合检索）
   - ❌ 不使用 `DocumentReader`（PDFBox 更灵活）

2. **混合检索流程**：
   ```
   Query → Dense 检索 (语义) ║ Sparse 检索 (BM25)
                              ↓
                          RRF 融合
                              ↓
                      可选 Rerank (LLM)
                              ↓
                           结果返回
   ```

3. **文档摄取 Pipeline**：
   ```
   PDF → PdfLoader → RecursiveSplitter → ChunkRefiner 
       → MetadataEnricher → BatchEmbedding → MilvusHybridStore
   ```

详细架构设计见 [DEV_SPEC.md](DEV_SPEC.md) 第 2-3 章节。

## 🐛 故障排查

### Maven 依赖下载失败

**问题**：Spring AI 或 MCP SDK 依赖无法下载

**解决方案**：
```bash
# 方案 1: 配置 Maven 使用代理
# 编辑 ~/.m2/settings.xml，添加 Spring Milestones 仓库

# 方案 2: 使用 VPN 访问国外镜像源
```

### Milvus 连接失败

**问题**：应用启动时提示无法连接 Milvus

**解决方案**：
```bash
# 1. 检查 Milvus 是否已启动
docker-compose ps

# 2. 查看 Milvus 日志
docker-compose logs milvus

# 3. 重启 Milvus
docker-compose restart milvus

# 4. 检查端口是否被占用
netstat -an | grep 19530
```

### Embedding 性能慢

**问题**：文档摄取或查询速度慢

**解决方案**：
1. 检查批量处理是否启用（`batch_size=16`）
2. 调整 QPS 限流参数：`spring.ai.dashscope.qps`
3. 启用缓存：`spring.cache.type=caffeine`
4. 使用更快的 Embedding 模型

### H2 数据库文件锁定

**问题**：应用无法启动，提示数据库被锁定

**解决方案**：
```bash
# 删除数据库锁文件
rm data/db/rag_server.lock.db

# 或使用内存模式（数据不持久化）
# 修改 application.yaml: url: jdbc:h2:mem:rag_server
```

## 📚 文档资源

- [CLAUDE.md](CLAUDE.md) - Claude Code 工作指南
- [DEV_SPEC.md](DEV_SPEC.md) - 完整的 53 个开发任务和架构设计
- [API 文档](docs/API.md) - REST API 接口文档（待完善）
- [部署指南](docs/DEPLOYMENT.md) - 生产环境部署说明（待完善）

## 🤝 贡献指南

欢迎贡献代码！请遵循以下流程：

1. Fork 本仓库
2. 创建特性分支（`git checkout -b feature/AmazingFeature`）
3. 提交更改（`git commit -m 'feat: 添加某个特性'`）
4. 推送到分支（`git push origin feature/AmazingFeature`）
5. 创建 Pull Request

### 提交信息规范

- `feat`: 新功能
- `fix`: 问题修复
- `refactor`: 代码重构
- `docs`: 文档更新
- `test`: 测试相关
- `chore`: 构建/工具链更新

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 📮 联系方式

- 项目主页: [GitHub Repository](#)
- 问题反馈: [Issues](#)
- 技术文档: [Wiki](#)

---

**版本**: 1.0.0-SNAPSHOT  
**最后更新**: 2025-01-06  
**维护者**: RAG-SERVER 开发团队

**⭐ 如果这个项目对你有帮助，请给一个 Star！**