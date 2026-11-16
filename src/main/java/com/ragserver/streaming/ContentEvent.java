package com.ragserver.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * 内容事件（逐字流式文本）
 *
 * <p>用于流式传输生成的文本内容，每个事件包含一个或多个字符。</p>
 *
 * @param content 文本内容
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
public record ContentEvent(String content) implements StreamEvent {

    @Override
    public String eventType() {
        return "content";
    }

    @Override
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"content\":\"\"}";
        }
    }
}
