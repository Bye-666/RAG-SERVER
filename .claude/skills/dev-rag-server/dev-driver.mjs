#!/usr/bin/env node

/**
 * RAG-SERVER 开发驱动器
 *
 * 功能：
 * - 初始化任务追踪
 * - 管理任务状态
 * - 生成进度报告
 * - 备份与恢复
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 项目根目录
const PROJECT_ROOT = path.resolve(__dirname, '../../..');
const TRACKER_FILE = path.join(__dirname, 'task-tracker.json');
const PROGRESS_FILE = path.join(__dirname, 'progress.md');
const BACKUP_DIR = path.join(__dirname, 'backups');

/**
 * 任务追踪器类
 */
class TaskTracker {
  constructor() {
    this.data = null;
  }

  /**
   * 加载进度文件
   */
  load() {
    if (fs.existsSync(TRACKER_FILE)) {
      this.data = JSON.parse(fs.readFileSync(TRACKER_FILE, 'utf8'));
      console.log('✅ 已加载进度文件');
    } else {
      console.log('⚠️  进度文件不存在，请先运行 init 命令');
      return false;
    }
    return true;
  }

  /**
   * 保存进度文件
   */
  save() {
    this.data.lastUpdated = new Date().toISOString().replace('T', ' ').substring(0, 19);
    fs.writeFileSync(TRACKER_FILE, JSON.stringify(this.data, null, 2), 'utf8');
    console.log('💾 进度已保存');
  }

