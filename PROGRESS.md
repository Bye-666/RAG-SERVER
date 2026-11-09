# RAG-SERVER 开发进度

> **项目定位**: 基于 Spring Boot + Spring AI 的企业级 RAG 框架  
> **技术栈**: Java 17 | Milvus 2.5 | MySQL 8.0 | Redis 7.2 | DashScope  
> **最后更新**: 2026-11-09 17:30  
> **维护人**: 小拜无霸哥

---

## 🎯 当前状态速览

| 指标 | 状态 |
|------|------|
| **当前阶段** | 阶段 F - MCP 协议集成 |
| **总体进度** | 82% (9/11 子任务) |
| **最近完成** | F-009 实现 MCP 异常处理 (2026-11-09 18:45) |
| **下一任务** | F-010 编写 MCP 集成测试 |
| **预计完成** | 2026-11-09（预计 1.5 小时） |
| **当前状态** | ▶️ 进行中（团队协作模式） |

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
| F-004 | 实现 ragQuery Tool | mcp/RagMcpTools.java | BackendDev | ✅ 完成 | 重构为Spring AI MCP |
| F-005 | 实现 rag_ingest Tool | mcp/RagMcpTools.java | BackendDev | ✅ 完成 | 1次修复 |
| F-006 | 实现 rag_list_docs Tool | mcp/RagMcpTools.java | BackendDev | ✅ 完成 | 1次修复 |
| F-007 | 实现 rag_delete_doc Tool | mcp/RagMcpTools.java | BackendDev | ✅ 完成 | 1次修复 |
| F-008 | 创建 McpToolsProvider | mcp/McpToolsProvider.java | - | ⏭️ 跳过 | Spring AI 自动注册 |
| F-009 | 添加 MCP 异常处理 | mcp/exception/ | BackendDev | ✅ 完成 | 2次审查 |
| F-010 | 编写 MCP 集成测试 | test/.../mcp/ | BackendDev | ⏳ 待开始 | - |
| F-011 | 更新项目文档 | CLAUDE.md, README.md | BackendDev | ⏳ 待开始 | - |

#### 关键里程碑
- ✅ **里程碑 1**: 基础设施就绪（F-001 ~ F-003）- 已完成
- ✅ **里程碑 2**: 核心 Tools 实现（F-004 ~ F-007）- 已完成
- 📝 **里程碑 3**: 集成与测试（F-008 ~ F-011）- 进行中（2/4 完成）

---

## 📜 开发历史

<details>
<summary><b>📅 2026-11-09 18:45 - F-009 完成（MCP 异常处理）</b></summary>

**任务**: 添加 MCP 异常处理  
**负责人**: BackendDev  
**结果**: ✅ 完成（2次审查后通过）

**工作内容**:
- ✅ 创建异常体系（5个类）:
  - `McpException` - 基础异常类（错误码 + details）
  - `McpValidationException` - 参数验证异常
  - `McpServiceException` - 服务调用异常
  - `McpResourceNotFoundException` - 资源不存在异常
  - `McpErrorResponse` - 统一错误响应格式
- ✅ 重构 4 个 MCP Tools 使用新异常处理
- ✅ 添加参数验证（question、topK、filePath、idOrPath）
- ✅ 统一错误响应格式：`[ERROR_CODE] message`
- ✅ 新增 `RagListDocsResponse` 包装类型
- ✅ 移除 Tool 层文件系统操作，职责分离
- ✅ 安全加固：通用异常不泄露敏感信息

**技术亮点**:
- 异常体系设计清晰，基类 + 3 个子类
- 静态工厂方法：`parameterRequired()`, `documentNotFound()` 等
- 链式调用：`addDetail()` 返回 this
- 安全防护：生产环境不返回 details，通用异常返回通用消息

**审查记录**:
- 第 1 次审查：发现 2 Critical + 3 Major + 2 Minor 问题
  - Critical: ragListDocs 行为不一致、ragQuery 未捕获异常
  - Major: 文件验证职责错位、敏感信息泄露、details 字段安全
- 修复后：所有 Critical 和 Major 问题解决
- 第 2 次审查：✅ 通过，剩余 1 个 Minor 问题不阻塞提交

**验收**:
- ✅ 编译成功
- ✅ 应用启动成功（11.582秒）
- ✅ 4 个 MCP Tools 成功注册
- ✅ 健康检查通过
- ✅ 代码审查通过

