# RAG-SERVER 技术笔记

> 记录开发过程中遇到的技术难点、典型问题和解决方案

**最后更新**: 2026-11-16 14:20  
**维护人**: 开发团队

---

## 目录

- [SSE 流式传输内容丢失问题](#sse-流式传输内容丢失问题)
- [Spring AI MCP 框架迁移](#spring-ai-mcp-框架迁移)
- [MCP Server 启动机制](#mcp-server-启动机制)
- [Spring Boot Configuration Properties 最佳实践](#spring-boot-configuration-properties-最佳实践)

---

## SSE 流式传输内容丢失问题

**日期**: 2026-11-16  
**类型**: 流式传输 Bug 修复

### 问题描述

流式 RAG 查询时，前端接收到的内容严重丢失且错乱。后端生成 1000+ 字符的完整答案，但前端只接收到 500-800 字符，且很多文本内容被截断。

### 现象对比

```
后端生成（完整）：1053 字符
## 华东精密2026年第三季度报告摘要
华东精密制造股份有限公司披露2026年第三季度报告摘要...
[完整内容]

前端接收（错乱）：586 字符
##管理，不计入本制度范围。公司实行标准工时:00至0，在保证核心...
[大量内容缺失，文字错乱]
```

### 问题定位过程

#### 1. 排查方向

通过对比数据库存储和前端显示，确认：
- ✅ 数据库存储完整 → 后端生成正常
- ❌ 前端显示不完整 → 问题在前端 SSE 解析

#### 2. 添加调试日志

**后端**：输出完整生成内容
```java
log.info("=== 后端生成完整答案 ===");
log.info("答案长度: {} 字符", fullAnswer.length());
log.info("答案内容:\n{}", fullAnswer);
```

**前端**：输出每个 chunk 和事件解析结果
```javascript
console.log(`=== Chunk ${chunkCount} ===`);
console.log('Buffer 内容:', buffer.substring(0, 200));
console.log('解析出事件数:', events.parsed.length);
console.log('事件类型:', event.type, '数据:', event.data);
```

#### 3. 关键发现

观察日志发现：
```javascript
=== Chunk 3 ===
Buffer 内容: event:content\ndata:
解析出事件数: 0
剩余 buffer 长度: 5

=== Chunk 4 ===
Buffer 内容: {"content":"考勤与休假管理制度"}\n\n
解析出事件数: 1
事件类型: message  // ❌ 应该是 'content'！
数据: {content: '考勤与休假管理制度'}
```

**问题原因**：
- Chunk 3 的 buffer 是 `event:content\ndata:`（保留 5 字符）
- Chunk 4 来了 `{"content":"考勤与休假管理制度"}\n\n`
- 合并后的 buffer 是 `data:{"content":"..."}\n\n`
- **缺少了 `event:content` 行**，`parseSSE` 函数使用默认的 `type: 'message'`
- 前端代码只处理 `type === 'content'` 的事件，导致内容被丢弃

### 根本原因

**SSE 事件被网络分割成多个 chunk，`parseSSE` 函数没有在多次调用之间保持状态。**

SSE 标准格式：
```
event:content
data:{"content":"文本"}

(空行表示事件结束)
```

但网络传输时可能分成 3 个 chunk：
```
Chunk 1: event:content\ndata:
Chunk 2: {"content":"文本"}
Chunk 3: \n\n
```

旧版 `parseSSE` 函数每次调用都创建新的 `currentEvent`，导致跨 chunk 的事件类型丢失。

### 解决方案

**使用函数属性持久化事件状态**，使其在多次调用之间保持。

#### 修复前（错误）

```javascript
function parseSSE(buffer) {
    const parsed = [];
    const lines = buffer.split('\n');
    
    // ❌ 每次调用都创建新的 currentEvent
    let currentEvent = { type: 'message', data: '' };
    
    // ... 解析逻辑
    
    return { parsed, remaining };
}
```

#### 修复后（正确）

```javascript
function parseSSE(buffer) {
    const parsed = [];
    const lines = buffer.split('\n');
    
    // ✅ 使用函数属性持久化状态
    if (!parseSSE.currentEvent) {
        parseSSE.currentEvent = { type: 'message', data: '' };
    }
    let currentEvent = parseSSE.currentEvent;
    
    // ... 解析逻辑
    
    // ✅ 保存当前事件状态（供下次调用使用）
    parseSSE.currentEvent = currentEvent;
    
    return { parsed, remaining };
}
```

#### 在查询开始时重置状态

```javascript
async function executeStreamQuery(query, topK, enableRerank) {
    try {
        // ✅ 重置 parseSSE 状态
        parseSSE.currentEvent = null;
        
        // ... 流式读取和解析
    }
}
```

### 验证结果

修复后对比：
```
后端生成：1053 字符
前端接收：1053 字符
✅ 完全一致！
```

### 关键代码位置

- **前端 SSE 解析**: `src/main/resources/static/js/app.js` - `parseSSE()` 函数
- **前端流式查询**: `src/main/resources/static/js/app.js` - `executeStreamQuery()` 函数
- **后端日志输出**: `src/main/java/com/ragserver/service/RagService.java`
- **相关提交**: `fix: 修复SSE流式传输内容丢失问题`

### 技术要点

#### 1. SSE 协议理解

Server-Sent Events (SSE) 格式：
```
event: <event_type>
data: <json_data>

(空行)
```

- `event:` 行指定事件类型
- `data:` 行包含数据（可以有多行）
- 空行表示事件结束
- 注释行以 `:` 开头

#### 2. 流式数据解析状态管理

处理流式数据时，必须考虑：
- **数据分片**：一个完整的逻辑单元可能跨越多个物理 chunk
- **状态保持**：解析器需要在多次调用之间保持状态
- **边界处理**：不完整的数据需要保留到下次处理

#### 3. JavaScript 函数属性

JavaScript 函数也是对象，可以添加属性：
```javascript
function myFunc() { }
myFunc.state = { ... };  // ✅ 可以用来保持状态

// 使用场景：
// - 单例模式
// - 缓存
// - 状态机
```

### 经验总结

1. **流式数据解析必须考虑跨 chunk 情况**
   - 不要假设一个逻辑单元总是在一个 chunk 内
   - 必须设计状态机来处理不完整的数据

2. **调试流式传输问题的方法**
   - 对比源头（后端）和终点（前端）的数据
   - 逐 chunk 输出 buffer 内容
   - 记录每个事件的解析结果
   - 关注事件类型是否正确

3. **SSE 解析的最佳实践**
   - 使用状态机模式
   - 正确处理不完整的行（buffer 末尾可能没有 `\n`）
   - 保留未完成的事件到下次调用
   - 在新请求开始时重置状态

4. **测试要覆盖边界情况**
   - 单个 chunk 包含完整事件
   - 事件跨越多个 chunk
   - `event:` 和 `data:` 在不同 chunk
   - `data:` 内容跨越多个 chunk

### 参考资料

- [Server-Sent Events Specification](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [MDN: Using Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
- [EventSource API](https://developer.mozilla.org/en-US/docs/Web/API/EventSource)

---

## Spring AI MCP 框架迁移

**任务**: F-004 实现 ragQuery Tool  
**日期**: 2026-11-09  
**类型**: 架构重构

### 问题描述

原计划使用底层 MCP SDK 手动实现 Tool 注册，但遇到版本兼容性问题和复杂的协议处理。需要切换到更高层的 Spring AI MCP 框架。

### 技术难点

#### 1. 依赖版本冲突

**现象**：添加 `spring-ai-starter-mcp-server-webmvc:1.0.0` 后，Lombok 注解处理失效，出现大量 "找不到符号" 错误（`log` 变量、`@Data` 生成的方法等）。

**根本原因**：
- Spring AI MCP 1.0.0 依赖 Spring Boot 3.4.5
- 项目使用 Spring Boot 3.2.1 和 Spring AI 1.0.0-M3
- Maven 自动降级依赖版本导致注解处理器冲突

**解决方案**：
```xml
<!-- 升级到兼容版本 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.1</version>  <!-- 从 3.2.1 升级 -->
</parent>

<properties>
    <spring-ai.version>1.0.0</spring-ai.version>  <!-- 从 1.0.0-M3 升级 -->
</properties>
```

**教训**：
- ⚠️ 使用新框架前必须检查依赖兼容性
- ⚠️ Milestone 版本和正式版本不要混用
- ✅ 升级 Spring Boot 3.2 → 3.4 通常是兼容的

#### 2. Spring AI Function Calling 机制

**关键发现**：Spring AI MCP 不需要手动实现 MCP Tool 接口，而是通过 **Function Calling** 机制自动转换。

**正确实现方式**：
```java
@Configuration
public class RagMcpTools {
    
    // 定义参数结构
    public record RagQueryRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("User question")
        String question,
        
        @JsonPropertyDescription("Number of documents (default: 10)")
        Integer topK
    ) {}
    
    // 使用 @Bean + @Description 定义 Tool
    @Bean
    @Description("Query the RAG knowledge base")
    public Function<RagQueryRequest, String> ragQuery(RagService ragService) {
        return request -> {
            // 业务逻辑
            return ragService.query(request.question(), topK).getAnswer();
        };
    }
}
```

**工作原理**：
1. Spring AI 扫描所有 `Function<Request, Response>` 类型的 Bean
2. `@Description` 注解提供 Tool 描述
3. Jackson 注解（`@JsonProperty`, `@JsonPropertyDescription`）生成 JSON Schema
4. `McpServerAutoConfiguration` 自动转换为 MCP `Tool` 规范
5. 通过 SSE 或 Stdio 暴露给 MCP Client

**优势**：
- ✅ 无需手动处理 MCP 协议细节
- ✅ 类型安全（编译时检查）
- ✅ 自动生成 JSON Schema
- ✅ Spring Boot Autoconfigure 自动注册

#### 3. YAML 配置陷阱

**错误示例**：
```yaml
spring:
  application:
    name: rag-server
  ai:
    dashscope:
      api-key: xxx

# 后面又出现了 spring:
spring:  # ❌ 重复键！
  ai:
    mcp:
      server:
        name: rag-server
```

**正确方式**：
```yaml
spring:
  application:
    name: rag-server
  ai:
    dashscope:
      api-key: xxx
    mcp:  # ✅ 合并到同一个 ai: 块
      server:
        name: rag-server
```

**教训**：
- ⚠️ YAML 不允许同级重复键
- ⚠️ 大文件容易出现这种错误
- ✅ 使用 IDE 的 YAML 验证功能

### 最终方案

**架构选择**：Spring AI MCP（高层框架）> 底层 MCP SDK

**代码结构**：
```
com.ragserver.mcp/
├── RagMcpTools.java  // Function Calling 方式定义所有 Tools
└── (删除) 旧的配置类：McpConfig、McpServerRunner 等
```

**配置文件**：
```yaml
spring:
  ai:
    mcp:
      server:
        name: rag-server
        version: 1.0.0
        description: "Enterprise RAG Framework"
        transport: stdio
```

### 参考资料

- [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/api/mcp.html)
- [MCP Specification](https://modelcontextprotocol.io/)
- Spring Boot 3.4 Release Notes

---

## MCP Server 启动机制

**任务**: F-003 实现 McpServerRunner  
**日期**: 2026-11-09  
**类型**: 架构设计

### 问题描述

MCP Server 启动后是否需要阻塞主线程？如果不阻塞，后台线程会继续运行吗？

### 技术验证

#### 1. 源码分析

**调用链**：
```
McpServer.sync(transport).build()
  -> new McpSyncServer(new McpAsyncServer(...))
    -> McpAsyncServer 构造函数
      -> transportProvider.setSessionFactory(...)
        -> StdioServerTransportProvider.setSessionFactory()
          -> transport.initProcessing()
            -> startInboundProcessing()  // 启动 stdio-inbound 线程
            -> startOutboundProcessing() // 启动 stdio-outbound 线程
```

**关键代码**（`StdioServerTransportProvider.java`）：
```java
public StdioMcpSessionTransport() {
    // 使用 Executors.newSingleThreadExecutor() 创建线程池
    this.inboundScheduler = Schedulers.fromExecutorService(
        Executors.newSingleThreadExecutor(), "stdio-inbound");
    this.outboundScheduler = Schedulers.fromExecutorService(
        Executors.newSingleThreadExecutor(), "stdio-outbound");
}
```

#### 2. 线程属性验证

通过 `ThreadPoolTest.java` 验证：
```java
ExecutorService executor = Executors.newSingleThreadExecutor();
executor.submit(() -> {
    Thread t = Thread.currentThread();
    System.out.println("isDaemon: " + t.isDaemon());  // 输出: false
});
```

**结论**：`Executors.newSingleThreadExecutor()` 创建的线程是**非 daemon 线程**。

### 解决方案

#### 错误实现（第一版）
```java
@Override
public void run(String... args) {
    mcpSyncServer = McpServer.sync(transport).build();
    
    // ❌ 阻塞主线程
    CountDownLatch latch = new CountDownLatch(1);
    latch.await();
}
```

**问题**：
- 违反 Spring Boot CommandLineRunner 最佳实践
- 主线程被阻塞（虽然不影响 Web 功能）

#### 正确实现（修复后）
```java
@Override
public void run(String... args) {
    mcpSyncServer = McpServer.sync(transport).build();
    
    // 注册 shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        if (mcpSyncServer != null) {
            mcpSyncServer.close();
        }
    }));
    
    // ✅ 直接返回，不阻塞
    log.info("MCP Server 已启动，后台线程正在监听");
}
```

**原理**：
- MCP Server 的后台线程是**非 daemon 线程**
- 非 daemon 线程会阻止 JVM 退出
- 无需手动阻塞主线程

### 关键代码位置

- **MCP Server 实现**: `src/main/java/com/ragserver/mcp/McpServerRunner.java`
- **验证测试**: 源码分析 + 线程属性测试
- **相关提交**: `feat(mcp): [F-003] 实现 MCP Server 启动器`

### 经验总结

1. **不要假设，要验证**：通过源码分析和实验验证线程属性
2. **遵循框架最佳实践**：Spring Boot CommandLineRunner 不应阻塞
3. **理解底层机制**：JVM 进程管理、daemon vs non-daemon 线程

---

## Spring Boot Configuration Properties 最佳实践

**任务**: F-002 创建 McpConfig 配置类  
**日期**: 2026-11-09  
**类型**: 代码规范

### 问题描述

如何正确使用 `@ConfigurationProperties` 绑定配置？

### 错误实现

```java
@Data
@Component  // ❌ 不推荐
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {
    private String name;
    private String version;
}
```

**问题**：
- `@ConfigurationProperties` 类不应该作为通用 Component 被扫描
- 不符合 Spring Boot 官方推荐方式

### 正确实现

#### 1. 配置属性类（纯配置）
```java
@Data
@Validated
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {
    @NotBlank(message = "MCP服务名称不能为空")
    private String name = "rag-server";
    
    @NotBlank(message = "服务版本号不能为空")
    private String version = "1.0.0";
    
    @NotNull(message = "enabled配置项不能为null")
    private Boolean enabled = true;
}
```

#### 2. 配置类（注册 Bean）
```java
@Configuration
@EnableConfigurationProperties(McpServerProperties.class)  // ✅ 推荐方式
public class McpConfig {
    private final McpServerProperties properties;
    
    public McpConfig(McpServerProperties properties) {
        this.properties = properties;
    }
}
```

### 必需依赖

在 `pom.xml` 中添加：
```xml
<!-- 生成配置元数据，支持 IDE 自动补全 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

并在 `maven-compiler-plugin` 中配置注解处理器：
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-configuration-processor</artifactId>
                <version>${project.parent.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

### 效果

- ✅ 编译时自动生成 `META-INF/spring-configuration-metadata.json`
- ✅ IDE 自动补全 `mcp.server.*` 配置项
- ✅ 配置校验（JSR-303）在启动时自动触发

### 关键代码位置

- **配置属性类**: `src/main/java/com/ragserver/config/McpServerProperties.java`
- **配置类**: `src/main/java/com/ragserver/config/McpConfig.java`
- **相关提交**: `feat(mcp): [F-002] 创建 MCP Server 配置类`

### 经验总结

1. **配置属性类应该是纯配置**：不要添加 `@Component`
2. **使用 @EnableConfigurationProperties 注册**：在配置类上启用
3. **添加 JSR-303 校验**：使用 `@Validated` + `@NotBlank` 等注解
4. **配置 configuration-processor**：支持 IDE 自动补全

---

## 待补充内容

后续开发中遇到的技术难点将持续更新到此文档。

**更新时间**: 每个阶段完成后由 TaskArchitect 整理
