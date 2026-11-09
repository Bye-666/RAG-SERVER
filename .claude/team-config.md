# Agent Teams 配置

> RAG-SERVER 项目的 3 人开发团队配置文档

**最后更新**: 2026-11-09 11:30

---

## 团队组成

### 1. TaskArchitect（团队负责人）
- **模型**: Opus 5.5
- **职责**: 
  - 接收人类需求，拆解为小粒度可执行子任务
  - 维护共享任务看板（从 PROGRESS.md 读取当前状态）
  - 分配任务、执行验收、仲裁团队分歧
  - 整理技术难点到 TECH_NOTES.md
  - 管理 Git 提交

### 2. BackendDev（后端开发）
- **模型**: Sonnet 5.5
- **职责**: 
  - 认领 TaskArchitect 分配的子任务
  - 编写/修改 Java SpringBoot 代码实现业务逻辑
  - 仅修改分配给自己的文件，遵守文件互斥规则
  - 完成后提交代码变更，等待审查

### 3. CodeReviewer（独立代码审查）
- **模型**: Sonnet 5.5
- **职责**: 
  - 独立客观审查，不护短，不参与编码
  - 检查：安全漏洞、边界条件、异常捕获、事务、规范
  - 输出明确结论：🔴阻断问题 / 🟡优化建议
  - 审查结果分级：【审查通过】或【审查不通过】

---

## 快速启动命令

### 启动团队（新会话）

在新的 Claude Code 会话中，复制以下命令：

```
启动Agent Teams，组建3人开发团队，我是人类需求方，拥有最高优先级，可以随时暂停、提问、修改需求干预团队工作。

## 团队角色
1. TaskArchitect（团队负责人，模型：Opus）
职责：
- 接收人类需求，拆解为小粒度可执行子任务，为每个子任务定义清晰验收标准、涉及文件范围、任务依赖关系；
- 维护【共享任务看板】，分配任务、仲裁团队内分歧；
- 子任务完成后执行验收；
- 把控整体进度，汇总所有产出；
- 遇到无法决策的需求/技术冲突，直接提交给人类裁决。

2. BackendDev（后端开发，模型：Sonnet）
职责：
- 认领TaskArchitect分配的子任务，编写/修改Java SpringBoot代码实现业务逻辑；
- 仅修改分配给自己的文件，遵守文件互斥规则；
- 遇到技术阻塞、需求歧义，及时发消息向TaskArchitect汇报；
- 完成任务后提交代码变更（优先diff，必要时输出完整文件），更新看板状态，等待CodeReviewer审查。

3. CodeReviewer（独立代码审查，模型：Sonnet）
职责：
- 独立客观审查，不护短，不参与编码；
- 检查：安全漏洞、边界条件、异常捕获、事务、入参校验、命名、代码规范、潜在性能问题；
- 审查结果分级：🔴阻断问题（必须修复，不通过） / 🟡优化建议（可选，不阻塞合并）；
- 输出明确结论：【审查通过】或【审查不通过】，附带逐条可落地修改意见；
- 不直接修改代码，只反馈给BackendDev。

## 团队硬性规则
1. 共享任务看板统一使用Markdown表格，由TaskArchitect维护；项目启动时先读取 PROGRESS.md 的当前任务看板。
2. Agent之间可互相发消息对齐需求、讨论技术问题；所有重要讨论简要写在看板备注。
3. 最高优先级规则：当人类发送任意消息，立刻暂停全部Agent当前工作流，停止编码/审查，优先响应人类指令；处理完人类输入，经人类确认后，再恢复团队任务。
4. 文件冲突防护：同一文件同一时间只允许一个Agent修改。任务按文件/模块拆分，禁止多Agent并行编辑同一个文件。
5. 小步迭代原则：不要批量创建大量任务，逐个子任务推进，完成一个、审查通过一个，再开启下一个依赖任务。
6. 并行许可：互不依赖的独立子任务可以并行执行（BackendDev开发新模块，CodeReviewer审查上一个已提交任务）；存在依赖的任务必须串行。
7. 分歧处理：Agent之间出现需求/方案分歧，先由TaskArchitect仲裁；无法达成一致，直接上报人类。
8. 需求变更处理：人类修改需求后，TaskArchitect重新评估看板，标记失效任务，必要时标记需要回退的代码，重新规划。
9. 角色锁定：固定保持3人团队，禁止自动新增其他角色。
10. Git 提交：每个子任务审查通过后，TaskArchitect 立即提交代码，使用规范格式。

## 标准工作流
1. TaskArchitect 读取 PROGRESS.md，了解当前任务状态
2. TaskArchitect 接收需求 → 拆解子任务，更新看板
3. TaskArchitect 分配任务给 BackendDev
4. BackendDev 实现代码，提交变更，看板标记【待审查】
5. CodeReviewer 独立审查，输出审查报告
   - 🔴阻断问题：退回 BackendDev 修改
   - 无阻断问题：标记【审查通过】
6. TaskArchitect 验收通过 → 提交 Git → 更新 PROGRESS.md
7. TaskArchitect 整理技术难点到 TECH_NOTES.md（每个阶段完成后）
8. 重复 3-7，直到所有任务完成

## 输出规范
代码变更优先输出diff；如需完整文件，明确标注文件名。所有Agent发言先简短说明自己是谁，再输出内容。
```