  /**
   * 初始化任务追踪（从DEV_SPEC.md提取53个任务）
   */
  initialize() {
    console.log('🚀 初始化任务追踪器...');

    // 定义53个任务（基于DEV_SPEC.md）
    const tasks = {
      // 阶段A: Spring Boot初始化 (3个任务)
      'A1': { name: 'Maven项目初始化', phase: 'A', priority: 'P0' },
      'A2': { name: '配置管理体系', phase: 'A', priority: 'P0' },
      'A3': { name: '数据库初始化', phase: 'A', priority: 'P0' },

      // 阶段B: DashScope集成 (4个任务)
      'B1': { name: 'DashScopeChatClient实现', phase: 'B', priority: 'P0' },
      'B2': { name: 'DashScopeEmbeddingClient实现', phase: 'B', priority: 'P0' },
      'B3': { name: 'API限流与重试', phase: 'B', priority: 'P0' },
      'B4': { name: 'DashScope Vision LLM', phase: 'B', priority: 'P1' },

      // 阶段C: Milvus集成 (5个任务)
      'C1': { name: 'Milvus连接与Schema创建', phase: 'C', priority: 'P0' },
      'C2': { name: 'Dense检索实现', phase: 'C', priority: 'P0' },
      'C3': { name: 'Sparse检索实现(BM25)', phase: 'C', priority: 'P0' },
      'C4': { name: 'RRF融合实现', phase: 'C', priority: 'P0' },
      'C5': { name: '批量Upsert方法', phase: 'C', priority: 'P0' },

      // 阶段D: 文档摄取Pipeline (8个任务)
      'D1': { name: 'PDF加载', phase: 'D', priority: 'P0' },
      'D2': { name: 'RecursiveSplitter实现', phase: 'D', priority: 'P0' },
      'D3': { name: 'ChunkRefiner实现', phase: 'D', priority: 'P0' },
      'D4': { name: 'MetadataEnricher实现', phase: 'D', priority: 'P0' },
      'D5': { name: '批量Embedding编码', phase: 'D', priority: 'P0' },
      'D6': { name: 'Pipeline编排', phase: 'D', priority: 'P0' },
      'D7': { name: 'ImageCaptioner实现', phase: 'D', priority: 'P1' },
      'D8': { name: '增量摄取与去重', phase: 'D', priority: 'P0' },

      // 阶段E: RAG检索与生成 (7个任务)
      'E1': { name: 'HybridRetriever实现', phase: 'E', priority: 'P0' },
      'E2': { name: 'Reranker实现', phase: 'E', priority: 'P0' },
      'E3': { name: 'Prompt模板管理', phase: 'E', priority: 'P0' },
      'E4': { name: 'RagService核心实现', phase: 'E', priority: 'P0' },
      'E5': { name: 'Citation生成', phase: 'E', priority: 'P0' },
      'E6': { name: '流式输出支持', phase: 'E', priority: 'P1' },
      'E7': { name: '缓存策略', phase: 'E', priority: 'P1' },

      // 阶段F: MCP协议集成 (5个任务)
      'F1': { name: 'MCP Server初始化', phase: 'F', priority: 'P0' },
      'F2': { name: 'query_knowledge_hub工具', phase: 'F', priority: 'P0' },
      'F3': { name: 'list_collections工具', phase: 'F', priority: 'P0' },
      'F4': { name: 'get_document_summary工具', phase: 'F', priority: 'P0' },
      'F5': { name: 'MCP异常处理', phase: 'F', priority: 'P1' },

      // 阶段G: 元数据与管理 (6个任务)
      'G1': { name: 'DocumentService实现', phase: 'G', priority: 'P0' },
      'G2': { name: 'ImageStorage实现', phase: 'G', priority: 'P0' },
      'G3': { name: 'IngestionHistoryRepository', phase: 'G', priority: 'P0' },
      'G4': { name: '批量删除功能', phase: 'G', priority: 'P0' },
      'G5': { name: '文档统计API', phase: 'G', priority: 'P0' },
      'G6': { name: '搜索历史记录', phase: 'G', priority: 'P1' },

      // 阶段H: Dashboard (6个任务)
      'H1': { name: 'REST API设计', phase: 'H', priority: 'P0' },
      'H2': { name: '前端页面骨架', phase: 'H', priority: 'P0' },
      'H3': { name: '文档浏览器', phase: 'H', priority: 'P0' },
      'H4': { name: 'Ingestion管理', phase: 'H', priority: 'P0' },
      'H5': { name: 'Query测试页面', phase: 'H', priority: 'P1' },
      'H6': { name: '系统总览页面', phase: 'H', priority: 'P1' },

      // 阶段I: 评估与优化 (5个任务)
      'I1': { name: '黄金测试集构建', phase: 'I', priority: 'P0' },
      'I2': { name: '评估指标实现', phase: 'I', priority: 'P0' },
      'I3': { name: 'EvaluationService实现', phase: 'I', priority: 'P0' },
      'I4': { name: '回归测试基线', phase: 'I', priority: 'P0' },
      'I5': { name: '性能优化', phase: 'I', priority: 'P1' },

      // 阶段J: 端到端验收 (4个任务)
      'J1': { name: '完整摄取测试', phase: 'J', priority: 'P0' },
      'J2': { name: '完整RAG查询测试', phase: 'J', priority: 'P0' },
      'J3': { name: 'MCP兼容性测试', phase: 'J', priority: 'P0' },
      'J4': { name: '文档与示例', phase: 'J', priority: 'P1' },
    };

    // 初始化每个任务的状态
    const initializedTasks = {};
    for (const [id, task] of Object.entries(tasks)) {
      initializedTasks[id] = {
        ...task,
        status: 'pending',
        startedAt: null,
        completedAt: null,
        files: [],
        notes: ''
      };
    }

    // 创建初始数据结构
    this.data = {
      version: '1.0.0',
      lastUpdated: new Date().toISOString().replace('T', ' ').substring(0, 19),
      currentPhase: 'A',
      currentTask: null,
      tasks: initializedTasks,
      statistics: {
        total: 53,
        completed: 0,
        inProgress: 0,
        pending: 53,
        blocked: 0,
        skipped: 0
      }
    };

    this.save();
    this.generateProgressReport();

    console.log('✅ 初始化完成！共53个任务');
    console.log('📝 进度文件: ' + TRACKER_FILE);
    console.log('📊 报告文件: ' + PROGRESS_FILE);
  }

  /**
   * 更新统计信息
   */
  updateStatistics() {
    const stats = {
      total: 53,
      completed: 0,
      inProgress: 0,
      pending: 0,
      blocked: 0,
      skipped: 0
    };

    for (const task of Object.values(this.data.tasks)) {
      stats[task.status]++;
    }

    this.data.statistics = stats;
  }

  /**
   * 获取下一个待开始的任务
   */
  getNextTask() {
    const phases = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'];

    for (const phase of phases) {
      const phaseTasks = Object.entries(this.data.tasks)
        .filter(([id, _]) => id.startsWith(phase))
        .sort(([a], [b]) => a.localeCompare(b));

      for (const [id, task] of phaseTasks) {
        if (task.status === 'pending' && task.priority === 'P0') {
          return { id, ...task };
        }
      }
    }

    // 如果所有P0任务完成，找P1任务
    for (const phase of phases) {
      const phaseTasks = Object.entries(this.data.tasks)
        .filter(([id, _]) => id.startsWith(phase))
        .sort(([a], [b]) => a.localeCompare(b));

      for (const [id, task] of phaseTasks) {
        if (task.status === 'pending' && task.priority === 'P1') {
          return { id, ...task };
        }
      }
    }

    return null;
  }

