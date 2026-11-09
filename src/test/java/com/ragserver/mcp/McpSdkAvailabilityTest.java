package com.ragserver.mcp;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MCP SDK 可用性测试
 *
 * 验证 MCP SDK 依赖已正确添加，相关类可以导入和使用
 *
 * 任务：T001 - 验证 MCP SDK 依赖可用性
 */
public class McpSdkAvailabilityTest {

    @Test
    public void testMcpCoreClassesAvailable() {
        // 验证核心 MCP 类可以访问
        assertNotNull(McpError.class, "McpError 类应该可用");
        assertNotNull(McpSchema.class, "McpSchema 类应该可用");
        assertNotNull(McpServerSession.class, "McpServerSession 类应该可用");
    }

    @Test
    public void testMcpServerClassesAvailable() {
        // 验证 MCP Server 相关类可以访问
        assertNotNull(McpServer.class, "McpServer 类应该可用");
        assertNotNull(McpServerFeatures.class, "McpServerFeatures 类应该可用");
        assertNotNull(StdioServerTransportProvider.class, "StdioServerTransportProvider 类应该可用");
    }

    @Test
    public void testMcpSchemaToolAvailable() {
        // 验证可以构建 Tool Schema
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("test_tool")
                .description("A test tool for verification")
                .build();

        assertNotNull(tool, "Tool 对象应该可以创建");
        assertEquals("test_tool", tool.name(), "Tool 名称应该正确");
        assertEquals("A test tool for verification", tool.description(), "Tool 描述应该正确");
    }

    @Test
    public void testMcpSchemaResourceAvailable() {
        // 验证可以构建 Resource Schema
        McpSchema.Resource resource = McpSchema.Resource.builder()
                .uri("file:///test/resource.txt")
                .name("test_resource")
                .description("A test resource")
                .build();

        assertNotNull(resource, "Resource 对象应该可以创建");
        assertEquals("file:///test/resource.txt", resource.uri(), "Resource URI 应该正确");
        assertEquals("test_resource", resource.name(), "Resource 名称应该正确");
    }
}
