---
name: dev-rag-server
description: 自动化开发RAG-SERVER项目，v2.1改进版，原子化提交+自动检查+Bug修复流程
tags: [development, automation, spring-boot, rag, chinese]
version: 2.1.0
---

# RAG-SERVER 自动化开发Skill v2.1

## 📋 概述

本Skill用于自动化开发RAG-SERVER项目，基于DEV_SPEC.md的53个任务逐步实施。

**v2.1 核心改进** 🚀：
- ✨ **原子化提交**：进度更新和代码提交强制同步，一次完成
- ✨ **自动检查机制**：对话启动时自动检查进度同步
- ✨ **Bug修复流程**：明确区分正常开发和Bug修复，强制用户验证
- ✨ **半自动执行**：自动编译、测试、提交（需确认）
- ✨ **批量处理**：一次处理多个相关任务

**核心特性**：
- ✅ **强制同步**：进度更新在用户确认前自动完成，不可能遗忘
- ✅ **中文注释**：所有Java代码使用完善的中文JavaDoc
- ✅ **界面中文化**：API响应、异常消息、日志全部中文
- ✅ **进度追踪**：自动保存进度，新对话自动恢复
- ✅ **模板驱动**：统一的代码风格和注释规范

---

## 🎯 版本历史

### v2.1（原子化提交版）- 当前版本
**发布日期**：2026-10-06

**重大改进**：
- 🔥 **原子化提交流程**：先自动更新进度 → 显示完整摘要 → 用户确认 → 一次性提交
- 🔥 **自动检查机制**：新对话启动时自动检查同步状态，可选自动修复
- 🔥 **Bug修复流程**：明确区分两种模式，Bug修复必须等待用户验证
- 📊 新增 `check` 命令：检查进度同步
- 📊 新增 `check --auto-fix`：自动修复不同步问题

**解决的问题**：
- ❌ v2.0问题：容易忘记更新进度，导致代码和进度脱节
- ✅ v2.1方案：强制在用户确认前自动更新进度，不可能遗忘

### v2.0（半自动版）
**发布日期**：2026-10-04
- 自动编译、测试、提交
- 批量任务处理
- 智能恢复

### v1.0（手动审核版）
**发布日期**：2026-01-XX
- 基础任务管理和进度追踪

---

## 🚀 快速开始

### 对话启动时（自动执行）

Claude 会自动检查进度同步：
```bash
node .claude/skills/dev-rag-server/dev-driver.mjs check
```

如果发现不同步，会提示并可选自动修复。

### 正常任务开发流程

**步骤1：选择任务**
```
继续开发RAG-SERVER任务E6
```

**Claude 自动执行**：
1. ✅ 读取任务E6详细说明
2. ✅ 生成代码（包含完整JavaDoc、中文注释）
3. ✅ 自动编译：`mvn compile`
4. ✅ 自动测试：`mvn test`
5. ✅ **自动更新进度**：`node dev-driver.mjs complete E6`
6. ⏸️ **暂停确认**：显示完整摘要（代码+进度）
7. ✅ 你确认后一次性提交：`git commit`

**步骤2：用户确认**
Claude 显示：
```
✅ 任务 E6 完成！

代码变更：
  📄 新增文件：
    - StreamingService.java (156行)
    - StreamingServiceTest.java (测试通过 8/8)
  
  📝 测试结果：
    - 编译成功 ✓
    - 测试通过 (8/8) ✓

进度更新：
  📊 task-tracker.json: 40/53 → 41/53 (77%)
  📊 当前阶段：E (RAG检索与生成)
  📊 progress.md: 已自动生成

是否提交？请确认。
```

你说：`提交` 或 `确认提交`

