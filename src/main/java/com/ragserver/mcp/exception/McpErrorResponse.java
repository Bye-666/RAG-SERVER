package com.ragserver.mcp.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * MCP 统一错误响应
 *
 * <p>所有 MCP Tool 的错误都应该返回此格式的响应。</p>
 *
 * @param success    是否成功（始终为 false）
 * @param errorCode  错误码（如 VALIDATION_ERROR, SERVICE_ERROR）
 * @param message    用户友好的错误消息
 * @param details    详细信息（可选，包含额外的调试信息）
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpErrorResponse(
        boolean success,
        String errorCode,
        String message,
        Map<String, Object> details
) {
    /**
     * 从 McpException 创建错误响应
     *
     * @param exception MCP 异常
     * @return 错误响应
     */
    public static McpErrorResponse fromException(McpException exception) {
        return new McpErrorResponse(
                false,
                exception.getErrorCode(),
                exception.getMessage(),
                null  // 生产环境不返回 details，避免泄露敏感信息
        );
    }

    /**
     * 从通用异常创建错误响应
     *
     * @param exception 异常
     * @return 错误响应
     */
    public static McpErrorResponse fromException(Exception exception) {
        return new McpErrorResponse(
                false,
                "INTERNAL_ERROR",
                "Internal server error occurred",  // 通用消息，不暴露细节
                null
        );
    }

    /**
     * 创建简单错误响应
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @return 错误响应
     */
    public static McpErrorResponse of(String errorCode, String message) {
        return new McpErrorResponse(false, errorCode, message, null);
    }

    /**
     * 创建带详细信息的错误响应
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param details   详细信息
     * @return 错误响应
     */
    public static McpErrorResponse of(String errorCode, String message, Map<String, Object> details) {
        return new McpErrorResponse(false, errorCode, message, details);
    }
}
