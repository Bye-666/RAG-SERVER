---
name: dev-rag-server
description: 自动化开发RAG-SERVER项目，按照DEV_SPEC.md逐步实施，支持进度追踪和恢复
tags: [development, automation, spring-boot, rag, chinese]
---

# RAG-SERVER 自动化开发Skill

## 📋 概述

本Skill用于自动化开发RAG-SERVER项目，基于DEV_SPEC.md的53个任务逐步实施。

**核心特性**：
- ✅ **渐进式开发**：手动控制每个任务，确保质量
- ✅ **中文注释**：所有Java代码使用完善的中文JavaDoc
- ✅ **界面中文化**：API响应、异常消息、日志全部中文
- ✅ **进度追踪**：自动保存进度，新对话自动恢复
- ✅ **模板驱动**：统一的代码风格和注释规范

## 🎯 当前版本

**版本**：v1.0（手动审核版）

工作流程：
1. 调用skill选择任务
2. Agent读取任务说明并生成代码
3. 你审核代码质量
4. 手动标记任务完成
5. 自动更新进度文件

## 🚀 快速开始

### 首次使用（初始化）

```bash
# 进入项目目录
cd d:/Dev/Workspace/RAG-SERVER

# 初始化进度追踪（创建task-tracker.json）
node .claude/skills/dev-rag-server/dev-driver.mjs init
```

### 查看当前进度

```bash
# 查看进度摘要
node .claude/skills/dev-rag-server/dev-driver.mjs status

# 查看详细进度报告
cat .claude/skills/dev-rag-server/progress.md
```

### 开始开发任务

```bash
# 方式1：自动选择下一个待开始的任务
node .claude/skills/dev-rag-server/dev-driver.mjs next

# 方式2：手动指定任务
node .claude/skills/dev-rag-server/dev-driver.mjs start A1

# 方式3：在Claude对话中直接说
# "开始任务A1" 或 "继续下一个任务"
```

### 标记任务完成

```bash
# 完成当前任务（需要提供生成的文件列表）
node .claude/skills/dev-rag-server/dev-driver.mjs complete A1 \
  --files "pom.xml,src/main/java/com/ragserver/RagServerApplication.java"

# 或在Claude对话中：
# "任务A1已完成，生成的文件是：pom.xml, ..."
```

### 任务控制

```bash
# 跳过任务（标记为skipped）
node .claude/skills/dev-rag-server/dev-driver.mjs skip A3 \
  --reason "暂时跳过，后续回来实现"

# 标记任务为阻塞
node .claude/skills/dev-rag-server/dev-driver.mjs block A2 \
  --reason "等待DashScope API密钥"

# 重新开始某个任务
node .claude/skills/dev-rag-server/dev-driver.mjs reset A1
```

## 📂 文件结构

```
.claude/skills/dev-rag-server/
├── SKILL.md                    # 本文件
├── dev-driver.mjs              # 开发驱动器（Node.js脚本）
├── task-tracker.json           # 任务进度追踪（自动生成）
├── progress.md                 # 人类可读的进度报告（自动生成）
└── templates/                  # 代码模板
    ├── controller.java.template    # Controller模板
    ├── service.java.template       # Service模板
    ├── repository.java.template    # Repository模板
    └── test.java.template          # 测试类模板
```

## 📝 代码规范

### Java注释规范

**所有Java代码必须包含完善的中文JavaDoc**：

#### 类注释

```java
/**
 * DashScope聊天客户端
 * 
 * <p>实现Spring AI的ChatClient接口，对接阿里云通义千问API。</p>
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>HTTP调用DashScope生成API</li>
 *   <li>支持流式输出（SSE）</li>
 *   <li>自动限流（令牌桶算法）</li>
 *   <li>失败重试（指数退避，最多3次）</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * ChatClient client = new DashScopeChatClient(properties);
 * ChatResponse response = client.call(new Prompt("你好"));
 * String answer = response.getResult().getOutput().getContent();
 * }</pre>
 * 
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 * @see org.springframework.ai.chat.ChatClient
 */
@Component
public class DashScopeChatClient implements ChatClient {
    // ...
}
```

