package com.ragserver.ai.dashscope;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * DashScope Chat API 请求对象
 *
 * <p>封装DashScope兼容模式API的请求参数。</p>
 *
 * <h3>API文档</h3>
 * <pre>
 * POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
 * Authorization: Bearer {api-key}
 * Content-Type: application/json
 *
 * {
 *   "model": "qwen-max",
 *   "messages": [
 *     {"role": "user", "content": "你好"}
 *   ],
 *   "temperature": 0.7,
 *   "max_tokens": 2000
 * }
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
public class ChatRequest {

    /**
     * 模型名称
     *
     * <p>可选值：</p>
     * <ul>
     *   <li>qwen-max：最强模型</li>
     *   <li>qwen-plus：平衡性能和成本</li>
     *   <li>qwen-turbo：快速响应</li>
     * </ul>
     */
    private String model;

    /**
     * 消息列表
     *
     * <p>对话历史和当前输入。</p>
     */
    private List<Message> messages;

    /**
     * 温度参数（0.0-2.0）
     *
     * <p>控制生成的随机性：</p>
     * <ul>
     *   <li>0.0：确定性输出</li>
     *   <li>0.7：平衡（推荐）</li>
     *   <li>2.0：创意性输出</li>
     * </ul>
     */
    private Double temperature = 0.7;

    /**
     * 最大生成token数
     *
     * <p>限制输出长度，默认2000。</p>
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens = 2000;

    /**
     * Top-P采样（0.0-1.0）
     *
     * <p>核采样参数，默认0.9。</p>
     */
    @JsonProperty("top_p")
    private Double topP = 0.9;

    /**
     * 停止词列表
     *
     * <p>遇到这些词时停止生成。</p>
     */
    private List<String> stop;

    /**
     * 是否流式输出
     *
     * <p>默认false，完整返回。</p>
     */
    private Boolean stream = false;

    /**
     * 聊天消息
     */
    @Data
    public static class Message {
        /**
         * 角色
         *
         * <p>可选值：system, user, assistant</p>
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        /**
         * 创建系统消息
         */
        public static Message system(String content) {
            return new Message("system", content);
        }

        /**
         * 创建用户消息
         */
        public static Message user(String content) {
            return new Message("user", content);
        }

        /**
         * 创建助手消息
         */
        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }
}
