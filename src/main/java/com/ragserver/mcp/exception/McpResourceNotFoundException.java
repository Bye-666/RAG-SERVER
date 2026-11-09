package com.ragserver.mcp.exception;

import java.util.Map;

/**
 * MCP 资源不存在异常
 *
 * <p>当请求的资源（文档、Collection 等）不存在时抛出此异常。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
public class McpResourceNotFoundException extends McpException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    /**
     * 构造函数
     *
     * @param message 错误消息
     */
    public McpResourceNotFoundException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 构造函数（带详细信息）
     *
     * @param message 错误消息
     * @param details 详细信息
     */
    public McpResourceNotFoundException(String message, Map<String, Object> details) {
        super(ERROR_CODE, message, details);
    }

    /**
     * 创建文档不存在异常
     *
     * @param idOrPath 文档 ID 或路径
     * @return 资源不存在异常
     */
    public static McpResourceNotFoundException documentNotFound(String idOrPath) {
        return new McpResourceNotFoundException("Document not found: " + idOrPath)
                .addDetail("idOrPath", idOrPath)
                .addDetail("resourceType", "document");
    }

    /**
     * 创建 Collection 不存在异常
     *
     * @param collectionName Collection 名称
     * @return 资源不存在异常
     */
    public static McpResourceNotFoundException collectionNotFound(String collectionName) {
        return new McpResourceNotFoundException("Collection not found: " + collectionName)
                .addDetail("collectionName", collectionName)
                .addDetail("resourceType", "collection");
    }

    /**
     * 创建文件不存在异常
     *
     * @param filePath 文件路径
     * @return 资源不存在异常
     */
    public static McpResourceNotFoundException fileNotFound(String filePath) {
        return new McpResourceNotFoundException("File not found: " + filePath)
                .addDetail("filePath", filePath)
                .addDetail("resourceType", "file");
    }

    @Override
    public McpResourceNotFoundException addDetail(String key, Object value) {
        super.addDetail(key, value);
        return this;
    }
}