#### 方法注释

```java
/**
 * 调用LLM生成回复（同步模式）
 * 
 * <p>该方法执行流程：</p>
 * <ol>
 *   <li>应用限流策略（等待令牌）</li>
 *   <li>构造HTTP请求（包含认证头）</li>
 *   <li>调用DashScope API</li>
 *   <li>解析响应为标准格式</li>
 *   <li>失败时自动重试（指数退避）</li>
 * </ol>
 * 
 * @param prompt 用户提示词（包含消息列表和参数）
 * @return 生成的回复（包含内容和元数据）
 * @throws RuntimeException 当API调用失败且重试耗尽时抛出
 */
@Override
public ChatResponse call(Prompt prompt) {
    // ...
}
```

#### 字段注释

```java
/**
 * DashScope配置属性（包含API密钥、模型名称等）
 */
private final DashScopeProperties properties;

/**
 * HTTP客户端，用于调用DashScope API
 */
private final RestTemplate restTemplate;

/**
 * 限流器，防止超过API的QPS限制（默认10 QPS）
 */
private final RateLimiter rateLimiter;
```

### 界面文字中文化

#### API响应消息

```java
@RestController
@RequestMapping("/api/query")
public class QueryController {
    
    @PostMapping
    public ResponseEntity<ApiResponse> query(@RequestBody QueryRequest request) {
        try {
            String answer = ragService.query(request.getQuery());
            return ResponseEntity.ok(ApiResponse.success("查询成功", answer));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(ApiResponse.error("查询失败：" + e.getMessage()));
        }
    }
}
```

#### 异常消息

```java
public class RagException extends RuntimeException {
    
    /**
     * 向量化失败异常
     */
    public static RagException embeddingFailed(String reason) {
        return new RagException("向量化失败：" + reason);
    }
    
    /**
     * Milvus连接失败异常
     */
    public static RagException milvusConnectionFailed() {
        return new RagException("Milvus连接失败，请检查服务是否启动");
    }
    
    /**
     * 文档加载失败异常
     */
    public static RagException documentLoadFailed(String path, String reason) {
        return new RagException(String.format("文档加载失败 [%s]：%s", path, reason));
    }
}
```

#### 日志输出

```java
@Slf4j
@Service
public class IngestionService {
    
    public void ingest(Path pdfPath, String collection) {
        log.info("开始摄取文档：{}, 目标集合：{}", pdfPath, collection);
        
        try {
            // 处理逻辑...
            log.info("文档摄取成功，生成 {} 个chunk", chunkCount);
        } catch (Exception e) {
            log.error("文档摄取失败：{}", e.getMessage(), e);
            throw RagException.ingestionFailed(pdfPath.toString(), e.getMessage());
        }
    }
}
```

## 🔄 工作流程详解

### 典型的任务开发流程

#### 1. 查看当前进度

```bash
node .claude/skills/dev-rag-server/dev-driver.mjs status
```

输出示例：
```
📊 RAG-SERVER 开发进度

总体统计:
  总任务: 53
  已完成: 2 (4%)
  进行中: 1
  待开始: 50
  已阻塞: 0

当前状态:
  当前阶段: A
  当前任务: A3 - 数据库初始化
  
下一个待开始任务: A3
```

#### 2. 开始下一个任务

在Claude对话中说：
```
"开始任务A3"
```

或运行命令：
```bash
node .claude/skills/dev-rag-server/dev-driver.mjs start A3
```

#### 3. Agent读取任务并生成代码

Agent会：
1. 从DEV_SPEC.md读取任务A3的详细说明
2. 查看已完成任务（A1、A2）的代码风格
3. 使用模板生成代码
4. 添加完善的中文注释
5. 展示生成的文件列表

#### 4. 你审核代码

检查项：
- ✅ 注释是否完善且中文
- ✅ 代码结构是否符合DEV_SPEC.md
- ✅ 界面文字是否中文化
- ✅ 是否有编译错误

如需修改，直接告诉Agent：
```
"DataSourceConfig.java中的注释不够详细，补充一下连接池配置的说明"
```

#### 5. 标记任务完成