**Claude 执行一次性提交**：
```bash
git add .
git commit -m "feat: 完成任务E6 - 流式输出支持

- 实现StreamingService流式响应功能
- 支持SSE (Server-Sent Events)
- 测试覆盖率100% (8/8通过)
- 更新任务进度至77%

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

**完成！**

---

## 🔧 Bug修复流程（特殊处理）

### 识别Bug修复场景

当用户说以下内容时，Claude 进入 **Bug修复模式**：
- "我发现XX问题"
- "XX功能有bug"
- "XX不工作"
- "需要修复XX"

### Bug修复流程（与正常开发不同）

**步骤1：Claude 修复代码**
1. 分析问题根因
2. 修复代码
3. 运行测试

**步骤2：⏸️ 第一次暂停 - 等待用户验证**

Claude 显示：
```
🔧 Bug 修复完成

修复内容：
  📄 文件：RagController.java
  ❌ 问题：上传接口未真正执行摄取流程
  ✅ 修复：添加 ingestionService.ingest() 调用
  
  📝 代码变更：
    - 第45行：添加文件保存逻辑
    - 第52行：调用 ingestionService.ingest()
    - 第58行：返回实际的chunk数量

测试结果：
  ✓ RagControllerTest: 7/7 通过
  ✓ IngestionServiceTest: 12/12 通过

⚠️  请验证修复是否符合预期
确认后说"提交修复"或"确认修复"
```

**步骤3：用户验证**（用户操作）
- 查看修复内容
- 可选：手动测试功能
- 确认修复效果

**步骤4：用户确认后，Claude 执行**
1. 更新进度（如果涉及任务）
2. 一次性提交（代码+进度）

### Bug修复 vs 正常开发对比

| 场景 | 暂停点 | 用户验证 | 提交时机 |
|-----|-------|---------|---------|
| 正常开发 | 测试通过后 | 看摘要即可 | 立即可提交 |
| Bug修复 | 测试通过后 | **必须验证修复效果** | 验证通过后提交 |

---

## ⚠️ CLAUDE 必须执行的流程（强制）

### 正常任务开发的提交流程

每完成一个任务后，**必须严格按以下顺序执行**：

#### 步骤1：自动更新进度（Claude执行）

**时机**：测试通过后立即执行，用户确认前完成

```bash
node .claude/skills/dev-rag-server/dev-driver.mjs complete [任务ID] \
  --files "生成的文件列表" \
  --notes "任务完成说明"
```

**示例**：
```bash
node .claude/skills/dev-rag-server/dev-driver.mjs complete E6 \
  --files "src/main/java/com/ragserver/service/StreamingService.java,src/test/java/com/ragserver/service/StreamingServiceTest.java" \
  --notes "流式输出支持实现完成。支持SSE，测试通过(8/8)。"
```

#### 步骤2：显示完整摘要（Claude执行）

显示内容包括：
- **代码变更**：新增/修改的文件列表、行数、测试结果
- **进度变更**：从 X% → Y%、任务状态变更
- **验证结果**：编译成功、测试通过数量

#### 步骤3：等待用户确认（暂停点）

⏸️ 等待用户说"提交"、"确认提交"、"commit"等

#### 步骤4：一次性提交（Claude执行）

```bash
git add .
git commit -m "feat: 完成任务[ID] - [任务名称]

[详细说明]
- 实现的功能点
- 测试覆盖情况
- 更新任务进度至X%

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### ❌ 错误示例（v2.0旧方式，已废弃）

```bash
# ❌ 错误：先提交代码
git commit -m "feat: 完成任务E6"

# ❌ 错误：再更新进度
node dev-driver.mjs complete E6

# ❌ 错误：再次提交进度
git commit -m "chore: 更新进度"
```

**问题**：
- 需要2次提交
- 容易忘记第2、3步
- 代码和进度可能脱节

### ✅ 正确示例（v2.1新方式）

