# RAG-SERVER 开发进度

> **项目定位**: 基于 Spring Boot + Spring AI 的企业级 RAG 框架  
> **技术栈**: Java 17 | Milvus 2.5 | MySQL 8.0 | Redis 7.2 | DashScope  
> **最后更新**: 2026-11-09 11:30  
> **维护人**: 小拜无霸哥

---

## 🎯 当前状态速览

| 指标 | 状态 |
|------|------|
| **当前阶段** | 阶段 F - MCP 协议集成 |
| **总体进度** | 27% (3/11 子任务) |
| **最近完成** | F-003 MCP Server 启动器 (2026-11-09 10:50) |
| **下一任务** | F-004 实现 rag_query Tool |
| **预计完成** | 2026-11-10（预计 8 小时） |
| **当前状态** | ⏸️ 暂停（文档重构完成） |

---

## 📋 当前任务看板

### 阶段 F：MCP 协议集成

**目标**: 实现 MCP 协议，让 Claude Code CLI 可以调用 RAG 功能  
**周期**: 2026-11-09 ~ 进行中  
**优先级**: P0（核心功能）

#### 任务列表

| ID | 任务描述 | 涉及文件 | 负责人 | 状态 | 备注 |
|----|----------|----------|--------|------|------|
| F-001 | 验证 MCP SDK 依赖 | pom.xml | BackendDev | ✅ 完成 | 编译通过 |
| F-002 | 创建 McpConfig 配置类 | config/McpConfig.java | BackendDev | ✅ 完成 | 1次修复 |
| F-003 | 实现 McpServerRunner | mcp/McpServerRunner.java | BackendDev | ✅ 完成 | 验证+修复 |
| F-004 | 实现 rag_query Tool | mcp/tools/RagQueryTool.java | BackendDev | ⏳ 待开始 | - |
| F-005 | 实现 rag_ingest Tool | mcp/tools/RagIngestTool.java | BackendDev | ⏳ 待开始 | - |
| F-006 | 实现 rag_list_docs Tool | mcp/tools/RagListDocsTool.java | BackendDev | ⏳ 待开始 | - |
| F-007 | 实现 rag_delete_doc Tool | mcp/tools/RagDeleteDocTool.java | BackendDev | ⏳ 待开始 | - |
| F-008 | 创建 McpToolsProvider | mcp/McpToolsProvider.java | BackendDev | ⏳ 待开始 | - |
| F-009 | 添加 MCP 异常处理 | mcp/exception/ | BackendDev | ⏳ 待开始 | - |
| F-010 | 编写 MCP 集成测试 | test/.../mcp/ | BackendDev | ⏳ 待开始 | - |
| F-011 | 更新项目文档 | CLAUDE.md, README.md | BackendDev | ⏳ 待开始 | - |

#### 关键里程碑
- ✅ **里程碑 1**: 基础设施就绪（F-001 ~ F-003）- 已完成
- ⏳ **里程碑 2**: 核心 Tools 实现（F-004 ~ F-007）- 未开始
- ⏳ **里程碑 3**: 集成与测试（F-008 ~ F-011）- 未开始

---

## ⏸️ 暂停/恢复记录

<details>
<summary>📌 最近一次暂停（点击展开）</summary>

**暂停时间**: 2026-11-09 11:30  
**暂停原因**: 文档重构、团队配置持久化  
**当前状态**: F-003 已完成并提交，基础设施就绪  
**下次恢复**: 从 F-004 开始（实现 rag_query Tool）  
**准备工作**: 无（可直接开始）

**已完成工作**：
- ✅ 重构 PROGRESS.md（新结构）
- ✅ 创建 docs/TECH_NOTES.md（技术难点）
- ✅ 创建 .claude/team-config.md（团队配置）
- ✅ 删除临时文档（T002/T003 实现报告）
- ✅ 提交 F-003 代码

</details>

---

## 🏗️ 项目架构概览

### 已完成的核心模块

