# RAG-SERVER 开发进度

**最后更新**: 2026-11-07 23:55  
**当前负责人**: 小拜无霸哥

---

## 📊 已经做了什么

### 核心功能（已完成）

#### 1. AI 集成
- ✅ **DashScope Chat Client** - 对话生成（限流、重试机制）
- ✅ **DashScope Embedding Client** - 文本向量化（batch 支持）
- ✅ **DashScope Vision Client** - 图像理解

#### 2. 向量检索
- ✅ **Milvus 混合检索** - Dense + Sparse 双路检索
- ✅ **BM25 Encoder** - 自实现稀疏向量编码
- ✅ **RRF Fusion** - 倒数排名融合算法
- ✅ **HybridRetriever** - 检索编排（支持 Rerank）

#### 3. 文档摄取
- ✅ **PdfLoader** - PDF 文档加载（PDFBox）
- ✅ **RecursiveSplitter** - 递归文档切分
- ✅ **BatchEmbedding** - 批量向量化
- ✅ **IngestionPipeline** - 摄取流程编排
- ✅ **去重机制** - SHA256 文件哈希去重

#### 4. RAG 服务
- ✅ **RagService** - 查询 + 生成编排
- ✅ **流式输出** - SSE 实时流式响应
- ✅ **耗时统计** - 各阶段性能监控

#### 5. 数据管理
- ✅ **IngestionHistory** - 文档上传历史
- ✅ **QueryHistory** - 查询历史记录
- ✅ **ImageIndex** - 图片索引管理
- ✅ **独立管理页面** - 历史记录查询界面

#### 6. 基础设施配置（刚完成）
- ✅ **Docker Compose 完整配置**:
  - Milvus 2.5.3（原生支持混合检索 + 内置 BM25）
  - MySQL 8.0（关系型数据库）
  - Redis 7.2（缓存服务）
  - Etcd 3.5.12（Milvus 元数据）
  - MinIO（Milvus 对象存储）
  - Attu 2.4（Milvus 可视化管理）
- ✅ **MySQL 初始化脚本** - 4张表的建表语句
- ✅ **Redis 配置文件** - 持久化、LRU、512MB 限制
- ✅ **所有服务启动成功**

### 最近提交（Git）
```
6f9c83b docs: 添加项目进度文档
672461f feat: 配置 Docker 开发环境
57abb33 docs: 完善项目文档
b791e03 feat: 添加文档上传历史和查询历史独立页面
918549c feat: 完善查询流式输出功能
```

---

## 🔧 正在做什么 / 刚完成了什么

### ✅ 刚完成：Swagger API 文档（2026-11-08 16:45）

#### 实现内容
- ✅ **添加 SpringDoc OpenAPI 依赖**：springdoc-openapi-starter-webmvc-ui 2.3.0
- ✅ **创建 OpenApiConfig**：配置 API 元数据、服务器列表
- ✅ **添加 API 注解**：为 RagController 添加 @Tag、@Operation、@Parameter 注解
- ✅ **配置访问路径**：
  - Swagger UI: http://localhost:8080/swagger-ui.html
  - OpenAPI JSON: http://localhost:8080/v3/api-docs

#### 功能特性
- 📖 自动生成 REST API 文档
- 🧪 在线测试 API 接口
- 📋 清晰的请求/响应示例
- 🎨 美观的交互式界面

### ✅ 刚完成：前端重构和问题修复（2026-11-08 16:35）

#### 修复的问题
- ✅ **数据库字段缺失**：添加 `IngestionHistory.fileName` 字段
- ✅ **缓存配置冲突**：CacheConfig 添加 `@ConditionalOnProperty` 注解，避免与 Redis 冲突
- ✅ **模型配置更新**：
  - LLM：`qwen3.8-2.4t-a95b`
  - Embedding：`qwen3.7-text-embedding-flash`
- ✅ **文档更新**：CLAUDE.md 和 README.md 同步最新技术栈和配置

