# RAG-SERVER 团队协作规则

> 本文档定义团队的协作流程、硬性规则和项目约定

**最后更新**: 2026-11-09 18:00

---

## 🚀 快速启动（持久化模式）

**启动命令**（复制粘贴到新会话）：

```
启动 RAG-SERVER 开发团队：
- 阅读 .claude/team-config.md 了解团队规则
- 阅读 .claude/agents/task-architect.md 了解你的职责
- 阅读 PROGRESS.md 了解当前进度
- 以 TaskArchitect 身份开始工作
```

---

## 团队成员

本项目使用 **3 人 Agent Teams** 协作开发（持久化实例）：

| 角色 | Agent 文件 | 实例模式 | 核心职责 |
|------|-----------|---------|---------|
| **TaskArchitect** | `.claude/agents/task-architect.md` | 主会话 | 需求拆解、任务协调、验收提交 |
| **BackendDev** | `.claude/agents/backend-dev.md` | 持久化 | 编写代码、实现功能 |
| **CodeReviewer** | `.claude/agents/code-reviewer.md` | 持久化 | 独立审查、质量把关 |

**持久化模式说明**：
- BackendDev 和 CodeReviewer 在会话开始时创建一次
- 整个会话期间复用同一实例（保持上下文连续性）
- 通过 SendMessage 通信（不重复创建新实例）

---

## 标准工作流（持久化模式）

```
┌─────────────────────────────────────────────────────────┐
│                    人类提出需求                          │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
         ┌──────────────────────┐
         │   TaskArchitect      │
         │  - 读取 PROGRESS.md  │
         │  - 拆解任务          │
         │  - 定义验收标准      │
         └──────────┬───────────┘
                    │
                    │ SendMessage
                    ▼
         ┌──────────────────────┐
         │   BackendDev         │ ← 持久实例（复用）
         │  - 实现功能          │
         │  - 编写代码          │
         │  - 自我验证          │
         └──────────┬───────────┘
                    │
                    │ 返回结果
                    ▼
         ┌──────────────────────┐
         │   TaskArchitect      │
         │  - 接收变更          │
         │  - 分配审查          │
         └──────────┬───────────┘
                    │
                    │ SendMessage
                    ▼
         ┌──────────────────────┐
         │   CodeReviewer       │ ← 持久实例（复用）
         │  - 审查代码          │
         │  - 输出报告          │
         └──────────┬───────────┘
                    │
                    │ 审查结果
                    ▼
         ┌──────────────────────┐
         │   TaskArchitect      │
         │  - 通过：提交 Git    │
         │  - 不通过：SendMessage 要求修复  │
         │  - 更新 PROGRESS.md  │
         │  - 记录 TECH_NOTES   │
         └──────────────────────┘
```

---

## 🔒 持久化 Agent 约束（强制）

**本团队仅包含 3 个固定 Agent 实例，禁止动态创建额外 Agent**。

### 约束清单

| # | 约束规则 | 违规后果 |
|---|---------|---------|
| 1 | **固定成员**：仅允许 TaskArchitect、BackendDev、CodeReviewer 三个实例 | 浪费资源、上下文混乱 |
| 2 | **一次创建**：会话开始时一次性创建，必须携带固定 `name` 参数 | 重复创建导致实例冗余 |
| 3 | **SendMessage 通信**：任务流转全程使用 `SendMessage` 向已存在 Agent 发送消息 | 违反持久化原则 |
| 4 | **禁止重复创建**：不允许重复调用 `Agent()` 工具新建同名实例 | Token 浪费、上下文丢失 |
| 5 | **上下文保持**：所有 Agent 保留会话上下文，记住历史任务和 PROGRESS.md 状态 | 失去连续性 |
| 6 | **禁用 fork/isolation**：不允许派生临时子 Agent，不使用 `subagent_type: "fork"` | 违反团队架构 |
| 7 | **恢复权限**：若 Agent 会话中断，仅允许 TaskArchitect 执行恢复，不得新建 | 避免实例失控 |

### 正确示例