```
RAG-SERVER/
├── AI 集成层
│   ├── ✅ DashScope Chat Client（对话生成）
│   ├── ✅ DashScope Embedding Client（向量化）
│   └── ✅ DashScope Vision Client（图像理解）
│
├── 检索层
│   ├── ✅ Milvus 混合检索（Dense + Sparse）
│   ├── ✅ BM25 Encoder（稀疏向量）
│   ├── ✅ RRF Fusion（倒数排名融合）
│   └── ✅ HybridRetriever（检索编排）
│
├── 摄取层
│   ├── ✅ PdfLoader（PDF 加载）
│   ├── ✅ RecursiveSplitter（文档切分）
│   ├── ✅ BatchEmbedding（批量向量化）
│   └── ✅ IngestionPipeline（流程编排）
│
├── 服务层
│   ├── ✅ RagService（查询+生成）
│   ├── ✅ IngestionService（文档摄取）
│   └── ✅ DocumentService（文档管理）
│
├── MCP 集成（进行中 27%）
│   ├── ✅ McpConfig（配置管理）
│   ├── ✅ McpServerRunner（服务启动）
│   └── ⏳ MCP Tools（4个工具待实现）
│
└── 基础设施
    ├── ✅ Docker Compose（Milvus + MySQL + Redis）
    ├── ✅ Swagger API 文档
    └── ✅ 管理 Dashboard（历史记录、统计）
```

---

## 📚 已归档任务

### 阶段 F：MCP 协议集成（进行中）

<details>
<summary>✅ 已完成任务（3/11）- 点击展开</summary>

| ID | 任务描述 | 完成时间 | 关键成果 | 技术难点 |
|----|----------|----------|----------|----------|
| F-001 | 验证 MCP SDK 依赖 | 2026-11-09 01:15 | 依赖可用，编译通过 | Maven 镜像配置 |
| F-002 | 创建 McpConfig 配置类 | 2026-11-09 01:35 | 配置绑定、JSR-303 校验 | @EnableConfigurationProperties 最佳实践 |
| F-003 | 实现 McpServerRunner | 2026-11-09 10:50 | stdio 模式启动，后台监听 | 后台线程机制验证 |

**阶段总结**：
- 基础设施已就绪，MCP Server 可以启动并监听
- 核心技术验证完成（非 daemon 线程机制）
- 下一步：实现具体的 MCP Tools

**Git 提交**：
```
feat(mcp): [F-001] 添加 MCP SDK 依赖
feat(mcp): [F-002] 创建 MCP Server 配置类
feat(mcp): [F-003] 实现 MCP Server 启动器
```

</details>

---

### 阶段 A-E：核心功能（已完成）

<details>
<summary>✅ 历史阶段总览 - 点击展开</summary>

#### 阶段 A：Spring Boot 初始化
- ✅ Maven 项目初始化
- ✅ 配置管理体系
- ✅ 数据库初始化

#### 阶段 B：DashScope 集成
- ✅ DashScope Chat Client（限流、重试）
- ✅ DashScope Embedding Client（batch、缓存）
- ✅ DashScope Vision Client

#### 阶段 C：Milvus 集成
- ✅ Milvus 混合检索（Dense + Sparse）
- ✅ BM25 Encoder
- ✅ RRF Fusion
- ✅ Collection 自动创建

#### 阶段 D：文档摄取 Pipeline
- ✅ PdfLoader（PDFBox）
- ✅ RecursiveSplitter（递归切分）
- ✅ BatchEmbedding（批量向量化）
- ✅ SHA256 去重

#### 阶段 E：RAG 检索与生成
- ✅ RagService（查询+生成）
- ✅ 流式输出（SSE）
- ✅ 耗时统计

**详细内容见本文档下方"已完成的核心功能"章节**

</details>

---

## 📝 技术决策记录

### 决策 5：任务 ID 命名规范
- **日期**: 2026-11-09
- **决策**: 使用 `阶段-序号` 格式（如 F-001）
- **原因**: 清晰区分不同阶段，支持跨阶段追踪
- **影响**: 所有任务 ID 统一格式

### 决策 4：Git 提交策略
- **日期**: 2026-11-09
- **决策**: 每个子任务审查通过后立即提交
- **原因**: 粒度细、易回滚、进度清晰
- **格式**: `feat(mcp): [F-003] 任务描述`

### 决策 3：PROGRESS.md 结构
- **日期**: 2026-11-09
- **决策**: 当前任务看板 + 归档 + 架构概览
- **原因**: 信息分层，状态优先，易于导航
- **特点**: 支持折叠，减少干扰

### 决策 2：MCP Server 启动方式
- **日期**: 2026-11-09
- **决策**: 移除 CountDownLatch，不阻塞主线程
- **原因**: 符合 Spring Boot 最佳实践，后台线程自动运行
- **技术验证**: 非 daemon 线程会阻止 JVM 退出

