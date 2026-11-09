package com.ragserver.mcp.exception;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP 基础异常类
 *
 * <p>所有 MCP 相关异常的基类，提供错误码和详细信息。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Getter
public class McpException extends RuntimeException {

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 详细信息
     */
    private final Map<String, Object> details;

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public McpException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    /**
     * 构造函数（带异常原因）
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     异常原因
     */
    public McpException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    /**
     * 构造函数（带详细信息）
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param details   详细信息
     */
    public McpException(String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details != null ? new HashMap<>(details) : new HashMap<>();
    }

    /**
     * 构造函数（完整参数）
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     异常原因
     * @param details   详细信息
     */
    public McpException(String errorCode, String message, Throwable cause, Map<String, Object> details) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details != null ? new HashMap<>(details) : new HashMap<>();
    }

    /**
     * 添加详细信息
     *
     * @param key   键
     * @param value 值
     * @return 当前异常对象（支持链式调用）
     */
    public McpException addDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }
}