```python
# ✅ 会话初始化（仅一次）
Agent(
    name="BackendDev",  # 必须：固定名称
    subagent_type="backend-dev",  # 必须：对应配置文件
    description="后端开发实例",
    prompt="实现 F-009 任务..."
)

Agent(
    name="CodeReviewer",  # 必须：固定名称
    subagent_type="code-reviewer",
    description="代码审查实例",
    prompt="审查 F-009 代码..."
)

# ✅ 后续任务（使用 SendMessage）
SendMessage(
    to="BackendDev",  # 向已存在的实例发送
    message="实现 F-010 任务...",
    summary="MCP 集成测试"
)

SendMessage(
    to="CodeReviewer",
    message="审查 F-010 代码...",
    summary="审查集成测试"
)
```

### 错误示例

```python
# ❌ 重复创建 BackendDev
Agent(name="BackendDev", prompt="实现 F-009...")  # 第一次
Agent(name="BackendDev", prompt="实现 F-010...")  # 第二次 - 错误！

# ❌ 省略 name 参数
Agent(subagent_type="backend-dev", prompt="...")  # 动态生成名称 - 错误！

# ❌ 使用 fork 派生
Agent(subagent_type="fork", prompt="...")  # 违反团队架构 - 错误！

# ❌ 创建额外 Agent
Agent(name="Tester", prompt="...")  # 不在固定成员中 - 错误！
```

### 验证检查清单

TaskArchitect 在每次调用 Agent 工具前必须自检：

- [ ] 是否是会话初始化阶段？（仅允许创建一次）
- [ ] 是否携带了固定 `name` 参数？（BackendDev / CodeReviewer）
- [ ] 是否应该使用 `SendMessage` 而不是 `Agent`？
- [ ] 是否违反了 3 人团队限制？
- [ ] 是否使用了 fork/isolation 参数？

### 恢复机制

如果 Agent 实例意外中断（极少发生）：

```python
# 仅 TaskArchitect 可执行恢复
SendMessage(
    to="BackendDev",  # 尝试向原实例发送
    message="状态检查：你是否还在？"
)

# 若无响应，使用 ListAgents 确认
ListAgents()

# 确认实例已消失后，才允许重新创建
Agent(name="BackendDev", subagent_type="backend-dev", prompt="恢复会话...")
```

---

## 硬性规则

### 1. 文件互斥规则

**规则**：同一文件同时只能被一个 Agent 修改。

**执行**：
- TaskArchitect 明确分配文件给 BackendDev
- BackendDev **严格遵守**，不擅自修改其他文件
- 如有冲突，由 TaskArchitect 协调

**违规后果**：
- 可能导致代码冲突
- 需要回滚重做

### 2. 串行协作规则

**规则**：一次只处理一个子任务，避免并发。

**执行**：
- TaskArchitect 一次只分配一个子任务
- BackendDev 完成后才进入审查
- 审查通过后才开始下一个任务

**好处**：
- 避免冲突
- 状态清晰
- 易于回滚

### 3. 状态透明规则

**规则**：所有任务状态必须记录在 `PROGRESS.md`。

**执行**：
- TaskArchitect 负责更新
- 每个任务的状态变更都要记录
- 包含：完成时间、负责人、遇到的问题

**好处**：
- 跨会话可恢复
- 进度可追溯
- 人类随时了解状态

### 4. 小步迭代规则

**规则**：每个子任务独立验证、独立提交。

**执行**：
- 子任务预计耗时 ≤ 1 小时
- 涉及文件数量 ≤ 3 个
- 每个子任务单独 Git commit

**好处**：
- 易于回滚
- 减少冲突
- 便于审查

### 5. 人类优先规则

**规则**：人类可随时暂停、修改需求、推翻决定。

**执行**：
- Agent 的决定不是最终的
- 重大决策前询问人类
- 遇到阻塞立即报告

---

## Git 提交规范

### Commit Message 格式

遵循 **Conventional Commits**：

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

