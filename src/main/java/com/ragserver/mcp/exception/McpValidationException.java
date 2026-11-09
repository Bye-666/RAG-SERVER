package com.ragserver.mcp.exception;

import java.util.Map;

/**
 * MCP 参数验证异常
 *
 * <p>当 MCP Tool 的输入参数不合法时抛出此异常。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
public class McpValidationException extends McpException {

    private static final String ERROR_CODE = "VALIDATION_ERROR";

    /**
     * 构造函数
     *
     * @param message 错误消息
     */
    public McpValidationException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * 构造函数（带详细信息）
     *
     * @param message 错误消息
     * @param details 详细信息
     */
    public McpValidationException(String message, Map<String, Object> details) {
        super(ERROR_CODE, message, details);
    }

    /**
     * 创建参数为空的验证异常
     *
     * @param paramName 参数名称
     * @return 验证异常
     */
    public static McpValidationException parameterRequired(String paramName) {
        return new McpValidationException("Required parameter '" + paramName + "' is missing or empty")
                .addDetail("parameter", paramName);
    }

    /**
     * 创建参数值无效的验证异常
     *
     * @param paramName 参数名称
     * @param value     参数值
     * @param reason    无效原因
     * @return 验证异常
     */
    public static McpValidationException parameterInvalid(String paramName, Object value, String reason) {
        return new McpValidationException("Invalid parameter '" + paramName + "': " + reason)
                .addDetail("parameter", paramName)
                .addDetail("value", value)
                .addDetail("reason", reason);
    }

    /**
     * 创建参数范围错误的验证异常
     *
     * @param paramName 参数名称
     * @param value     参数值
     * @param min       最小值
     * @param max       最大值
     * @return 验证异常
     */
    public static McpValidationException parameterOutOfRange(String paramName, Object value, Object min, Object max) {
        return new McpValidationException("Parameter '" + paramName + "' is out of range [" + min + ", " + max + "]")
                .addDetail("parameter", paramName)
                .addDetail("value", value)
                .addDetail("min", min)
                .addDetail("max", max);
    }

    @Override
    public McpValidationException addDetail(String key, Object value) {
        super.addDetail(key, value);
        return this;
    }
}
