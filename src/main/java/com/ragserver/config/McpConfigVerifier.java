package com.ragserver.config;

/**
 * MCP配置验证工具类
 *
 * <p>用于验证MCP配置是否可以被正确加载和使用。</p>
 *
 * <p>此类提供静态方法来验证配置的正确性。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
public class McpConfigVerifier {

    /**
     * 验证McpServerProperties配置
     *
     * @param properties MCP Server配置属性
     * @return 验证是否通过
     */
    public static boolean verify(McpServerProperties properties) {
        if (properties == null) {
            System.err.println("McpServerProperties is null");
            return false;
        }

        System.out.println("=== MCP Configuration Verification ===");
        System.out.println("Server Name: " + properties.getName());
        System.out.println("Version: " + properties.getVersion());
        System.out.println("Description: " + properties.getDescription());
        System.out.println("Enabled: " + properties.getEnabled());

        boolean passed = true;

        // 验证必需字段
        if (properties.getName() == null || properties.getName().isEmpty()) {
            System.err.println("ERROR: Server name is empty");
            passed = false;
        }

        if (properties.getVersion() == null || properties.getVersion().isEmpty()) {
            System.err.println("ERROR: Version is empty");
            passed = false;
        }

        if (properties.getDescription() == null || properties.getDescription().isEmpty()) {
            System.err.println("ERROR: Description is empty");
            passed = false;
        }

        if (properties.getEnabled() == null) {
            System.err.println("ERROR: Enabled is null");
            passed = false;
        }

        System.out.println("=== Verification " + (passed ? "PASSED" : "FAILED") + " ===");
        return passed;
    }

    /**
     * 验证McpConfig配置
     *
     * @param config MCP配置
     * @return 验证是否通过
     */
    public static boolean verify(McpConfig config) {
        if (config == null) {
            System.err.println("McpConfig is null");
            return false;
        }

        System.out.println("=== MCP Config Verification ===");
        System.out.println("McpConfig instance created successfully");

        McpServerProperties properties = config.getMcpServerProperties();
        if (properties == null) {
            System.err.println("ERROR: Cannot get McpServerProperties from McpConfig");
            return false;
        }

        System.out.println("McpServerProperties accessible from McpConfig");
        return verify(properties);
    }

    /**
     * 验证默认值
     *
     * @return 验证是否通过
     */
    public static boolean verifyDefaults() {
        System.out.println("=== Default Values Verification ===");

        McpServerProperties properties = new McpServerProperties();

        boolean passed = true;

        if (!"rag-server".equals(properties.getName())) {
            System.err.println("ERROR: Default name should be 'rag-server', but got: " + properties.getName());
            passed = false;
        } else {
            System.out.println("Default name: OK (rag-server)");
        }

        if (!"1.0.0".equals(properties.getVersion())) {
            System.err.println("ERROR: Default version should be '1.0.0', but got: " + properties.getVersion());
            passed = false;
        } else {
            System.out.println("Default version: OK (1.0.0)");
        }

        if (!"Enterprise RAG Framework with Hybrid Retrieval".equals(properties.getDescription())) {
            System.err.println("ERROR: Default description mismatch");
            passed = false;
        } else {
            System.out.println("Default description: OK");
        }

        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            System.err.println("ERROR: Default enabled should be true, but got: " + properties.getEnabled());
            passed = false;
        } else {
            System.out.println("Default enabled: OK (true)");
        }

        System.out.println("=== Default Values " + (passed ? "PASSED" : "FAILED") + " ===");
        return passed;
    }

    /**
     * 主方法，用于独立运行验证
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.out.println("Starting MCP Configuration Verification...\n");

        boolean defaultsOk = verifyDefaults();

        System.out.println("\n=== Summary ===");
        System.out.println("Default values verification: " + (defaultsOk ? "PASSED" : "FAILED"));

        System.exit(defaultsOk ? 0 : 1);
    }
}
