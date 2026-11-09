package com.ragserver.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

/**
 * MCP SDK API 探索测试
 *
 * 用于了解 MCP SDK 2.0.0 的正确 API 使用方式
 */
public class McpApiExplorerTest {

    @Test
    public void exploreStdioServerTransportProvider() {
        System.out.println("=== StdioServerTransportProvider 构造器 ===");
        Constructor<?>[] constructors = StdioServerTransportProvider.class.getConstructors();
        for (Constructor<?> constructor : constructors) {
            System.out.println("构造器: " + constructor);
            System.out.println("参数类型: ");
            for (Class<?> paramType : constructor.getParameterTypes()) {
                System.out.println("  - " + paramType.getName());
            }
        }
    }

    @Test
    public void exploreMcpServer() {
        System.out.println("=== McpServer 方法 ===");
        Method[] methods = McpServer.class.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() == McpServer.class) {
                System.out.println("方法: " + method.getName());
                System.out.println("  返回类型: " + method.getReturnType().getName());
                System.out.println("  参数类型: ");
                for (Class<?> paramType : method.getParameterTypes()) {
                    System.out.println("    - " + paramType.getName());
                }
            }
        }
    }

    @Test
    public void exploreMcpJsonDefaults() {
        System.out.println("=== McpJsonDefaults 方法 ===");
        Method[] methods = McpJsonDefaults.class.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() == McpJsonDefaults.class) {
                System.out.println("方法: " + method.getName());
                System.out.println("  返回类型: " + method.getReturnType().getName());
            }
        }
    }

    @Test
    public void exploreImplementation() {
        System.out.println("=== McpSchema.Implementation 方法 ===");
        Method[] methods = McpSchema.Implementation.class.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() == McpSchema.Implementation.class) {
                System.out.println("方法: " + method.getName());
                System.out.println("  返回类型: " + method.getReturnType().getName());
                System.out.println("  参数类型: ");
                for (Class<?> paramType : method.getParameterTypes()) {
                    System.out.println("    - " + paramType.getName());
                }
            }
        }
    }
}
