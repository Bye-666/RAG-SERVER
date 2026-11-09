package com.ragserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpConfig配置类测试
 *
 * <p>验证MCP配置类和配置属性是否可以被正确注入和使用。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
    "mcp.server.enabled=true",
    "mcp.server.name=test-rag-server",
    "mcp.server.version=1.0.0-test",
    "mcp.server.description=Test MCP Server"
})
class McpConfigTest {

    @Autowired(required = false)
    private McpConfig mcpConfig;

    @Autowired(required = false)
    private McpServerProperties mcpServerProperties;

    /**
     * 测试McpConfig是否可以被正确注入
     */
    @Test
    void testMcpConfigInjection() {
        assertNotNull(mcpConfig, "McpConfig应该可以被注入");
    }

    /**
     * 测试McpServerProperties是否可以被正确注入
     */
    @Test
    void testMcpServerPropertiesInjection() {
        assertNotNull(mcpServerProperties, "McpServerProperties应该可以被注入");
    }

    /**
     * 测试配置属性值是否正确读取
     */
    @Test
    void testMcpServerPropertiesValues() {
        assertNotNull(mcpServerProperties, "McpServerProperties应该可以被注入");

        // 验证配置值
        assertEquals("test-rag-server", mcpServerProperties.getName(),
            "服务名称应该从测试配置读取");
        assertEquals("1.0.0-test", mcpServerProperties.getVersion(),
            "版本号应该从测试配置读取");
        assertEquals("Test MCP Server", mcpServerProperties.getDescription(),
            "描述应该从测试配置读取");
        assertTrue(mcpServerProperties.getEnabled(),
            "enabled应该为true");
    }

    /**
     * 测试McpConfig中的getter方法
     */
    @Test
    void testMcpConfigGetProperties() {
        assertNotNull(mcpConfig, "McpConfig应该可以被注入");

        McpServerProperties properties = mcpConfig.getMcpServerProperties();
        assertNotNull(properties, "应该可以从McpConfig获取McpServerProperties");
        assertEquals("test-rag-server", properties.getName(),
            "从McpConfig获取的属性应该包含正确的服务名称");
    }

    /**
     * 测试默认值
     */
    @Test
    void testDefaultValues() {
        // 创建一个新的McpServerProperties实例，验证默认值
        McpServerProperties defaultProperties = new McpServerProperties();

        assertEquals("rag-server", defaultProperties.getName(),
            "默认服务名称应该为rag-server");
        assertEquals("1.0.0", defaultProperties.getVersion(),
            "默认版本号应该为1.0.0");
        assertEquals("Enterprise RAG Framework with Hybrid Retrieval",
            defaultProperties.getDescription(),
            "默认描述应该正确");
        assertTrue(defaultProperties.getEnabled(),
            "默认enabled应该为true");
    }
}