**统计**:
- 新增文件：5 个（exception 包）
- 修改文件：2 个（RagMcpTools.java, McpErrorResponse.java）
- 新增代码：~450 行
- 删除代码：~20 行（文件系统验证）
- ⏱️ 耗时：约 90 分钟（实现 40 分钟 + 审查修复 50 分钟）
- 📦 提交：commit c709e7f

</details>

<details>
<summary><b>📅 2026-11-09 18:15 - F-008 跳过（架构变更）</b></summary>

**任务**: 创建 McpToolsProvider  
**决策**: ⏭️ 跳过  
**原因**: Spring AI MCP 框架提供自动注册机制

**决策背景**:
- ✅ 当前架构：Spring AI MCP 框架（spring-ai-starter-mcp-server-webmvc:1.0.0）
- ✅ 自动注册：Spring AI AutoConfiguration 自动扫描 @Bean Function 作为 MCP Tools
- ✅ 已实现功能：4个 Function Bean 已在 RagMcpTools.java 中定义
- ❌ 不需要手动 Provider：会与自动配置冲突
- ❌ 原始规范已过时：DEV_SPEC.md 基于底层 MCP SDK 设计

**架构说明**:
```
Spring AI MCP 框架启动流程：
1. Spring Boot 启动
2. AutoConfiguration 扫描所有 @Bean Function<?, ?>
3. 自动注册为 MCP Tools
4. 启动 MCP Server（stdio 模式）
5. 监听 MCP 协议请求
```

**影响**:
- ✅ 简化架构：减少手动配置
- ✅ 符合 Spring 最佳实践：Convention over Configuration
- ✅ 易于扩展：新增 Tool 只需添加 @Bean Function

**下一步**: F-009 添加 MCP 异常处理

</details>

<details>
<summary><b>📅 2026-11-09 17:30 - F-007 完成（🎉 里程碑 2 完成）</b></summary>

**任务**: 实现 rag_delete_doc Tool  
**负责人**: BackendDev  
**结果**: ✅ 完成（1次修复后通过审查）

**工作内容**:
- ✅ 创建：`RagDeleteDocRequest` record（idOrPath 参数）
- ✅ 创建：`RagDeleteDocResponse` record（success, message, idOrPath）
- ✅ 实现：`ragDeleteDoc` Function Bean（删除文档）
- ✅ 调用：`DocumentService.deleteDocument()` 联动清理
- ✅ 支持：通过文件哈希或文件路径删除
- ✅ 添加：参数验证（null 和空字符串检查）
- 🐛 修复：缺少参数验证问题
- ⏱️ 耗时：约 30 分钟（实现 20 分钟 + 修复 10 分钟）
- 📦 提交：commit 5ff14fd

**验收**:
- ✅ 编译成功
- ✅ 应用启动成功
- ✅ ragDeleteDoc Tool 已注册
- ✅ 代码审查通过

**里程碑成就**:
🎉 **里程碑 2 完成**：4 个核心 MCP Tools 全部实现！
- ✅ ragQuery - 查询知识库
- ✅ ragIngest - 上传文档
- ✅ ragListDocs - 列出文档
- ✅ ragDeleteDoc - 删除文档

**审查记录**:
- 第 1 次审查：发现 Critical 问题（缺少参数验证）
- 修复后：直接通过（小修复无需二次完整审查）

</details>

<details>
<summary><b>📅 2026-11-09 16:00 - F-006 完成</b></summary>

**任务**: 实现 rag_list_docs Tool  
**负责人**: BackendDev  
**结果**: ✅ 完成（1次修复后通过审查）

**工作内容**:
- ✅ 创建：`RagListDocsRequest` record（支持可选的status参数）
- ✅ 实现：`ragListDocs` Function Bean（查询文档列表）
- ✅ 调用：`DocumentService.listDocuments()` 和 `listDocumentsByStatus()`
- ✅ 返回：`List<DocumentService.DocumentInfo>` 结构化对象
- ✅ 支持：按状态过滤（SUCCESS/FAILED），大小写容错
- 🐛 修复：异常处理不一致（改为返回空列表）
- ⏱️ 耗时：约 30 分钟（实现 20 分钟 + 修复 10 分钟）
- 📦 提交：commit b110200

**验收**:
- ✅ 编译成功
- ✅ 应用启动成功
- ✅ ragListDocs Tool 已注册
- ✅ 代码审查通过