满意后，在对话中说：
```
"任务A3已完成，生成的文件有：
- src/main/java/com/ragserver/config/DataSourceConfig.java
- src/main/resources/db/migration/V1__init_schema.sql
- src/test/java/com/ragserver/repository/IngestionHistoryRepositoryTest.java"
```

或运行命令：
```bash
node .claude/skills/dev-rag-server/dev-driver.mjs complete A3 \
  --files "src/main/java/com/ragserver/config/DataSourceConfig.java,..."
```

#### 6. 自动更新进度

- `task-tracker.json` 自动更新
- `progress.md` 自动重新生成
- Git自动提交（可选）

#### 7. 继续下一个任务

重复步骤2-6

## 📊 进度文件说明

### task-tracker.json

记录每个任务的详细状态：

```json
{
  "version": "1.0.0",
  "lastUpdated": "2025-01-XX 18:30:00",
  "currentPhase": "A",
  "currentTask": "A3",
  "tasks": {
    "A1": {
      "name": "Maven项目初始化",
      "phase": "A",
      "priority": "P0",
      "status": "completed",
      "startedAt": "2025-01-XX 10:00:00",
      "completedAt": "2025-01-XX 11:30:00",
      "files": [
        "pom.xml",
        ".gitignore",
        "src/main/java/com/ragserver/RagServerApplication.java"
      ],
      "notes": "初始化完成，编译通过"
    },
    "A2": {
      "name": "配置管理体系",
      "phase": "A",
      "priority": "P0",
      "status": "completed",
      "startedAt": "2025-01-XX 11:35:00",
      "completedAt": "2025-01-XX 13:00:00",
      "files": [
        "src/main/resources/application.yaml",
        "src/main/resources/application-dev.yaml",
        "src/main/resources/application-prod.yaml"
      ],
      "notes": "配置加载测试通过"
    },
    "A3": {
      "name": "数据库初始化",
      "phase": "A",
      "priority": "P0",
      "status": "in-progress",
      "startedAt": "2025-01-XX 14:00:00",
      "completedAt": null,
      "files": [],
      "notes": ""
    }
  },
  "statistics": {
    "total": 53,
    "completed": 2,
    "inProgress": 1,
    "pending": 50,
    "blocked": 0,
    "skipped": 0
  }
}
```

### progress.md

人类可读的进度报告（自动生成）：

```markdown
# RAG-SERVER 开发进度报告

生成时间: 2025-01-XX 18:30:00

## 📊 总体进度

[██░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░] 4%

- **总任务数**: 53
- **已完成**: 2
- **进行中**: 1
- **待开始**: 50
- **已阻塞**: 0
- **已跳过**: 0

## 🎯 当前状态

- **当前阶段**: 阶段A - Spring Boot初始化
- **当前任务**: A3 - 数据库初始化
- **开始时间**: 2025-01-XX 14:00:00

## ✅ 已完成任务 (2/53)

### 阶段A: Spring Boot初始化

- ✅ **A1** - Maven项目初始化 (P0)
  - 完成时间: 2025-01-XX 11:30:00
  - 生成文件: pom.xml, .gitignore, RagServerApplication.java
  - 备注: 初始化完成，编译通过

- ✅ **A2** - 配置管理体系 (P0)
  - 完成时间: 2025-01-XX 13:00:00
  - 生成文件: application.yaml, application-dev.yaml, ...
  - 备注: 配置加载测试通过

## 🔄 进行中任务 (1)

- 🔄 **A3** - 数据库初始化 (P0)
  - 开始时间: 2025-01-XX 14:00:00
  - 预计文件: DataSourceConfig.java, V1__init_schema.sql, ...

## 📋 待开始任务 (50)

### 阶段B: DashScope集成 (4个任务)

- ⏳ **B1** - DashScopeChatClient实现 (P0)
- ⏳ **B2** - DashScopeEmbeddingClient实现 (P0)
- ⏳ **B3** - API限流与重试 (P0)
- ⏳ **B4** - DashScope Vision LLM (P1 - 可选)

### 阶段C: Milvus集成 (5个任务)

...

## 📈 阶段进度

- **阶段A** (3个任务): ██████░░░░ 67% (2/3)
- **阶段B** (4个任务): ░░░░░░░░░░ 0% (0/4)
- **阶段C** (5个任务): ░░░░░░░░░░ 0% (0/5)
- ...
```

