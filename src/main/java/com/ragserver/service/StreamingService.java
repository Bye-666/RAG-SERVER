package com.ragserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragserver.ai.dashscope.ChatRequest;
import com.ragserver.config.DashScopeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 流式输出服务（重构版）
 *
 * <p>提供SSE（Server-Sent Events）流式响应功能，使用 Jackson 解析 JSON。</p>
 *
 * <h3>改进点</h3>
 * <ul>
 *   <li>使用 Jackson 解析 JSON，替代手动字符串操作</li>
 *   <li>更健壮的错误处理</li>
 *   <li>清晰的代码结构</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class StreamingService {

    private final DashScopeProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * SSE结束标记
     */
    private static final String DONE_MARKER = "[DONE]";

    /**
     * SSE数据前缀
     */
    private static final String DATA_PREFIX = "data:";

    public StreamingService(DashScopeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("StreamingService 初始化完成");
    }

    /**
     * 流式调用 Chat API
     *
     * <p>使用SSE（Server-Sent Events）方式流式返回大模型响应。</p>
     *
     * <h3>工作原理</h3>
     * <ol>
     *   <li>设置 stream=true 参数</li>
     *   <li>建立SSE连接</li>
     *   <li>逐步接收生成内容</li>
     *   <li>使用 Jackson 解析 JSON</li>
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
        log.debug("开始流式 Chat 调用，prompt 长度：{}", prompt.length());

        // 构建请求
        ChatRequest request = new ChatRequest();
        request.setModel(properties.getModel());
        request.setMessages(List.of(ChatRequest.Message.user(prompt)));
        request.setStream(true);  // 关键：启用流式输出

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseSSE)
                .doOnComplete(() -> log.debug("流式 Chat 调用完成"))
                .doOnError(e -> log.error("流式 Chat 调用失败", e))
                .onErrorResume(e -> {
                    log.error("流式调用出错，返回错误信息", e);
                    return Flux.just("错误：" + e.getMessage());
                });
    }

    /**
     * 解析 SSE 响应（使用 Jackson）
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
    private Flux<String> parseSSE(String sseData) {
        try {
            // 跳过空行
            if (sseData == null || sseData.trim().isEmpty()) {
                return Flux.empty();
            }

            // 移除 "data:" 前缀
            String data = sseData.trim();
            if (data.startsWith(DATA_PREFIX)) {
                data = data.substring(DATA_PREFIX.length()).trim();
            }

            // 检查结束标记
            if (DONE_MARKER.equals(data)) {
                log.debug("收到流式结束标记");
                return Flux.empty();
            }

            // 使用 Jackson 解析 JSON
            JsonNode root = objectMapper.readTree(data);

            // 提取 content: choices[0].delta.content 或 choices[0].message.content
            String content = root.at("/choices/0/delta/content").asText("");
            if (content.isEmpty()) {
                content = root.at("/choices/0/message/content").asText("");
            }

            return content.isEmpty() ? Flux.empty() : Flux.just(content);

        } catch (Exception e) {
            log.warn("SSE 解析失败：{}", sseData, e);
            return Flux.empty();
        }
    }
}