**技术亮点**:
- 日志记录完善（注册、调用、成功、警告、错误全覆盖）
- 防御性编程（null检查、空字符串检查、大小写容错）
- 边界情况处理（无效status降级为返回所有文档）
- 异常处理统一（返回空列表，不抛异常）

**审查记录**:
- 第 1 次审查：发现 Critical 问题（异常处理不一致）
- 修复后：直接通过（小修复无需二次完整审查）

</details>

<details>
<summary><b>📅 2026-11-09 15:20 - F-005 完成</b></summary>

**任务**: 实现 rag_ingest Tool  
**负责人**: BackendDev  
**结果**: ✅ 完成（1次修复后通过审查）

**工作内容**:
- ✅ 创建：`RagIngestRequest` record（filePath, collectionName, metadata）
- ✅ 创建：`RagIngestResponse` record（success, message, fileName, chunksProcessed, durationMs）
- ✅ 实现：`ragIngest` Function Bean（文档上传到知识库）
- ✅ 调用：`IngestionService.ingestDocuments()` 完成摄取
- ✅ 添加：参数验证、错误处理、性能监控
- 🐛 修复：潜在的 IndexOutOfBoundsException（空列表安全检查）
- ⏱️ 耗时：约 40 分钟（实现 30 分钟 + 修复 10 分钟）
- 📦 提交：commit 0332170

**验收**:
- ✅ 编译成功
- ✅ 应用启动成功
- ✅ ragIngest Tool 已注册
- ✅ 代码审查通过

**技术难点**:
- 防御性编程：处理 batchResult 既无成功也无失败的边界情况
- 参数设计：metadata 参数预留，待阶段 G 实现

**审查记录**:
- 第 1 次审查：发现 Major 问题（IndexOutOfBoundsException）
- 修复后：直接通过（小修复无需二次完整审查）

</details>

<details>
<summary><b>📅 2026-11-09 13:55 - F-004 完成（重大重构）</b></summary>

**任务**: 实现 ragQuery Tool  
**负责人**: BackendDev  
**结果**: ✅ 完成（重构为 Spring AI MCP 框架）

**工作内容**:
- 🔄 重大架构变更：从底层 MCP SDK 切换到 Spring AI MCP
- ⬆️ 依赖升级：Spring Boot 3.2.1 → 3.4.1, Spring AI 1.0.0-M3 → 1.0.0
- ➕ 添加：`spring-ai-starter-mcp-server-webmvc:1.0.0`
- ➖ 删除：底层 MCP SDK 及旧配置类（5个文件）
- ✅ 创建：`RagMcpTools.java` - 使用 Spring AI Function Calling
- ✅ 实现：`ragQuery` Tool（查询知识库，支持 topK 和 rerank）
- 📝 更新：`application.yaml` - Spring AI MCP Server 配置
- 🐛 修复：Lombok 注解处理失效（版本冲突导致）
- ⏱️ 耗时：约 3 小时（大部分时间在调试依赖）
- 📦 提交：commit 604b968

**验收**:
- ✅ 编译成功
- ✅ 应用启动成功
- ✅ MCP Server 已启用
- ✅ ragQuery Tool 已注册
- ⚠️ MCP 协议测试：推迟到项目完成后

**技术难点**:
- 版本冲突：Spring AI MCP 1.0.0 需要 Spring Boot 3.4+ 和 Spring AI 1.0.0
- Lombok 失效：实现接口时触发注解处理器冲突
- YAML 配置：重复键导致启动失败

</details>

<details>
<summary><b>📅 2026-11-09 11:30 - 暂停（文档重构）</b></summary>

**暂停原因**: 文档重构、团队配置持久化  
**当前状态**: F-003 已完成并提交，基础设施就绪  

**已完成工作**：
- ✅ 重构 PROGRESS.md（新结构）
- ✅ 创建 docs/TECH_NOTES.md（技术难点）
- ✅ 创建 .claude/team-config.md（团队配置）
- ✅ 删除临时文档（T002/T003 实现报告）
- ✅ 提交 F-003 代码

</details>

---

## ⏸️ 暂停/恢复记录

<details>
<summary>📌 当前状态（点击展开）</summary>

**当前时间**: 2026-11-09 17:30  
**当前状态**: ▶️ 进行中（团队协作模式）  
**当前任务**: F-008 创建 McpToolsProvider  
**负责人**: 待分配  
**准备工作**: 等待 TaskArchitect 下一步指示
**里程碑**: 🎉 里程碑 2 已完成，进入里程碑 3

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