  /**
   * 开始任务
   */
  startTask(taskId) {
    if (!this.data.tasks[taskId]) {
      console.error(`❌ 任务 ${taskId} 不存在`);
      return false;
    }

    const task = this.data.tasks[taskId];

    if (task.status !== 'pending' && task.status !== 'blocked') {
      console.error(`❌ 任务 ${taskId} 当前状态为 ${task.status}，无法开始`);
      return false;
    }

    task.status = 'in-progress';
    task.startedAt = new Date().toISOString().replace('T', ' ').substring(0, 19);
    this.data.currentTask = taskId;
    this.data.currentPhase = task.phase;

    this.updateStatistics();
    this.save();
    this.generateProgressReport();

    console.log(`🚀 任务 ${taskId} 已开始: ${task.name}`);
    return true;
  }

  /**
   * 完成任务
   */
  completeTask(taskId, files = [], notes = '') {
    if (!this.data.tasks[taskId]) {
      console.error(`❌ 任务 ${taskId} 不存在`);
      return false;
    }

    const task = this.data.tasks[taskId];
    task.status = 'completed';
    task.completedAt = new Date().toISOString().replace('T', ' ').substring(0, 19);
    task.files = files;
    if (notes) task.notes = notes;

    this.updateStatistics();
    this.save();
    this.generateProgressReport();

    console.log(`✅ 任务 ${taskId} 已完成: ${task.name}`);
    return true;
  }

  /**
   * 跳过任务
   */
  skipTask(taskId, reason = '') {
    if (!this.data.tasks[taskId]) {
      console.error(`❌ 任务 ${taskId} 不存在`);
      return false;
    }

    const task = this.data.tasks[taskId];
    task.status = 'skipped';
    task.notes = reason || '已跳过';

    this.updateStatistics();
    this.save();
    this.generateProgressReport();

    console.log(`⏭️  任务 ${taskId} 已跳过`);
    return true;
  }

  /**
   * 阻塞任务
   */
  blockTask(taskId, reason) {
    if (!this.data.tasks[taskId]) {
      console.error(`❌ 任务 ${taskId} 不存在`);
      return false;
    }

    const task = this.data.tasks[taskId];
    task.status = 'blocked';
    task.notes = reason;

    this.updateStatistics();
    this.save();
    this.generateProgressReport();

    console.log(`🚫 任务 ${taskId} 已标记为阻塞: ${reason}`);
    return true;
  }

  /**
   * 重置任务
   */
  resetTask(taskId) {
    if (!this.data.tasks[taskId]) {
      console.error(`❌ 任务 ${taskId} 不存在`);
      return false;
    }

    const task = this.data.tasks[taskId];
    task.status = 'pending';
    task.startedAt = null;
    task.completedAt = null;
    task.files = [];
    task.notes = '';

    this.updateStatistics();
    this.save();
    this.generateProgressReport();

    console.log(`🔄 任务 ${taskId} 已重置`);
    return true;
  }