#### 前端重构
- ✅ **代码拆分**：将 1638 行的 index.html 拆分为：
  - `css/style.css` (515行) - 样式
  - `js/app.js` (787行) - 逻辑
  - `index.html` (334行) - 结构
- ✅ **提高可维护性**：模块化结构，便于后续开发

#### 待解决问题
- ⏳ **DocumentInfo 序列化问题**：`java.time.Instant` JSON 序列化失败
  - 已添加 `jackson-datatype-jsr310` 依赖
  - 已添加 `@JsonFormat` 注解
  - 问题仍存在，需要进一步排查

### ✅ 已完成：基础设施适配（2026-11-08 01:04）

#### 技术栈升级
- ✅ **数据库迁移**: H2 → MySQL 8.0
- ✅ **新增缓存**: Redis 7.2（主缓存方案）
- ✅ **Milvus 升级**: 确认使用 2.5.3（原生混合检索）

#### 完成的配置
- ✅ 添加 MySQL 驱动到 `pom.xml`
- ✅ 添加 Redis 依赖（Spring Data Redis + Commons Pool2）
- ✅ H2 改为仅测试使用
- ✅ 移除 SQLite 依赖
- ✅ 配置 MySQL 数据源（替代 H2）
- ✅ 配置 Redis 缓存（连接池、Spring Cache）
- ✅ 创建 `RedisConfig.java`（JSON 序列化、@Primary CacheManager）
- ✅ `DashScopeEmbeddingClient` 添加 `@Cacheable` 缓存
- ✅ Hibernate DDL 改为 `update`（开发环境自动建表）

#### 启动验证通过
- ✅ MySQL 连接成功（HikariPool-1 启动）
- ✅ Redis 配置生效（作为主缓存）
- ✅ Milvus Collection 自动创建（rag_knowledge_hub）
- ✅ 应用启动成功（端口 8080）

#### Git 提交
- ✅ Commit: `bb85f25 feat: 集成 MySQL 和 Redis，添加 Embedding 缓存`
- ✅ 6 个文件修改，612 行新增

---

## 📋 下一步要做什么

---

## 🎯 还有什么没做

### 后续开发任务（按优先级）

#### 优先级 P1：MCP 协议集成（阶段 F）
**预计耗时**: 4-6 小时

**要做的事**:
- [ ] 添加 MCP SDK 依赖到 pom.xml
- [ ] 实现 McpServer（stdio 模式）
- [ ] 定义 RAG Tools:
  - `rag_query` - 查询知识库
  - `rag_ingest` - 上传文档
  - `rag_list_docs` - 列出文档
  - `rag_delete_doc` - 删除文档
- [ ] 编写 MCP 集成测试
- [ ] 更新文档（如何使用 MCP 调用）

**验收标准**:
- Claude Desktop 可以通过 MCP 调用 RAG 功能
- 所有 Tools 正常工作
- 错误处理完善

---

#### 优先级 P2：Dashboard 完善（阶段 H）
**预计耗时**: 8-12 小时

**要做的事**:
- [ ] 文档管理界面:
  - 上传文档（拖拽上传）
  - 查看文档列表（分页、搜索）
  - 删除文档（确认对话框）
  - 查看文档详情（切块预览）
- [ ] 查询测试界面:
  - 查询输入框（支持多行）
  - 流式输出显示
  - 检索结果展示（高亮相关片段）
  - 参数调整（top-k、是否 Rerank）
- [ ] 统计数据可视化:
  - 文档数量趋势图（Chart.js）
  - 查询量统计
  - 平均响应时间
  - 缓存命中率
- [ ] 系统监控面板:
  - JVM 内存使用
  - Milvus Collection 大小
  - MySQL 连接池状态
  - Redis 内存使用

**验收标准**:
- 所有界面美观、易用
- 数据实时刷新
- 移动端适配

---

#### 优先级 P3：评估与优化（阶段 I）
**预计耗时**: 4-6 小时

**要做的事**:
- [ ] 检索质量评估:
  - 准备测试数据集（问题 + 标准答案）
  - 计算 Hit@5、MRR 指标
  - 对比 Dense、Sparse、Hybrid 效果