```bash
# ✅ 正确：先更新进度
node dev-driver.mjs complete E6 --files "..." --notes "..."

# ✅ 正确：显示摘要，等待用户确认
# （显示代码变更 + 进度变更）

# ✅ 正确：用户确认后，一次性提交全部
git add .
git commit -m "feat: 完成任务E6 - 流式输出支持

- 实现StreamingService
- 测试通过 (8/8)
- 更新进度至77%

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

**优势**：
- ✅ 只需1次提交
- ✅ 进度更新在用户确认前自动完成
- ✅ 不可能遗忘更新进度
- ✅ 代码和进度强制同步

---

## 🔍 自动检查机制

### 对话启动时自动检查

Claude 在每次新对话启动时，会自动运行：

```bash
node .claude/skills/dev-rag-server/dev-driver.mjs check
```

**检查内容**：
- 比对最近Git提交中的任务ID
- 比对 task-tracker.json 中的完成状态
- 发现不一致自动提示

**示例输出**：

```
🔍 检查进度同步状态...

✅ 进度同步正常！
   - Git提交中的任务: 40 个
   - Tracker中已完成: 40 个
   - 状态一致 ✓
```

或发现问题时：

```
🔍 检查进度同步状态...

⚠️  发现不同步问题：

   ❌ 任务 E6 (流式输出支持): 代码已提交，但进度未更新
   ❌ 任务 E7 (缓存策略): 代码已提交，但进度未更新

💡 修复建议：
   方法1（推荐）：自动修复
   node dev-driver.mjs check --auto-fix

   方法2：手动标记
   node dev-driver.mjs complete E6
   node dev-driver.mjs complete E7
```

### 自动修复

如果发现不同步，可以自动修复：

```bash
node .claude/skills/dev-rag-server/dev-driver.mjs check --auto-fix
```

**效果**：
- 自动标记已提交但未更新的任务为 `completed`
- 更新 task-tracker.json
- 重新生成 progress.md
- 提交进度文件

---

## 📖 命令参考

### 基本命令

#### 1. 检查同步状态（v2.1新增）
```bash
# 检查进度同步
node dev-driver.mjs check

# 检查并自动修复
node dev-driver.mjs check --auto-fix
```

#### 2. 查看进度
```bash
node dev-driver.mjs status
```

输出：
```
📊 RAG-SERVER 开发进度

总体统计:
  总任务: 53
  已完成: 40 (75%)
  进行中: 0
  待开始: 6
  已阻塞: 4
  已跳过: 0

当前状态:
  当前阶段: J
  当前任务: J4
  最后更新: 2026-10-06 10:00:00

下一个待开始任务: E6 - 流式输出支持
```

#### 3. 开始任务
```bash
node dev-driver.mjs start E6
```

#### 4. 完成任务
```bash
node dev-driver.mjs complete E6 \
  --files "StreamingService.java,StreamingServiceTest.java" \
  --notes "流式输出支持实现完成"
```

#### 5. 跳过任务
```bash
node dev-driver.mjs skip B4 \
  --reason "暂不实现Vision功能"
```

#### 6. 标记阻塞
```bash
node dev-driver.mjs block F1 \
  --reason "MCP SDK依赖不可用"
