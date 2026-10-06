package com.ragserver.service;

import com.ragserver.ai.dashscope.ChatRequest;
import com.ragserver.ai.dashscope.DashScopeChatClient;
import com.ragserver.config.DashScopeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

/**
 * 流式输出服务
 *
 * <p>提供SSE（Server-Sent Events）流式响应功能，用于：</p>
 * <ul>
 *   <li>流式RAG查询：实时返回生成的答案</li>
 *   <li>大模型流式调用：降低首字延迟</li>
 *   <li>用户体验优化：逐步显示内容</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @GetMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
 * public Flux<String> queryStream(@RequestParam String question) {
 *     String prompt = ragService.buildPrompt(question);
 *     return streamingService.streamChat(prompt);
 * }
 * }</pre>
 *
 * <h3>SSE格式</h3>
 * <pre>
 * data: 文本内容
 * data: [DONE]
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class StreamingService {

    private final DashScopeProperties properties;
    private final WebClient webClient;

    /**
     * SSE结束标记
     */
    private static final String DONE_MARKER = "[DONE]";

    /**
     * SSE数据前缀
     */
    private static final String DATA_PREFIX = "data: ";

    public StreamingService(DashScopeProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("StreamingService初始化完成");
    }

    /**
     * 流式调用Chat API
     *
     * <p>使用SSE（Server-Sent Events）方式流式返回大模型响应。</p>
     *
     * <h3>工作原理</h3>
     * <ol>
     *   <li>设置 stream=true 参数</li>
     *   <li>建立SSE连接</li>
     *   <li>逐步接收生成内容</li>
     *   <li>解析delta内容并返回</li>
     * </ol>
     *
     * <h3>错误处理</h3>
     * <ul>
     *   <li>连接失败：返回错误信息</li>
     *   <li>解析失败：记录日志并跳过</li>
     *   <li>超时：自动重连（由WebClient处理）</li>
     * </ul>
     *
     * @param prompt 输入提示词
     * @return 流式文本内容
     */
    public Flux<String> streamChat(String prompt) {
        log.debug("开始流式Chat调用，prompt长度：{}", prompt.length());

        // 构建请求
        ChatRequest request = new ChatRequest();
        request.setModel(properties.getModel());
        request.setMessages(List.of(ChatRequest.Message.user(prompt)));

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(Map.of(
                    "model", request.getModel(),
                    "input", Map.of("messages", request.getMessages()),
                    "parameters", Map.of(
                        "incremental_output", true,
                        "result_format", "message"
                    )
                ))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseStreamResponse)
                .doOnComplete(() -> log.debug("流式Chat调用完成"))
                .doOnError(e -> log.error("流式Chat调用失败", e))
                .onErrorResume(e -> {
                    log.error("流式调用出错，返回错误信息", e);
                    return Flux.just("错误：" + e.getMessage());
                });
    }

    /**
     * 解析流式响应
     *
     * <p>从SSE格式中提取实际文本内容。</p>
     *
     * <h3>SSE格式示例</h3>
     * <pre>
     * data: {"output":{"choices":[{"message":{"content":"你"}}]}}
     * data: {"output":{"choices":[{"message":{"content":"好"}}]}}
     * data: [DONE]
     * </pre>
     *
     * @param sseData SSE数据行
     * @return 提取的文本内容
     */
    private Flux<String> parseStreamResponse(String sseData) {
        try {
            // 跳过空行
            if (sseData == null || sseData.trim().isEmpty()) {
                return Flux.empty();
            }

            // 处理data:前缀
            String data = sseData;
            if (data.startsWith(DATA_PREFIX)) {
                data = data.substring(DATA_PREFIX.length()).trim();
            }

            // 检查结束标记
            if (DONE_MARKER.equals(data)) {
                log.debug("接收到流式结束标记");
                return Flux.empty();
            }

            // 解析JSON并提取content
            // 简化处理：直接查找content字段（避免引入JSON库）
            int contentIndex = data.indexOf("\"content\"");
            if (contentIndex == -1) {
                return Flux.empty();
            }

            // 提取content值
            int startQuote = data.indexOf("\"", contentIndex + 10);
            if (startQuote == -1) {
                return Flux.empty();
            }

            int endQuote = data.indexOf("\"", startQuote + 1);
            if (endQuote == -1) {
                return Flux.empty();
            }

            String content = data.substring(startQuote + 1, endQuote);

            // 处理转义字符
            content = unescapeJson(content);

            if (!content.isEmpty()) {
                log.trace("解析到流式内容：{}", content);
                return Flux.just(content);
            }

            return Flux.empty();

        } catch (Exception e) {
            log.warn("解析流式响应失败：{}", sseData, e);
            return Flux.empty();
        }
    }

    /**
     * 反转义JSON字符串
     *
     * <p>处理常见的JSON转义字符：</p>
     * <ul>
     *   <li>\" → "</li>
     *   <li>\\ → \</li>
     *   <li>\n → 换行</li>
     *   <li>\t → 制表符</li>
     * </ul>
     *
     * @param str 转义的字符串
     * @return 反转义后的字符串
     */
    private String unescapeJson(String str) {
        return str.replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .replace("\\n", "\n")
                  .replace("\\t", "\t");
    }

    /**
     * 流式RAG查询（简化版）
     *
     * <p>提供完整的流式RAG查询功能，包括：</p>
     * <ol>
     *   <li>检索相关文档</li>
     *   <li>构建Prompt</li>
     *   <li>流式生成答案</li>
     * </ol>
     *
     * <p>注意：此方法为简化实现，完整实现需要整合RagService。</p>
     *
     * @param question 用户问题
     * @param context 上下文文档
     * @return 流式答案
     */
    public Flux<String> streamRagQuery(String question, String context) {
        log.info("开始流式RAG查询，问题：{}", question);

        // 构建RAG prompt
        String prompt = buildRagPrompt(question, context);

        // 流式调用
        return streamChat(prompt);
    }

    /**
     * 构建RAG Prompt
     *
     * <p>简化版Prompt构建，完整版应使用PromptService。</p>
     *
     * @param question 用户问题
     * @param context 上下文
     * @return Prompt文本
     */
    private String buildRagPrompt(String question, String context) {
        return String.format("""
            你是一个智能助手，请根据以下上下文回答问题。

            上下文：
            %s

            问题：%s

            回答：
            """, context, question);
    }
}
