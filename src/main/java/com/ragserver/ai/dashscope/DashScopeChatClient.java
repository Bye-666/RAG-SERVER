package com.ragserver.ai.dashscope;

import com.ragserver.config.DashScopeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * DashScope Chat客户端
 *
 * <p>封装DashScope Chat API调用，提供：</p>
 * <ul>
 *   <li>HTTP请求封装</li>
 *   <li>异常处理</li>
 *   <li>重试机制（3次，指数退避）</li>
 *   <li>超时控制</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Service
 * public class ChatService {
 *     @Autowired
 *     private DashScopeChatClient chatClient;
 *
 *     public String chat(String userMessage) {
 *         ChatRequest request = new ChatRequest();
 *         request.setModel("qwen-max");
 *         request.setMessages(List.of(
 *             ChatRequest.Message.user(userMessage)
 *         ));
 *
 *         ChatResponse response = chatClient.call(request);
 *         return response.getContent();
 *     }
 * }
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class DashScopeChatClient {

    private final DashScopeProperties properties;
    private final RestTemplate restTemplate;

    /**
     * Chat API端点
     */
    private static final String CHAT_ENDPOINT = "/chat/completions";

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRIES = 3;

    /**
     * 初始重试延迟（毫秒）
     */
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    public DashScopeChatClient(DashScopeProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * 调用Chat API
     *
     * <p>发送聊天请求并返回响应，支持自动重试。</p>
     *
     * <p>重试策略：</p>
     * <ul>
     *   <li>最大重试3次</li>
     *   <li>指数退避：1秒、2秒、4秒</li>
     *   <li>仅对网络错误和5xx错误重试</li>
     *   <li>4xx错误（参数错误、认证失败等）不重试</li>
     * </ul>
     *
     * @param request 聊天请求
     * @return 聊天响应
     * @throws DashScopeException 调用失败时抛出
     */
    public ChatResponse call(ChatRequest request) {
        // 设置默认模型
        if (request.getModel() == null) {
            request.setModel(properties.getModel());
        }

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                log.debug("调用DashScope Chat API，尝试次数：{}/{}", attempt + 1, MAX_RETRIES);
                return doCall(request);
            } catch (HttpClientErrorException e) {
                // 4xx错误不重试（参数错误、认证失败等）
                log.error("DashScope API调用失败（客户端错误）：{} - {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new DashScopeException("DashScope API调用失败：" + e.getMessage(), e);
            } catch (HttpServerErrorException e) {
                // 5xx错误可重试
                lastException = e;
                log.warn("DashScope API调用失败（服务器错误），尝试次数：{}/{}，错误：{}",
                    attempt + 1, MAX_RETRIES, e.getMessage());
            } catch (Exception e) {
                // 其他异常（网络错误、超时等）可重试
                lastException = e;
                log.warn("DashScope API调用失败，尝试次数：{}/{}，错误：{}",
                    attempt + 1, MAX_RETRIES, e.getMessage());
            }

            attempt++;

            // 如果还有重试机会，等待后重试
            if (attempt < MAX_RETRIES) {
                long delayMs = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1)); // 指数退避：1s, 2s, 4s
                log.info("等待{}毫秒后重试...", delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new DashScopeException("重试被中断", ie);
                }
            }
        }

        // 所有重试都失败
        throw new DashScopeException(
            String.format("DashScope API调用失败，已重试%d次", MAX_RETRIES),
            lastException
        );
    }

    /**
     * 执行实际的HTTP调用
     *
     * @param request 聊天请求
     * @return 聊天响应
     */
    private ChatResponse doCall(ChatRequest request) {
        // 构建请求URL
        String url = properties.getBaseUrl() + CHAT_ENDPOINT;

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        // 构建请求体
        HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

        // 发送请求
        log.debug("发送请求到DashScope：URL={}, Model={}, Messages={}",
            url, request.getModel(), request.getMessages().size());

        ResponseEntity<ChatResponse> responseEntity = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            ChatResponse.class
        );

        ChatResponse response = responseEntity.getBody();

        if (response == null) {
            throw new DashScopeException("DashScope API返回空响应");
        }

        if (!response.isSuccess()) {
            throw new DashScopeException("DashScope API返回无效响应：" + response);
        }

        log.debug("DashScope API调用成功，Token使用：{}", response.getUsage());

        return response;
    }

    /**
     * 简化调用：发送单条消息
     *
     * <p>快捷方法，适合简单的一问一答场景。</p>
     *
     * @param userMessage 用户消息
     * @return 生成的回复文本
     */
    public String chat(String userMessage) {
        ChatRequest request = new ChatRequest();
        request.setModel(properties.getModel());
        request.setMessages(List.of(ChatRequest.Message.user(userMessage)));

        ChatResponse response = call(request);
        return response.getContent();
    }

    /**
     * 简化调用：带系统提示词
     *
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @return 生成的回复文本
     */
    public String chat(String systemPrompt, String userMessage) {
        ChatRequest request = new ChatRequest();
        request.setModel(properties.getModel());
        request.setMessages(List.of(
            ChatRequest.Message.system(systemPrompt),
            ChatRequest.Message.user(userMessage)
        ));

        ChatResponse response = call(request);
        return response.getContent();
    }

    /**
     * DashScope异常
     */
    public static class DashScopeException extends RuntimeException {
        public DashScopeException(String message) {
            super(message);
        }

        public DashScopeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