- [ ] 性能优化:
  - 测试 Query P99 延迟
  - 优化慢查询
  - 调整 Embedding batch_size
  - 调整 Milvus 索引参数
- [ ] 缓存优化:
  - 监控缓存命中率
  - 调整 TTL 策略
  - 识别热点数据

**验收标准**:
- Hit@5 ≥ 85%
- Query P99 < 2s
- 缓存命中率 ≥ 80%

---

#### 优先级 P4：生产环境准备
**预计耗时**: 4-6 小时

**要做的事**:
- [ ] 集成 Flyway（数据库版本管理）
- [ ] 将 `hibernate.ddl-auto` 改为 `validate`
- [ ] 编写数据库迁移脚本
- [ ] 配置日志聚合（ELK）
- [ ] 配置监控告警（Prometheus + Grafana）
- [ ] 编写部署文档
- [ ] 压力测试

---

#### 可选优化：Milvus 2.5 原生 API
**预计耗时**: 2-3 小时

**要做的事**:
- [ ] 研究 Milvus 2.5 的原生混合检索 API
- [ ] 重构 `MilvusHybridStore` 使用原生 API
- [ ] 对比性能（原生 vs 手动融合）
- [ ] 如果性能提升明显，则替换

**是否做**:
- 当前手动实现已经工作良好
- 建议在其他功能完成后再考虑

---

## 📝 技术决策记录

### 决策 1：数据库选择
- **从**: H2（内存/文件数据库）
- **到**: MySQL 8.0（关系型数据库）
- **原因**: 生产环境需要可靠的关系型数据库
- **日期**: 2026-11-07

### 决策 2：缓存方案
- **选择**: Redis 7.2 + Spring Cache
- **实现方式**: `@Cacheable` 注解
- **TTL**: 1小时
- **原因**: 节省 Embedding API 调用，提升性能
- **日期**: 2026-11-07

### 决策 3：Hibernate DDL 模式
- **开发环境**: `ddl-auto: update`（自动建表）
- **生产环境**: `ddl-auto: validate` + Flyway（后续）
- **原因**: 开发阶段快速迭代，生产环境保护数据
- **日期**: 2026-11-07

### 决策 4：Milvus 混合检索实现
- **选择**: 保持手动实现（Dense + Sparse 分别查询 + RRF 融合）
- **不选**: Milvus 2.5 原生混合检索 API
- **原因**: 当前实现稳定、代码清晰，后续可选优化
- **日期**: 2026-11-07

---

## 🔗 相关文档

- [CLAUDE.md](CLAUDE.md) - 项目规范和常用命令
- [DEV_SPEC.md](DEV_SPEC.md) - 完整的 53 个开发任务
- [README.md](README.md) - 项目概述和快速开始
- [docker-compose.yml](docker-compose.yml) - Docker 服务编排
- [config/mysql/init.sql](config/mysql/init.sql) - MySQL 建表脚本

---

## 💡 给下一个 Agent 的提示

### 如果你是新 Agent，应该这样开始：

1. **阅读本文件**，了解项目进度
2. **查看"下一步要做什么"**，找到当前任务
3. **检查 Git 状态**: `git status`
4. **检查 Docker 状态**: `docker-compose ps`
5. **从未完成的任务开始执行**

### 常用命令

```bash
# Docker 管理
docker-compose up -d          # 启动所有服务
docker-compose ps             # 查看服务状态
docker-compose logs -f milvus # 查看日志

# 应用启动
mvn spring-boot:run           # 启动应用

# 数据库检查
docker-compose exec mysql mysql -uraguser -pragpassword -e "USE rag_server; SHOW TABLES;"

# Redis 检查
docker-compose exec redis redis-cli KEYS "embeddings*"

# Milvus 管理界面
http://localhost:8000         # Attu 可视化管理
```

---

**更新时间**: 2026-11-07 23:55  
**下次更新**: 完成"基础设施适配"任务后
