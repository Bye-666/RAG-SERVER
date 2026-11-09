package com.ragserver.mcp;

import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/**
 * 探索 McpSyncServer 的可用方法
 */
public class McpSyncServerExplorerTest {

    @Test
    public void exploreMcpSyncServerMethods() {
        System.out.println("=== McpSyncServer 方法 ===");
        Method[] methods = McpSyncServer.class.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() == McpSyncServer.class ||
                method.getDeclaringClass().getName().contains("modelcontextprotocol")) {
                System.out.println("方法: " + method.getName());
                System.out.println("  声明类: " + method.getDeclaringClass().getSimpleName());
                System.out.println("  返回类型: " + method.getReturnType().getSimpleName());
                System.out.print("  参数: ");
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length == 0) {
                    System.out.println("无参数");
                } else {
                    System.out.println();
                    for (Class<?> paramType : paramTypes) {
                        System.out.println("    - " + paramType.getSimpleName());
                    }
                }
                System.out.println();
            }
        }
    }
}
