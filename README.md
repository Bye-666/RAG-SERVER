# RAG-SERVER

基于Spring Boot 3.2和Spring AI 1.0.0-M3的企业级RAG框架

## 项目状态

🚧 **开发中** - 当前完成阶段A任务1（Maven项目初始化）

### 开发进度

- ✅ **阶段A1**: Maven项目初始化（2025-01-03完成）
- ⏳ **阶段A2**: 配置管理体系（待开始）
- ⏳ **阶段A3**: 数据库初始化（待开始）

查看完整进度：[进度报告](.claude/skills/dev-rag-server/progress.md)

## 技术栈

- **框架**: Spring Boot 3.2.1 (Java 17+)
- **AI抽象**: Spring AI 1.0.0-M3（将在阶段B集成）
- **LLM**: 阿里云DashScope 通义千问
- **Embedding**: DashScope text-embedding-v4 (2048维)
- **向量库**: Milvus 2.5+
- **数据库**: H2/SQLite
- **文档处理**: Apache PDFBox 3.0.8

## 快速开始

### 前置要求

- JDK 17+
- Maven 3.8+
- （可选）Docker（用于运行Milvus）

### 编译项目

```bash
mvn clean compile
```

### 运行应用

```bash
mvn spring-boot:run
```

应用启动后访问：
- 健康检查：http://localhost:8080/actuator/health

## 项目结构

```
RAG-SERVER/
├── src/main/java/com/ragserver/
│   └── RagServerApplication.java       # 主类
├── src/main/resources/
│   └── application.yaml                # 配置文件
├── pom.xml                             # Maven配置
├── DEV_SPEC.md                         # 开发规范（53个任务）
└── .claude/skills/dev-rag-server/      # 自动化开发Skill
    ├── SKILL.md                        # Skill说明
    ├── dev-driver.mjs                  # 开发驱动器
    ├── task-tracker.json               # 任务追踪
    └── progress.md                     # 进度报告
```

## 开发说明

### 使用自动化开发Skill

本项目使用自动化Skill进行迭代开发：

```bash
# 查看当前进度
node .claude/skills/dev-rag-server/dev-driver.mjs status

# 开始下一个任务
node .claude/skills/dev-rag-server/dev-driver.mjs next
```

详见：[迭代指南](.claude/skills/dev-rag-server/HOW_TO_ITERATE.md)

## 文档

- [开发规范](DEV_SPEC.md) - 完整的53个开发任务
- [Spring AI能力分析](SPRING_AI_GUIDE.md) - 未追踪
- [纯Java方案](DEV_SPEC_PURE_JAVA.md) - 备份参考（未追踪）

## 当前阶段注意事项

### 阶段A1完成状态

✅ 已完成：
- Maven项目初始化
- Spring Boot 3.2.1配置
- 基础目录结构
- application.yaml配置

⚠️ 暂时注释的依赖（将在后续阶段启用）：
- Spring AI（阶段B）
- Milvus SDK（阶段C）
- MCP SDK（阶段F）
- PDFBox（阶段D）
- Testcontainers（阶段C）

**原因**：国内Maven镜像无法访问部分依赖，将在需要时解决。

## 许可证

MIT License

---

**最后更新**: 2025-01-03  
**版本**: 1.0.0-SNAPSHOT