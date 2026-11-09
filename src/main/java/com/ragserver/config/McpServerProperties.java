package com.ragserver.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MCP Server配置属性类
 *
 * <p>绑定application.yaml中mcp.server开头的配置项。</p>
 *
 * <p>配置示例：</p>
 * <pre>{@code
 * mcp:
 *   server:
 *     name: rag-server
 *     version: 1.0.0
 *     description: "Enterprise RAG Framework with Hybrid Retrieval"
 *     enabled: true
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    /**
     * MCP服务名称，用于标识服务
     * <p>默认值：rag-server</p>
     */
    @NotBlank(message = "MCP服务名称不能为空")
    private String name = "rag-server";

    /**
     * 服务版本号（SemVer规范）
     * <p>默认值：1.0.0</p>
     */
    @NotBlank(message = "服务版本号不能为空")
    private String version = "1.0.0";

    /**
     * 服务描述信息
     * <p>默认值：Enterprise RAG Framework with Hybrid Retrieval</p>
     */
    private String description = "Enterprise RAG Framework with Hybrid Retrieval";

    /**
     * 是否启用MCP Server
     * <p>默认值：true</p>
     * <p>禁用后，MCP相关的Bean不会被创建</p>
     */
    @NotNull(message = "enabled配置项不能为null")
    private Boolean enabled = true;
}
