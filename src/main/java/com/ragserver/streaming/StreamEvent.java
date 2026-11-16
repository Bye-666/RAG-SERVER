package com.ragserver.streaming;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 流式事件接口（简化版）
 *
 * <p>定义流式输出的事件类型，支持：</p>
 * <ul>
 *   <li>ContentEvent: 逐字流式文本内容</li>
 *   <li>TimingEvent: 耗时统计信息（流结束时发送）</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
public sealed interface StreamEvent permits ContentEvent, TimingEvent {

    /**
     * 获取事件类型
     * @return 事件类型字符串（用于 SSE 的 event 字段）
     */
    String eventType();

    /**
     * 转换为 JSON 字符串
     * @return JSON 格式的事件数据
     */
    String toJson();

    /**
     * 共享的 ObjectMapper 实例
     */
    ObjectMapper MAPPER = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
}
