package com.ragserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MCP配置类
 *
 * <p>配置MCP Server的基础信息和相关Bean。</p>
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>注册MCP Server配置属性（McpServerProperties）</li>
 *   <li>根据enabled配置条件性启用MCP功能</li>
 *   <li>提供MCP相关Bean的初始化</li>
 * </ul>
 *
 * <h3>配置要求</h3>
 * <p>在application.yaml中配置：</p>
 * <pre>{@code
 * mcp:
 *   server:
 *     name: rag-server
 *     version: 1.0.0
 *     description: "Enterprise RAG Framework with Hybrid Retrieval"
 *     enabled: true
 * }</pre>
 *
 * <h3>启用条件</h3>
 * <p>只有当mcp.server.enabled=true时，此配置类才会生效。</p>
 *
 * <h3>依赖关系</h3>
 * <ul>
 *   <li>依赖McpServerProperties配置属性类</li>
 *   <li>后续将创建McpServer、McpToolRegistry等Bean</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 * @see McpServerProperties
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
@ConditionalOnProperty(prefix = "mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpConfig {

    private final McpServerProperties mcpServerProperties;

    /**
     * 构造函数，注入MCP Server配置属性
     *
     * @param mcpServerProperties MCP Server配置属性
     */
    public McpConfig(McpServerProperties mcpServerProperties) {
        this.mcpServerProperties = mcpServerProperties;

        log.info("MCP配置已启用：name={}, version={}, description={}",
            mcpServerProperties.getName(),
            mcpServerProperties.getVersion(),
            mcpServerProperties.getDescription());
    }

    /**
     * 获取MCP Server配置属性
     *
     * <p>供其他Bean使用，获取MCP服务的基本信息。</p>
     *
     * @return MCP Server配置属性
     */
    public McpServerProperties getMcpServerProperties() {
        return mcpServerProperties;
    }
}
