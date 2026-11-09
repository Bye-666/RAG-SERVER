package com.ragserver.mcp.exception;

import java.util.Map;

/**
 * MCP 服务调用异常
 *
 * <p>当调用底层服务（RagService, IngestionService 等）失败时抛出此异常。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
public class McpServiceException extends McpException {

    private static final String ERROR_CODE = "SERVICE_ERROR";

    /**
     * 构造函数
     *
     * @param message 错误消息
     */
    public McpServiceException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 构造函数（带异常原因）
     *
     * @param message 错误消息
     * @param cause   异常原因
     */
    public McpServiceException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    /**
     * 构造函数（带详细信息）
     *
     * @param message 错误消息
     * @param details 详细信息
     */
    public McpServiceException(String message, Map<String, Object> details) {
        super(ERROR_CODE, message, details);
    }

    /**
     * 构造函数（完整参数）
     *
     * @param message 错误消息
     * @param cause   异常原因
     * @param details 详细信息
     */
    public McpServiceException(String message, Throwable cause, Map<String, Object> details) {
        super(ERROR_CODE, message, cause, details);
    }

    @Override
    public McpServiceException addDetail(String key, Object value) {
        super.addDetail(key, value);
        return this;
    }
}
