# 如何使用自动化开发Skill进行迭代

## 📚 目录

1. [第一次使用](#第一次使用)
2. [日常开发流程](#日常开发流程)
3. [新对话恢复进度](#新对话恢复进度)
4. [如何迭代到v2.0](#如何迭代到v20)
5. [常见场景](#常见场景)

---

## 🚀 第一次使用

### 步骤1：初始化（已完成✅）

你已经完成了初始化：
```bash
node .claude/skills/dev-rag-server/dev-driver.mjs init
```

生成的文件：
- ✅ `.claude/skills/dev-rag-server/task-tracker.json` - 任务追踪
- ✅ `.claude/skills/dev-rag-server/progress.md` - 进度报告

### 步骤2：开始第一个任务

在Claude对话中直接说：
```
"开始开发任务A1"
```

或运行命令：
```bash
node .claude/skills/dev-rag-server/dev-driver.mjs start A1
```

### 步骤3：Agent生成代码

Agent会：
1. 从DEV_SPEC.md读取任务A1的详细说明
2. 生成代码（pom.xml、项目结构、主类）
3. 添加完善的中文注释
4. 展示生成的文件

### 步骤4：审核代码

**检查清单**：
- [ ] 所有类有完整的中文JavaDoc吗？
- [ ] 注释格式是否规范？
- [ ] 代码结构符合DEV_SPEC.md吗？
- [ ] 能否编译通过（`mvn compile`）？

**如果需要修改**，直接告诉Agent：
```
"pom.xml中缺少lombok依赖，请添加"
```

### 步骤5：标记任务完成

**方式1：在对话中说**
```
"任务A1已完成，生成的文件有：
- pom.xml
- src/main/java/com/ragserver/RagServerApplication.java
- src/main/resources/application.yaml"
```

**方式2：运行命令**
```bash
node .claude/skills/dev-rag-server/dev-driver.mjs complete A1 \
  --files "pom.xml,src/main/java/com/ragserver/RagServerApplication.java,src/main/resources/application.yaml" \
  --notes "编译通过，项目初始化完成"
```

### 步骤6：查看进度

```bash
# 查看摘要
node .claude/skills/dev-rag-server/dev-driver.mjs status

# 查看详细报告
cat .claude/skills/dev-rag-server/progress.md
```

### 步骤7：继续下一个任务

重复步骤2-6

---

## 🔄 日常开发流程

### 典型工作流

```
早上：
  1. 打开Claude，说"查看RAG-SERVER开发进度"
  2. Agent显示：当前在任务A3，已完成A1、A2
  3. 说"继续开发任务A3"

中间：
  4. Agent生成代码 → 你审核 → 修改 → 标记完成
  5. 重复：A4 → A5 → ...

下午：
  6. 完成3-5个任务
  7. 说"备份当前进度"
  8. 提交Git
```

### 关键命令

| 场景 | 命令/对话 |
|-----|---------|
| **查看进度** | `node dev-driver.mjs status` 或"查看进度" |
| **开始任务** | `node dev-driver.mjs start A2` 或"开始任务A2" |
| **完成任务** | "任务A2已完成，生成的文件有..." |
| **跳过任务** | `node dev-driver.mjs skip B4 --reason "暂不实现"` |
| **阻塞任务** | `node dev-driver.mjs block A2 --reason "等待API密钥"` |
| **备份进度** | `node dev-driver.mjs backup` |

---

## 🆕 新对话恢复进度

### 场景：昨天完成了5个任务，今天打开新对话

**步骤1：询问进度**
```
你："查看RAG-SERVER的开发进度"
```

**步骤2：Agent自动读取**

Agent会：
1. 运行 `node dev-driver.mjs status`
2. 读取 `task-tracker.json`
3. 显示当前状态：
```
已完成: 5个任务（A1-A3, B1-B2）
进行中: 无
下一个: B3 - API限流与重试
```

**步骤3：继续开发**
```
你："继续开发任务B3"
```

**无缝恢复**：
- ✅ Agent知道已完成哪些任务
- ✅ Agent可以查看已完成任务的代码风格
- ✅ Agent知道下一个应该做什么

---

## 🚀 如何迭代到v2.0（半自动版）

### 当前：v1.0（手动审核版）

**特点**：每个任务都需要你手动审核和标记完成

**适用期**：前10-15个任务，建立信任

---

### 升级时机：何时升级到v2.0？

**检查清单**：
- [ ] 已完成10+个任务
- [ ] 代码风格一致性达到90%+
- [ ] 注释规范完全符合要求
- [ ] 连续5个任务零错误
- [ ] 你完全信任Agent的判断

**评估方法**：
```bash
# 统计代码质量
cd src/main/java
grep -r "^/\*\*" . | wc -l  # 统计JavaDoc注释数量
grep -r "TODO" . | wc -l    # 统计未完成项
```

---

### 升级步骤

#### 1. 备份当前进度
```bash
node dev-driver.mjs backup
```

#### 2. 创建v2.0版本的driver

在对话中说：
```
"我想升级到v2.0半自动版本，帮我创建一个增强的dev-driver-v2.mjs"
```

#### 3. v2.0新功能

**Agent会创建**：
```javascript
// dev-driver-v2.mjs（半自动版）

async function autoDevWithReview() {
  while (true) {
    const nextTask = tracker.getNextTask();
    if (!nextTask) break;
    
    console.log(`\n🚀 自动开始任务：${nextTask.id} - ${nextTask.name}`);
    
    // 1. 自动生成代码（调用Claude API）
    const result = await generateCode(nextTask);
    
    // 2. 自动运行测试
    const testResult = await runTests(nextTask);
    
    // 3. 展示结果
    console.log('\n📝 生成的文件：');
    result.files.forEach(f => console.log(`  - ${f}`));
    console.log(`\n✅ 测试: ${testResult.passed ? '通过' : '失败'}`);
    
    // 4. 等待人工审核（关键点）
    const approval = await askUser(
      `\n任务 ${nextTask.id} 已完成，请审核：\n` +
      `  [a] 批准并继续\n` +
      `  [m] 需要修改\n` +
      `  [s] 跳过\n` +
      `  [q] 退出\n`
    );
    
    if (approval === 'a') {
      tracker.completeTask(nextTask.id, result.files);
      
      // 询问是否继续
      const shouldContinue = await askUser('继续下一个？(y/n): ');
      if (shouldContinue !== 'y') break;
    } else if (approval === 'm') {
      await modifyTask(nextTask, result);
    } else {
      break;
    }
  }
}
```

#### 4. 测试v2.0

在少量任务上测试：
```bash
# 使用v2.0完成3个任务
node dev-driver-v2.mjs auto --limit 3
```

检查质量，如果满意：
```bash
# 替换为默认driver
mv dev-driver.mjs dev-driver-v1.mjs.backup
mv dev-driver-v2.mjs dev-driver.mjs
```

---

### v2.0的使用方式

#### 批量审核模式
```bash
# 让Agent自动完成3个任务，然后批量审核
node dev-driver.mjs auto --batch 3
```

**流程**：
1. Agent自动生成3个任务的代码
2. 自动运行测试
3. 展示所有结果
4. 你一次性审核3个任务
5. 批准或标记需要修改的

#### 连续模式
```bash
# Agent持续开发，每个任务后暂停等待你确认
node dev-driver.mjs auto --continuous
```

**适用**：你在电脑旁边，可以快速审核

---

## 📖 常见场景

### 场景1：任务卡住了，想跳过

```bash
# 跳过B4（Vision LLM，可选功能）
node dev-driver.mjs skip B4 --reason "暂不实现Vision功能"

# 继续下一个
node dev-driver.mjs next
```

### 场景2：等待外部条件（API密钥）

```bash
# 标记为阻塞
node dev-driver.mjs block B1 --reason "等待DashScope API密钥"

# 跳到其他可做的任务
node dev-driver.mjs start C1
```

解决后恢复：
```bash
# 重置任务状态
node dev-driver.mjs reset B1

# 继续
node dev-driver.mjs start B1
```

### 场景3：发现之前任务有bug

```bash
# 重置任务
node dev-driver.mjs reset A2

# 重新开始
node dev-driver.mjs start A2
```

### 场景4：想查看某个任务的详细说明

在对话中说：
```
"显示任务C3的详细说明"
```

Agent会从DEV_SPEC.md中提取该任务的完整内容。

### 场景5：批量提交Git

完成一个阶段后：
```bash
# 阶段A的3个任务都完成了
git add .
git commit -m "feat: 完成阶段A - Spring Boot初始化

- A1: Maven项目初始化
- A2: 配置管理体系
- A3: 数据库初始化

已完成任务: 3/53

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"

git push origin main
```

### 场景6：进度数据丢失了

```bash
# 恢复到最近的备份
ls .claude/skills/dev-rag-server/backups/
node dev-driver.mjs restore .claude/skills/dev-rag-server/backups/backup-2025-01-03-18-30.json
```

---

## 📊 进度追踪技巧

### 每日结束时

```bash
# 1. 备份进度
node dev-driver.mjs backup

# 2. 查看统计
node dev-driver.mjs status

# 3. 提交Git
git add .
git commit -m "progress: 完成X个任务 (Y/53)"
git push
```

### 每周回顾

```bash
# 查看详细报告
cat .claude/skills/dev-rag-server/progress.md

# 统计本周完成的任务数
git log --since="1 week ago" --oneline | grep "feat:" | wc -l
```

---

## 🎓 最佳实践

### 1. 小步快跑
- 不要一次完成太多任务不提交
- 每完成1-3个任务就提交Git
- 出错容易回滚

### 2. 定期备份
```bash
# 每完成一个阶段就备份
node dev-driver.mjs backup
```

### 3. 代码审查
每个任务完成后检查：
- [ ] 注释完善且中文
- [ ] 编译通过
- [ ] 测试通过（如果有）
- [ ] 符合DEV_SPEC.md

### 4. 记录问题
遇到问题时在notes中记录：
```bash
node dev-driver.mjs complete C3 \
  --files "..." \
  --notes "BM25实现遇到性能问题，后续需要优化"
```

### 5. 利用模板
生成代码时告诉Agent：
```
"使用templates/service.java.template生成RagService"
```

---

## ❓ 常见问题

### Q: 如何在新电脑上恢复开发？

A: 进度文件已经在Git中，克隆代码后直接查看：
```bash
git clone <repo>
cd RAG-SERVER
node .claude/skills/dev-rag-server/dev-driver.mjs status
```

### Q: 可以多人协作吗？

A: 可以，但需要注意：
- 每人负责不同的阶段（避免冲突）
- 定期同步progress（git pull）
- 使用不同的分支开发

### Q: 任务顺序可以调整吗？

A: P0任务建议按顺序（有依赖），P1任务可以灵活调整：
```bash
# 跳过P1任务，继续P0任务
node dev-driver.mjs skip B4
node dev-driver.mjs next  # 自动找到下一个P0任务
```

### Q: 如何评估开发效率？

A: 查看统计信息（未来v2.0会提供）：
```bash
node dev-driver.mjs stats

# 输出示例:
# 平均每任务: 1.5小时
# 已用时间: 12小时
# 预计完成: 2周
```

---

## 🎯 总结

### 当前阶段（v1.0）
- ✅ 手动控制每个任务
- ✅ 建立信任
- ✅ 学习代码风格
- ⏱️ 预计完成时间：3-4周（按每天2-3个任务）

### 未来升级（v2.0）
- 🚀 半自动化
- 🚀 批量审核
- 🚀 效率提升3-5倍
- ⏱️ 预计完成时间：1-2周

### 最终目标（v3.0）
- 🤖 全自动化
- 🤖 自主修复错误
- 🤖 24小时持续开发

---

**开始你的第一个任务**：
```
"开始开发任务A1"
```

祝开发顺利！🎉
