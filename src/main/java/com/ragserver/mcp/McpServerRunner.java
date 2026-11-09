package com.ragserver.mcp;

import com.ragserver.config.McpServerProperties;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MCP Server 启动器
 *
 * <p>在应用启动时初始化并启动 MCP Server（stdio 模式）。</p>
 *
 * <p>功能特性：</p>
 * <ul>
 *   <li>使用 stdio 传输层（标准输入/输出）与 MCP 客户端通信</li>
 *   <li>实现 {@link CommandLineRunner}，在 Spring Boot 启动后自动执行</li>
 *   <li>通过 {@code mcp.server.enabled} 配置项控制是否启用</li>
 *   <li>设置服务器基本信息（name、version、description）</li>
 *   <li>后台线程持续监听 MCP 协议消息，不阻塞主线程</li>
 * </ul>
 *
 * <p>配置示例（application.yaml）：</p>
 * <pre>{@code
 * mcp:
 *   server:
 *     enabled: true
 *     name: rag-server
 *     version: 1.0.0
 *     description: "Enterprise RAG Framework with Hybrid Retrieval"
 * }</pre>
 *
 * <p>注意事项：</p>
 * <ul>
 *   <li>MCP Server 在构建时自动启动非守护后台线程监听 stdin</li>
 *   <li>后台线程会持续运行，防止 JVM 退出</li>
 *   <li>后续任务将添加 Tools 注册（T004-T007）</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 * @see McpServerProperties
 * @see CommandLineRunner
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerRunner implements CommandLineRunner {

    private final McpServerProperties properties;

    /**
     * MCP Server 实例（volatile 保证线程安全）
     */
    private volatile McpSyncServer mcpSyncServer;

    /**
     * 构造器注入 MCP Server 配置属性
     *
     * @param properties MCP Server 配置属性
     */
    public McpServerRunner(McpServerProperties properties) {
        this.properties = properties;
        log.info("McpServerRunner 已初始化，配置: name={}, version={}, enabled={}",
                properties.getName(), properties.getVersion(), properties.getEnabled());
    }

    /**
     * Spring Boot 启动后执行，初始化并启动 MCP Server
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>创建 stdio 传输层（StdioServerTransportProvider）</li>
     *   <li>创建 MCP Server 实例</li>
     *   <li>配置服务器信息和功能</li>
     *   <li>注册 Tools（后续任务实现）</li>
     *   <li>MCP Server 自动启动后台线程监听 stdin/stdout</li>
     * </ol>
     *
     * @param args 命令行参数（未使用）
     */
    @Override
    public void run(String... args) {
        try {
            log.info("========================================");
            log.info("正在启动 MCP Server (stdio 模式)...");
            log.info("服务名称: {}", properties.getName());
            log.info("服务版本: {}", properties.getVersion());
            log.info("服务描述: {}", properties.getDescription());
            log.info("========================================");

            // 1. 创建 stdio 传输层（使用默认的 JSON Mapper）
            log.debug("创建 StdioServerTransportProvider...");
            StdioServerTransportProvider transport = new StdioServerTransportProvider(
                    McpJsonDefaults.getMapper()
            );

            // 2. 创建并配置 MCP Server
            log.debug("创建 McpServer 实例...");

            // 配置服务器能力：启用 Tools 功能
            McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                    .tools(true)  // 启用 Tools 功能
                    .build();
            log.debug("已配置服务器能力: Tools=enabled");

            // 创建 MCP Sync Server
            mcpSyncServer = McpServer.sync(transport)
                    .serverInfo(properties.getName(), properties.getVersion())
                    .capabilities(capabilities)
                    .build();

            // 3. 注册 Tools（后续任务 T004-T007 实现）
            // TODO: 在后续任务中注册 query_knowledge_hub、list_collections、get_document_summary
            log.info("Tools 注册: 待实现（任务 T004-T007）");

            // 4. 注册 shutdown hook，确保优雅关闭
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("收到关闭信号，正在关闭 MCP Server...");
                try {
                    if (mcpSyncServer != null) {
                        mcpSyncServer.close();
                    }
                } catch (Exception e) {
                    log.error("关闭 MCP Server 时出错", e);
                } finally {
                    log.info("MCP Server 已关闭");
                }
            }));

            // 5. MCP Server 已启动
            log.info("MCP Server 已启动，后台线程正在监听 stdin/stdout");
            log.info("应用将继续运行，直到收到关闭信号");
            log.info("========================================");

        } catch (Exception e) {
            log.error("MCP Server 启动失败", e);
            throw new RuntimeException("Failed to start MCP Server", e);
        }
    }
}