### 决策 1：数据库选择
- **日期**: 2026-11-07
- **决策**: MySQL 8.0 + Redis 7.2
- **原因**: 生产环境需要可靠的关系型数据库和缓存

---

## 🔗 相关文档

### 开发文档
- [CLAUDE.md](CLAUDE.md) - 项目规范和常用命令
- [DEV_SPEC.md](DEV_SPEC.md) - 完整的 53 个开发任务
- [README.md](README.md) - 项目概述和快速开始

### 技术文档
- [docs/TECH_NOTES.md](docs/TECH_NOTES.md) - 技术难点和解决方案
- [.claude/team-config.md](.claude/team-config.md) - Agent Teams 配置

### 配置文件
- [docker-compose.yml](docker-compose.yml) - Docker 服务编排
- [config/mysql/init.sql](config/mysql/init.sql) - MySQL 建表脚本
- [src/main/resources/application.yaml](src/main/resources/application.yaml) - 应用配置

---

## 💡 给下一个 Agent 的提示

### 如何恢复工作

1. **启动 Agent Teams**：
   ```
   复制 .claude/team-config.md 中的启动命令
   ```

2. **TaskArchitect 读取当前状态**：
   ```
   阅读本文件的"当前状态速览"和"当前任务看板"
   ```

3. **继续任务**：
   ```
   从"下一任务"开始执行（当前是 F-004）
   ```

### 常用命令

```bash
# Docker 管理
docker-compose up -d          # 启动所有服务
docker-compose ps             # 查看服务状态

# 应用启动
mvn spring-boot:run           # 启动应用

# 数据库检查
docker-compose exec mysql mysql -uraguser -pragpassword -e "USE rag_server; SHOW TABLES;"

# Redis 检查
docker-compose exec redis redis-cli KEYS "rag:*"
```

---

## 📊 已完成的核心功能

### 1. AI 集成
- ✅ **DashScope Chat Client** - 对话生成（限流、重试机制）
- ✅ **DashScope Embedding Client** - 文本向量化（batch 支持、Redis 缓存）
- ✅ **DashScope Vision Client** - 图像理解

### 2. 向量检索
- ✅ **Milvus 混合检索** - Dense + Sparse 双路检索
- ✅ **BM25 Encoder** - 自实现稀疏向量编码
- ✅ **RRF Fusion** - 倒数排名融合算法
- ✅ **HybridRetriever** - 检索编排（支持 Rerank）

### 3. 文档摄取
- ✅ **PdfLoader** - PDF 文档加载（PDFBox）
- ✅ **RecursiveSplitter** - 递归文档切分
- ✅ **BatchEmbedding** - 批量向量化
- ✅ **IngestionPipeline** - 摄取流程编排
- ✅ **去重机制** - SHA256 文件哈希去重

### 4. RAG 服务
- ✅ **RagService** - 查询 + 生成编排
- ✅ **流式输出** - SSE 实时流式响应
- ✅ **耗时统计** - 各阶段性能监控

### 5. 数据管理
- ✅ **IngestionHistory** - 文档上传历史
- ✅ **QueryHistory** - 查询历史记录
- ✅ **ImageIndex** - 图片索引管理
- ✅ **独立管理页面** - 历史记录查询界面

### 6. 基础设施
- ✅ **Docker Compose 完整配置**:
  - Milvus 2.5.3（原生支持混合检索 + 内置 BM25）
  - MySQL 8.0（关系型数据库）
  - Redis 7.2（缓存服务）
  - Etcd 3.5.12（Milvus 元数据）
  - MinIO（Milvus 对象存储）
  - Attu 2.4（Milvus 可视化管理）
- ✅ **MySQL 初始化脚本** - 4张表的建表语句
- ✅ **Redis 配置文件** - 持久化、LRU、512MB 限制
- ✅ **Swagger API 文档** - 完整的 REST API 文档

### 7. API 文档
- ✅ **Swagger UI** - http://localhost:8080/swagger-ui.html
- ✅ **OpenAPI 注解** - 所有 Controller 完整注解
  - RagController: 8个接口
  - StatsController: 2个接口
  - IngestionHistoryController: 2个接口
  - QueryHistoryController: 6个接口

---

**维护人**: 小拜无霸哥  
**最后更新**: 2026-11-09 11:30