## 🔧 高级功能

### 备份与恢复

```bash
# 备份当前进度
node .claude/skills/dev-rag-server/dev-driver.mjs backup

# 恢复到指定备份
node .claude/skills/dev-rag-server/dev-driver.mjs restore backup-20250103-1830.json
```

### 进度导出

```bash
# 导出为Excel
node .claude/skills/dev-rag-server/dev-driver.mjs export --format excel

# 导出为JSON
node .claude/skills/dev-rag-server/dev-driver.mjs export --format json
```

### 统计分析

```bash
# 查看开发效率统计
node .claude/skills/dev-rag-server/dev-driver.mjs stats

# 输出示例:
# 平均每任务耗时: 1.5小时
# 最快任务: A1 (30分钟)
# 最慢任务: C3 (3小时)
# 总开发时间: 12小时
```

## 🎓 迭代升级路径

### 当前：v1.0 手动审核版

**特点**：每个任务都需要人工审核

**适用**：建立信任，学习代码风格

### 下一步：v2.0 半自动版

**升级时机**：
- ✅ 已完成10+个任务
- ✅ 代码风格一致性达到90%+
- ✅ 注释规范符合要求

**新功能**：
- Agent自动生成代码
- 自动运行测试
- 批量审核模式（一次审核3-5个任务）

**升级命令**：
```bash
node .claude/skills/dev-rag-server/dev-driver.mjs upgrade --to v2
```

### 未来：v3.0 全自动版

**升级时机**：
- ✅ v2.0运行稳定
- ✅ 连续20个任务零错误
- ✅ 完全信任Agent的判断

**新功能**：
- 全天候自动开发
- 智能错误修复
- 自动代码审查

## ⚠️ 注意事项

### 任务依赖

某些任务有依赖关系，必须按顺序完成：
- A2依赖A1（配置需要项目结构）
- B1依赖A2（DashScope配置在application.yaml）
- C1依赖B2（Milvus需要EmbeddingClient）

driver会自动检测依赖，阻止跳过关键任务。

### Git集成

每完成一个任务，建议提交：
```bash
git add .
git commit -m "feat: 完成任务A3 - 数据库初始化

- 创建DataSourceConfig配置类
- 添加Flyway迁移脚本
- 实现Repository接口

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### 代码审查清单

每个任务完成后检查：
- [ ] 所有类都有完整的中文JavaDoc
- [ ] 所有方法都有参数和返回值说明
- [ ] 界面文字（日志、异常、响应）都是中文
- [ ] 代码符合DEV_SPEC.md的架构设计
- [ ] 编译通过（mvn compile）
- [ ] 测试通过（如果有测试）

## 📚 参考资料

- **开发规范**: `DEV_SPEC.md`
- **Spring AI分析**: `SPRING_AI_GUIDE.md`（未追踪，本地参考）
- **纯Java方案**: `DEV_SPEC_PURE_JAVA.md`（未追踪，本地参考）

## 🆘 常见问题

### Q: 如何在新对话中恢复进度？

A: 进度自动保存在`task-tracker.json`，新对话中直接运行：
```bash
node .claude/skills/dev-rag-server/dev-driver.mjs status
```
就能看到当前进度并继续。

### Q: 任务生成的代码不满意怎么办？

A: 直接告诉Agent修改，不要标记完成。修改满意后再complete。

### Q: 可以跳过某个任务吗？

A: 可以，但注意：
- P0任务不建议跳过（核心功能）
- P1任务可以跳过（可选功能）
- 跳过后记得标记原因

### Q: 如何查看某个任务的详细说明？

A: 在对话中说：
```
"显示任务B2的详细说明"
```
Agent会从DEV_SPEC.md中提取该任务的完整内容。

---

**版本**: v1.0.0  
**最后更新**: 2025-01-XX  
**维护**: RAG-SERVER开发团队