| Type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(mcp): 实现 ragQuery Tool` |
| `fix` | Bug 修复 | `fix(retrieval): 修复混合检索权重计算` |
| `refactor` | 重构 | `refactor(mcp): 切换到 Spring AI MCP 框架` |
| `docs` | 文档 | `docs: 更新 README 团队启动说明` |
| `test` | 测试 | `test(service): 添加 RagService 单元测试` |
| `chore` | 构建/工具 | `chore: 升级 Spring Boot 到 3.4.1` |
| `style` | 代码格式 | `style: 统一代码缩进为 4 空格` |
| `perf` | 性能优化 | `perf(retrieval): 优化向量查询性能` |

### 示例

```
feat(mcp): 实现 ragIngest Tool

- 添加 RagIngestRequest record 定义参数
- 实现 ragIngest Function Bean
- 调用 IngestionService 完成文档摄取
- 添加错误处理和日志记录

验收:
- ✅ 编译成功
- ✅ 应用启动成功
- ✅ Tool 已注册
```

---

## 技术难点记录规范

### 什么时候记录到 TECH_NOTES.md

- ✅ 非常规问题（超出标准开发流程）
- ✅ 耗时超过 1 小时的调试
- ✅ 版本冲突、依赖问题
- ✅ 重大架构决策
- ✅ 性能优化经验

- ❌ 常规 Bug 修复
- ❌ 简单的配置修改
- ❌ 标准的 CRUD 实现

### 记录格式

```markdown
## [问题标题]

**任务**: F-XXX 任务名称
**日期**: YYYY-MM-DD
**类型**: 架构设计 | 性能优化 | Bug 修复 | 依赖冲突

### 问题描述
[简要说明遇到的问题]

### 技术难点
#### 1. [难点名称]
**现象**: [具体表现]
**根本原因**: [深层原因]
**解决方案**: [代码/配置]
**教训**: [经验总结]

### 参考资料
[相关文档、Issue、PR 链接]
```

---

## 项目重要文件

### 必读文件（开始工作前）

- **`PROGRESS.md`** - 当前进度看板（最重要！）
- **`.claude/team-config.md`** - 团队协作规则（本文档）
- **`CLAUDE.md`** - 项目规范和常用命令

### Agent 定义文件

- **`.claude/agents/task-architect.md`** - TaskArchitect 职责和规范
- **`.claude/agents/backend-dev.md`** - BackendDev 职责和规范
- **`.claude/agents/code-reviewer.md`** - CodeReviewer 职责和规范

### 需维护的文件

- **`PROGRESS.md`** - 任务状态（TaskArchitect 维护）
- **`docs/TECH_NOTES.md`** - 技术难点（TaskArchitect 维护）

### 参考文件

- **`DEV_SPEC.md`** - 完整的 53 个开发任务
- **`README.md`** - 项目介绍
- **`pom.xml`** - Maven 依赖
- **`src/main/resources/application.yaml`** - 应用配置

---

## 技术栈快速参考

| 类别 | 技术 | 版本 |
|-----|------|------|
| 框架 | Spring Boot | 3.4.1 |
| AI 集成 | Spring AI | 1.0.0 |
| LLM | DashScope | qwen3.7-flash |
| Embedding | DashScope | qwen3.7-text-embedding |
| 向量库 | Milvus | 2.5.3 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.2 |

---

## 常用命令

```bash
# 编译
mvn clean compile

# 运行应用
mvn spring-boot:run

# 打包（跳过测试）
mvn package -DskipTests

# 运行测试
mvn test

# 启动 Docker 服务
docker-compose up -d

# 查看服务状态
docker-compose ps
```

---

## 端点访问

- **应用**: http://localhost:8080
- **健康检查**: http://localhost:8080/actuator/health
- **Milvus Attu**: http://localhost:8000

---

## 注意事项

1. **会话持久性**：Agent Teams 仅存在于当前会话，关闭后需重新启动
2. **代码持久性**：Git 提交的代码会持久化，可跨会话恢复
3. **状态恢复**：通过 PROGRESS.md 恢复任务状态
4. **人类优先**：人类随时可以暂停、修改需求
5. **小步迭代**：每个子任务独立验证、提交

---

**维护人**: TaskArchitect  
**最后更新**: 2026-11-09 18:20