```

#### 7. 备份进度
```bash
node dev-driver.mjs backup
```

#### 8. 恢复进度
```bash
node dev-driver.mjs restore backups/backup-2026-10-06.json
```

---

## 🎯 开发模式对比

### 正常开发模式（默认）

**触发条件**：
- "继续开发任务XX"
- "开始任务XX"
- "实现XX功能"

**流程**：
```
任务开始 → 代码生成 → 编译 → 测试 → 
自动更新进度 → 显示摘要 → 用户确认 → 一次性提交
```

**特点**：
- ✅ 高效快速
- ✅ 自动更新进度
- ✅ 用户看摘要即可确认

### Bug修复模式（特殊）

**触发条件**：
- "我发现XX问题"
- "XX有bug"
- "修复XX"

**流程**：
```
用户报告问题 → Claude分析 → 修复代码 → 测试 → 
⏸️ 等待用户验证 → 用户确认 → 更新进度 → 提交
```

**特点**：
- 🔍 强制用户验证
- ⏸️ 明确的暂停点
- ✅ 确保修复符合预期

---

## 📊 v2.1 vs v2.0 对比

| 特性 | v2.0 | v2.1 | 改进 |
|-----|------|------|------|
| 提交流程 | 3命令，2提交 | 1命令，1提交 | 简化67% |
| 进度更新 | 手动执行 | 自动强制 | 不可能遗忘 |
| 同步检查 | 无 | 自动检查 | 新增 |
| Bug修复 | 与正常开发混淆 | 明确分离 | 新增 |
| 用户确认 | 提交前 | 提交前（看到进度变更） | 更透明 |

### 进度同步问题发生率

- **v2.0**：约20%（容易忘记）
- **v2.1**：0%（强制执行）

---

## 🎓 最佳实践

### 对 Claude 的要求

1. **对话启动时检查同步**
   ```bash
   node dev-driver.mjs check
   ```
   发现问题立即提示用户

2. **任务完成后立即更新进度**
   - 测试通过后立即执行 `complete` 命令
   - 不要等到用户确认后才更新
   - 让用户看到进度变更

3. **一次性提交所有变更**
   - 代码 + 进度文件一起提交
   - 避免多次提交
   - 保持Git历史清晰

4. **Bug修复必须等待确认**
   - 用户主动报告的问题
   - 修复后必须暂停
   - 等待用户验证效果

### 对用户的建议

1. **明确区分场景**
   - 正常开发：说"继续任务XX"
   - Bug修复：说"我发现XX问题"

2. **验证后再确认**
   - 看清楚代码变更摘要
   - 确认进度更新正确
   - Bug修复要验证效果
   - 确认后说"提交"或"确认提交"

3. **定期检查进度**
   ```bash
   node dev-driver.mjs status
   ```
   确保进度准确

---

## 🔄 批量处理模式

### 批量完成阶段任务

```
批量完成阶段E的所有P1任务
```

Claude 会：
1. 识别阶段E的所有P1任务（E6, E7）
2. 逐个执行每个任务
3. 每个任务完成后自动更新进度
4. 显示批量完成摘要
5. 用户确认后统一提交

---

## 🚨 常见问题

### Q1: 如果忘记更新进度怎么办？

**A**: v2.1不会发生这个问题，因为进度更新是自动强制的。但如果真的发生了：

```bash
# 检查并自动修复
node dev-driver.mjs check --auto-fix
```

### Q2: 如何回滚到之前的进度？

**A**: 使用备份恢复：

```bash
# 先备份当前状态
node dev-driver.mjs backup

# 恢复到指定备份
node dev-driver.mjs restore backups/backup-2026-10-05.json
```

### Q3: Bug修复后不需要验证可以吗？

**A**: 不可以。Bug修复流程设计就是为了让用户验证修复效果。如果不需要验证，说明这不是Bug修复，而是正常功能改进。

### Q4: 能否跳过进度更新直接提交？

**A**: 不能。v2.1强制进度更新在提交前完成，这是设计的核心特性，确保代码和进度永远同步。

---

## 📚 相关文档

- [DEV_SPEC.md](../../../DEV_SPEC.md) - 完整的53个开发任务
- [task-tracker.json](task-tracker.json) - 任务进度追踪文件
- [progress.md](progress.md) - 进度报告（自动生成）
- [IMPROVEMENT_SUMMARY.md](../../../docs/IMPROVEMENT_SUMMARY.md) - v2.1改进总结

---

## 🎊 总结

### v2.1 核心优势

1. **原子化提交**：进度和代码强制同步，一次完成
2. **自动检查**：对话启动时自动检查，发现问题立即提示
3. **明确流程**：正常开发和Bug修复分离，不再混淆
4. **不可能遗忘**：进度更新是强制步骤，自动执行

### 适用场景

- ✅ 53个任务的渐进式开发
- ✅ 需要严格进度追踪的项目
- ✅ 多次对话中断和恢复
- ✅ 团队协作需要清晰的进度

### 不适用场景

- ❌ 一次性完成的小项目
- ❌ 不需要进度追踪的探索性开发
- ❌ 频繁回滚和重构的原型开发

---

**最后更新**: 2026-10-06  
**当前版本**: v2.1.0  
**维护者**: Claude Opus 5.5