  /**
   * 检查进度同步状态
   */
  checkSync(options = {}) {
    console.log('🔍 检查进度同步状态...\n');

    try {
      // 获取最近10次提交
      const gitLog = execSync('git log --oneline -10', {
        cwd: PROJECT_ROOT,
        encoding: 'utf8'
      });

      // 提取任务ID（匹配格式：feat: 完成任务X1、fix: 修复XX等）
      const taskIdPattern = /(?:完成任务|任务)([A-J]\d)/g;
      const completedInGit = new Set();

      let match;
      while ((match = taskIdPattern.exec(gitLog)) !== null) {
        completedInGit.add(match[1]);
      }

      // 获取 tracker 中已完成的任务
      const completedInTracker = new Set();
      for (const [id, task] of Object.entries(this.data.tasks)) {
        if (task.status === 'completed') {
          completedInTracker.add(id);
        }
      }

      // 找出在Git中已提交但tracker中未标记完成的任务
      const missing = Array.from(completedInGit).filter(id => !completedInTracker.has(id));

      if (missing.length > 0) {
        console.log('⚠️  发现不同步问题：\n');
        missing.forEach(id => {
          const task = this.data.tasks[id];
          if (task) {
            console.log(`   ❌ 任务 ${id} (${task.name}): 代码已提交，但进度未更新`);
          } else {
            console.log(`   ❌ 任务 ${id}: 在Git提交中找到，但不在任务列表中`);
          }
        });

        if (options.autoFix) {
          console.log('\n🔧 自动修复中...\n');
          let fixed = 0;
          missing.forEach(id => {
            const task = this.data.tasks[id];
            if (task && task.status !== 'completed') {
              task.status = 'completed';
              task.completedAt = new Date().toISOString().replace('T', ' ').substring(0, 19);
              task.notes = task.notes || '自动修复：从Git历史恢复';
              console.log(`   ✅ 已修复任务 ${id}`);
              fixed++;
            }
          });

          if (fixed > 0) {
            this.updateStatistics();
            this.save();
            this.generateProgressReport();
            console.log(`\n✅ 修复完成！共修复 ${fixed} 个任务\n`);
          }
        } else {
          console.log('\n💡 修复建议：');
          console.log('   方法1（推荐）：自动修复');
          console.log('   node dev-driver.mjs check --auto-fix\n');
          console.log('   方法2：手动标记');
          missing.forEach(id => {
            console.log(`   node dev-driver.mjs complete ${id}`);
          });
          console.log('');
        }

        return false;
      } else {
        console.log('✅ 进度同步正常！');
        console.log(`   - Git提交中的任务: ${completedInGit.size} 个`);
        console.log(`   - Tracker中已完成: ${completedInTracker.size} 个`);
        console.log(`   - 状态一致 ✓\n`);
        return true;
      }
    } catch (error) {
      console.error('❌ 检查失败:', error.message);
      console.log('提示: 请确保在Git仓库中运行此命令\n');
      return false;
    }
  }

  /**
   * 显示状态
   */
  showStatus() {
    console.log('\n📊 RAG-SERVER 开发进度\n');

    const stats = this.data.statistics;
    const progress = Math.round((stats.completed / stats.total) * 100);

    console.log('总体统计:');
    console.log(`  总任务: ${stats.total}`);
    console.log(`  已完成: ${stats.completed} (${progress}%)`);
    console.log(`  进行中: ${stats.inProgress}`);
    console.log(`  待开始: ${stats.pending}`);
    console.log(`  已阻塞: ${stats.blocked}`);
    console.log(`  已跳过: ${stats.skipped}`);

    console.log('\n当前状态:');
    console.log(`  当前阶段: ${this.data.currentPhase}`);
    console.log(`  当前任务: ${this.data.currentTask || '无'}`);
    console.log(`  最后更新: ${this.data.lastUpdated}`);

    const nextTask = this.getNextTask();
    if (nextTask) {
      console.log(`\n下一个待开始任务: ${nextTask.id} - ${nextTask.name}`);
    } else {
      console.log('\n🎉 所有任务已完成！');
    }
  }

