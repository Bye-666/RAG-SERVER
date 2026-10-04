package com.ragserver.ai.dashscope;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * DashScope Chat API 响应对象
 *
 * <p>封装DashScope兼容模式API的响应数据。</p>
 *
 * <h3>响应示例</h3>
 * <pre>
 * {
 *   "id": "chatcmpl-abc123",
 *   "object": "chat.completion",
 *   "created": 1704067200,
 *   "model": "qwen-max",
 *   "choices": [
 *     {
 *       "index": 0,
 *       "message": {
 *         "role": "assistant",
 *         "content": "你好！我是通义千问，很高兴为您服务。"
 *       },
 *       "finish_reason": "stop"
 *     }
 *   ],
 *   "usage": {
 *     "prompt_tokens": 10,
 *     "completion_tokens": 20,
 *     "total_tokens": 30
 *   }
 * }
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
public class ChatResponse {

    /**
     * 响应ID
     */
    private String id;

    /**
     * 对象类型（固定为"chat.completion"）
     */
    private String object;

    /**
     * 创建时间戳（Unix时间）
     */
    private Long created;

    /**
     * 使用的模型名称
     */
    private String model;

    /**
     * 生成结果列表（通常只有1个）
     */
    private List<Choice> choices;

    /**
     * Token使用统计
     */
    private Usage usage;

    /**
     * 生成结果
     */
    @Data
    public static class Choice {
        /**
         * 结果索引（从0开始）
         */
        private Integer index;

        /**
         * 生成的消息
         */
        private ChatRequest.Message message;

        /**
         * 结束原因
         *
         * <p>可选值：</p>
         * <ul>
         *   <li>stop：正常结束</li>
         *   <li>length：达到max_tokens限制</li>
         *   <li>content_filter：内容过滤</li>
         * </ul>
         */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    /**
     * Token使用统计
     */
    @Data
    public static class Usage {
        /**
         * 输入token数
         */
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        /**
         * 输出token数
         */
        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        /**
         * 总token数
         */
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    /**
     * 获取生成的文本内容
     *
     * <p>快捷方法，返回第一个choice的消息内容。</p>
     *
     * @return 生成的文本，如果没有结果则返回null
     */
    public String getContent() {
        if (choices != null && !choices.isEmpty()) {
            Choice choice = choices.get(0);
            if (choice.getMessage() != null) {
                return choice.getMessage().getContent();
            }
        }
        return null;
    }

    /**
     * 检查是否成功生成
     *
     * @return 是否有有效的生成结果
     */
    public boolean isSuccess() {
        return choices != null && !choices.isEmpty() && getContent() != null;
    }
}
