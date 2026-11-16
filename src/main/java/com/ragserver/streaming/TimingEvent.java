package com.ragserver.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * 耗时统计事件（流结束时发送）
 *
 * <p>在流式输出完成后发送，包含各阶段的耗时统计信息。</p>
 *
 * @param retrievalMs 检索耗时（毫秒）
 * @param promptBuildMs Prompt构建耗时（毫秒）
 * @param ttftMs Time To First Token - 首字延迟（毫秒）
 * @param generationMs 流式生成耗时（毫秒）
 * @param totalMs 总耗时（毫秒）
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
public record TimingEvent(
    long retrievalMs,
    long promptBuildMs,
    long ttftMs,
    long generationMs,
    long totalMs
) implements StreamEvent {

    @Override
    public String eventType() {
        return "timing";
    }

    @Override
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"totalMs\":0}";
        }
    }
}