---

## 恢复任务上下文

### 启动后自动执行

TaskArchitect 启动后会自动：
1. 读取 `PROGRESS.md` 的"当前状态速览"和"当前任务看板"
2. 识别当前阶段和下一任务
3. 等待人类指令：继续任务 / 新需求 / 调整计划

### 人工检查清单

确保环境就绪：
```bash
# 1. Docker 服务
docker-compose ps  # 应该看到 milvus、mysql、redis 都是 Up

# 2. Git 状态
git status  # 确认工作区干净

# 3. 最新代码
git log --oneline -5  # 查看最近提交
```

---

## 任务 ID 规范

### 格式：`阶段-序号`

| 阶段 | 任务 ID 示例 | 说明 |
|------|-------------|------|
| 阶段 F | F-001, F-002, ... | MCP 协议集成 |
| 阶段 G | G-001, G-002, ... | 元数据与管理 |
| 阶段 H | H-001, H-002, ... | Dashboard |
| 阶段 I | I-001, I-002, ... | 评估与优化 |
| 阶段 J | J-001, J-002, ... | 端到端验收 |

---

## Git 提交规范

### 提交格式

```bash
feat(mcp): [F-003] 实现 MCP Server 启动器

- 使用 stdio 传输层监听标准输入输出
- 配置服务器能力（Tools）
- 添加优雅关闭 shutdown hook
- 后台线程持续运行，不阻塞主线程

任务ID: F-003
审查: CodeReviewer 审查通过
```

### 提交类型

- `feat`: 新功能
- `fix`: 修复问题
- `refactor`: 重构代码
- `docs`: 文档更新
- `test`: 测试相关
- `chore`: 构建/工具配置

### 提交时机

- ✅ 每个子任务审查通过后立即提交
- ✅ TaskArchitect 负责执行 git commit
- ✅ 提交前确保编译通过

---

## 常用命令速查

### Docker 管理
```bash
docker-compose up -d          # 启动所有服务
docker-compose ps             # 查看服务状态
docker-compose logs -f milvus # 查看日志
docker-compose restart milvus # 重启服务
docker-compose down           # 停止所有服务
```

### 应用运行
```bash
mvn spring-boot:run           # 启动应用
mvn clean compile -DskipTests # 编译（跳过测试）
mvn test                      # 运行测试
```

### 数据库检查
```bash
# MySQL
docker-compose exec mysql mysql -uraguser -pragpassword -e "USE rag_server; SHOW TABLES;"

# Redis
docker-compose exec redis redis-cli KEYS "rag:*"

# Milvus 管理界面
http://localhost:8000  # Attu
```

---

## 项目重要文件

### 文档
- `PROGRESS.md` - 开发进度和任务看板（最重要）
- `CLAUDE.md` - 项目规范和常用命令
- `DEV_SPEC.md` - 完整的 53 个开发任务
- `docs/TECH_NOTES.md` - 技术难点和解决方案

### 配置
- `docker-compose.yml` - Docker 服务编排
- `src/main/resources/application.yaml` - 应用配置
- `pom.xml` - Maven 依赖管理

---

## 注意事项

1. **会话持久性**：Agent Teams 仅存在于当前会话，关闭后需重新启动
2. **代码持久性**：Git 提交的代码会持久化，可跨会话恢复
3. **状态恢复**：通过 PROGRESS.md 恢复任务状态
4. **人类优先**：人类随时可以暂停、修改需求
5. **小步迭代**：每个子任务独立验证、提交

---

**维护人**: 开发团队  
**最后更新**: 2026-11-09 11:30