  /**
   * 生成进度报告（Markdown）
   */
  generateProgressReport() {
    const stats = this.data.statistics;
    const progress = Math.round((stats.completed / stats.total) * 100);
    const barLength = 50;
    const filled = Math.round((progress / 100) * barLength);
    const progressBar = '█'.repeat(filled) + '░'.repeat(barLength - filled);

    let report = `# RAG-SERVER 开发进度报告

生成时间: ${this.data.lastUpdated}

## 📊 总体进度

[${progressBar}] ${progress}%

- **总任务数**: ${stats.total}
- **已完成**: ${stats.completed}
- **进行中**: ${stats.inProgress}
- **待开始**: ${stats.pending}
- **已阻塞**: ${stats.blocked}
- **已跳过**: ${stats.skipped}

## 🎯 当前状态

- **当前阶段**: 阶段${this.data.currentPhase}
- **当前任务**: ${this.data.currentTask || '无'}
- **最后更新**: ${this.data.lastUpdated}

`;

    // 已完成任务
    const completedTasks = Object.entries(this.data.tasks)
      .filter(([_, task]) => task.status === 'completed')
      .sort(([a], [b]) => a.localeCompare(b));

    if (completedTasks.length > 0) {
      report += `## ✅ 已完成任务 (${completedTasks.length}/${stats.total})\n\n`;

      const phases = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'];
      for (const phase of phases) {
        const phaseTasks = completedTasks.filter(([id]) => id.startsWith(phase));
        if (phaseTasks.length === 0) continue;

        report += `### 阶段${phase}\n\n`;
        for (const [id, task] of phaseTasks) {
          report += `- ✅ **${id}** - ${task.name} (${task.priority})\n`;
          report += `  - 完成时间: ${task.completedAt}\n`;
          if (task.files.length > 0) {
            report += `  - 生成文件: ${task.files.slice(0, 3).join(', ')}${task.files.length > 3 ? '...' : ''}\n`;
          }
          if (task.notes) {
            report += `  - 备注: ${task.notes}\n`;
          }
          report += '\n';
        }
      }
    }

    // 进行中任务
    const inProgressTasks = Object.entries(this.data.tasks)
      .filter(([_, task]) => task.status === 'in-progress');

    if (inProgressTasks.length > 0) {
      report += `## 🔄 进行中任务 (${inProgressTasks.length})\n\n`;
      for (const [id, task] of inProgressTasks) {
        report += `- 🔄 **${id}** - ${task.name} (${task.priority})\n`;
        report += `  - 开始时间: ${task.startedAt}\n\n`;
      }
    }

    // 阻塞任务
    const blockedTasks = Object.entries(this.data.tasks)
      .filter(([_, task]) => task.status === 'blocked');

    if (blockedTasks.length > 0) {
      report += `## 🚫 阻塞任务 (${blockedTasks.length})\n\n`;
      for (const [id, task] of blockedTasks) {
        report += `- 🚫 **${id}** - ${task.name} (${task.priority})\n`;
        report += `  - 原因: ${task.notes}\n\n`;
      }
    }

    // 待开始任务（只显示下一阶段）
    const nextPhase = this.data.currentPhase;
    const pendingTasks = Object.entries(this.data.tasks)
      .filter(([id, task]) => task.status === 'pending' && id.startsWith(nextPhase))
      .sort(([a], [b]) => a.localeCompare(b));

    if (pendingTasks.length > 0) {
      report += `## 📋 阶段${nextPhase}待完成任务 (${pendingTasks.length})\n\n`;
      for (const [id, task] of pendingTasks) {
        report += `- ⏳ **${id}** - ${task.name} (${task.priority})\n`;
      }
      report += '\n';
    }

    // 阶段进度统计
    report += `## 📈 阶段进度\n\n`;
    const phases = [
      { id: 'A', name: 'Spring Boot初始化', total: 3 },
      { id: 'B', name: 'DashScope集成', total: 4 },
      { id: 'C', name: 'Milvus集成', total: 5 },
      { id: 'D', name: '文档摄取Pipeline', total: 8 },
      { id: 'E', name: 'RAG检索与生成', total: 7 },
      { id: 'F', name: 'MCP协议集成', total: 5 },
      { id: 'G', name: '元数据与管理', total: 6 },
      { id: 'H', name: 'Dashboard', total: 6 },
      { id: 'I', name: '评估与优化', total: 5 },
      { id: 'J', name: '端到端验收', total: 4 },
    ];

    for (const phase of phases) {
      const phaseTasks = Object.entries(this.data.tasks)
        .filter(([id]) => id.startsWith(phase.id));
      const completed = phaseTasks.filter(([_, task]) => task.status === 'completed').length;
      const phaseProgress = Math.round((completed / phase.total) * 100);
      const phaseBarLength = 10;
      const phaseFilled = Math.round((phaseProgress / 100) * phaseBarLength);
      const phaseBar = '█'.repeat(phaseFilled) + '░'.repeat(phaseBarLength - phaseFilled);

      report += `- **阶段${phase.id}** ${phase.name} (${phase.total}个任务): [${phaseBar}] ${phaseProgress}% (${completed}/${phase.total})\n`;
    }

    fs.writeFileSync(PROGRESS_FILE, report, 'utf8');
    console.log('📊 进度报告已生成: ' + PROGRESS_FILE);
  }

  /**
   * 备份进度
   */
  backup() {
    if (!fs.existsSync(BACKUP_DIR)) {
      fs.mkdirSync(BACKUP_DIR, { recursive: true });
    }

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19);
    const backupFile = path.join(BACKUP_DIR, `backup-${timestamp}.json`);

    fs.copyFileSync(TRACKER_FILE, backupFile);
    console.log(`💾 进度已备份: ${backupFile}`);
  }

  /**
   * 恢复进度
   */
  restore(backupFile) {
    if (!fs.existsSync(backupFile)) {
      console.error(`❌ 备份文件不存在: ${backupFile}`);
      return false;
    }

    fs.copyFileSync(backupFile, TRACKER_FILE);
    this.load();
    console.log(`✅ 进度已恢复: ${backupFile}`);
    return true;
  }
}

/**
 * 主函数
 */
