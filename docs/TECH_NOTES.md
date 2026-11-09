# RAG-SERVER 技术笔记

> 记录开发过程中遇到的技术难点、典型问题和解决方案

**最后更新**: 2026-11-09 11:30  
**维护人**: 开发团队

---

## 目录

- [MCP Server 启动机制](#mcp-server-启动机制)
- [Spring Boot Configuration Properties 最佳实践](#spring-boot-configuration-properties-最佳实践)

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