function main() {
  const args = process.argv.slice(2);
  const command = args[0];

  const tracker = new TaskTracker();

  switch (command) {
    case 'init':
      tracker.initialize();
      break;

    case 'check':
      if (!tracker.load()) return;
      const autoFix = args.includes('--auto-fix');
      tracker.checkSync({ autoFix });
      break;

    case 'status':
      if (!tracker.load()) return;
      tracker.showStatus();
      break;

    case 'next':
      if (!tracker.load()) return;
      const nextTask = tracker.getNextTask();
      if (nextTask) {
        console.log(`\n下一个任务: ${nextTask.id} - ${nextTask.name}`);
        console.log(`优先级: ${nextTask.priority}`);
        console.log(`阶段: ${nextTask.phase}`);
        console.log(`\n运行以下命令开始任务:`);
        console.log(`  node dev-driver.mjs start ${nextTask.id}`);
      } else {
        console.log('🎉 所有任务已完成！');
      }
      break;

    case 'start':
      if (!tracker.load()) return;
      const taskId = args[1];
      if (!taskId) {
        console.error('❌ 请指定任务ID，例如: start A1');
        return;
      }
      tracker.startTask(taskId);
      break;

    case 'complete':
      if (!tracker.load()) return;
      const completeId = args[1];
      if (!completeId) {
        console.error('❌ 请指定任务ID');
        return;
      }

      // 解析文件列表
      const filesIndex = args.indexOf('--files');
      const files = filesIndex !== -1 && args[filesIndex + 1]
        ? args[filesIndex + 1].split(',').map(f => f.trim())
        : [];

      const notesIndex = args.indexOf('--notes');
      const notes = notesIndex !== -1 && args[notesIndex + 1]
        ? args[notesIndex + 1]
        : '';

      tracker.completeTask(completeId, files, notes);
      break;

    case 'skip':
      if (!tracker.load()) return;
      const skipId = args[1];
      if (!skipId) {
        console.error('❌ 请指定任务ID');
        return;
      }

      const skipReasonIndex = args.indexOf('--reason');
      const skipReason = skipReasonIndex !== -1 && args[skipReasonIndex + 1]
        ? args[skipReasonIndex + 1]
        : '';

      tracker.skipTask(skipId, skipReason);
      break;

    case 'block':
      if (!tracker.load()) return;
      const blockId = args[1];
      if (!blockId) {
        console.error('❌ 请指定任务ID');
        return;
      }

      const blockReasonIndex = args.indexOf('--reason');
      const blockReason = blockReasonIndex !== -1 && args[blockReasonIndex + 1]
        ? args[blockReasonIndex + 1]
        : '已阻塞';

      tracker.blockTask(blockId, blockReason);
      break;

    case 'reset':
      if (!tracker.load()) return;
      const resetId = args[1];
      if (!resetId) {
        console.error('❌ 请指定任务ID');
        return;
      }
      tracker.resetTask(resetId);
      break;

    case 'backup':
      if (!tracker.load()) return;
      tracker.backup();
      break;

    case 'restore':
      const restoreFile = args[1];
      if (!restoreFile) {
        console.error('❌ 请指定备份文件路径');
        return;
      }
      tracker.restore(restoreFile);
      break;

    case 'help':
    default:
      console.log(`
RAG-SERVER 开发驱动器

用法: node dev-driver.mjs <command> [options]

命令:
  init                     初始化任务追踪（首次使用）
  check                    检查进度同步状态
    --auto-fix               自动修复不同步问题
  status                   显示当前进度
  next                     显示下一个待开始的任务
  start <taskId>           开始指定任务
  complete <taskId>        完成任务
    --files <file1,file2>    生成的文件列表（逗号分隔）
    --notes <text>           备注信息
  skip <taskId>            跳过任务
    --reason <text>          跳过原因
  block <taskId>           标记任务为阻塞
    --reason <text>          阻塞原因
  reset <taskId>           重置任务状态
  backup                   备份当前进度
  restore <file>           恢复到指定备份
  help                     显示帮助信息

示例:
  node dev-driver.mjs init
  node dev-driver.mjs check
  node dev-driver.mjs check --auto-fix
  node dev-driver.mjs status
  node dev-driver.mjs start A1
  node dev-driver.mjs complete A1 --files "pom.xml,src/..." --notes "编译通过"
  node dev-driver.mjs skip B4 --reason "暂不实现Vision功能"
  node dev-driver.mjs backup
      `);
      break;
  }
}

main();
