package com.ragserver.ai.dashscope;

import com.ragserver.config.DashScopeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * DashScopeChatClient单元测试
 *
 * <p>使用MockRestServiceServer模拟HTTP响应。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("DashScopeChatClient单元测试")
class DashScopeChatClientTest {

    private DashScopeChatClient chatClient;
    private MockRestServiceServer mockServer;
    private DashScopeProperties properties;

    @BeforeEach
    void setUp() {
        // 配置属性
        properties = new DashScopeProperties();
        properties.setApiKey("test-api-key");
        properties.setBaseUrl("http://localhost:8080/mock");
        properties.setModel("qwen-max");
        properties.setTimeoutMs(60000);

        // 创建RestTemplate和MockServer
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        // 创建客户端
        chatClient = new DashScopeChatClient(properties, restTemplate);
    }

    @Test
    @DisplayName("应能成功调用Chat API")
    void testCallSuccess() {
        // Mock响应
        String mockResponse = """
            {
              "id": "chatcmpl-123",
              "object": "chat.completion",
              "created": 1704067200,
              "model": "qwen-max",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "你好！我是通义千问。"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 15,
                "total_tokens": 25
              }
            }
            """;

        mockServer.expect(requestTo("http://localhost:8080/mock/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-api-key"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        // 创建请求
        ChatRequest request = new ChatRequest();
        request.setModel("qwen-max");
        request.setMessages(List.of(ChatRequest.Message.user("你好")));

        // 调用
        ChatResponse response = chatClient.call(request);

        // 验证
        assertNotNull(response, "响应不应为null");
        assertEquals("chatcmpl-123", response.getId());
        assertEquals("qwen-max", response.getModel());
        assertTrue(response.isSuccess(), "响应应成功");
        assertEquals("你好！我是通义千问。", response.getContent());

        // 验证Token使用
        assertNotNull(response.getUsage());
        assertEquals(10, response.getUsage().getPromptTokens());
        assertEquals(15, response.getUsage().getCompletionTokens());
        assertEquals(25, response.getUsage().getTotalTokens());

        // 验证所有Mock请求都被调用
        mockServer.verify();
    }

    @Test
    @DisplayName("应能使用简化调用方法")
    void testChatSimplified() {
        String mockResponse = """
            {
              "id": "chatcmpl-456",
              "object": "chat.completion",
              "created": 1704067200,
              "model": "qwen-max",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "简化调用成功"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 5,
                "completion_tokens": 8,
                "total_tokens": 13
              }
            }
            """;

        mockServer.expect(requestTo("http://localhost:8080/mock/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        // 简化调用
        String result = chatClient.chat("测试消息");

        assertEquals("简化调用成功", result);
        mockServer.verify();
    }

    @Test
    @DisplayName("应能处理4xx客户端错误（不重试）")
    void testClientError() {
        // Mock 401错误
        mockServer.expect(requestTo("http://localhost:8080/mock/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Invalid API key\"}"));

        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(ChatRequest.Message.user("测试")));

        // 验证抛出异常
        assertThrows(DashScopeChatClient.DashScopeException.class, () -> {
            chatClient.call(request);
        });

        // 验证只调用了一次（不重试）
        mockServer.verify();
    }

    @Test
    @DisplayName("应能重试5xx服务器错误")
    void testServerErrorRetry() {
        // 前两次返回500，第三次成功
        String successResponse = """
            {
              "id": "chatcmpl-789",
              "object": "chat.completion",
              "created": 1704067200,
              "model": "qwen-max",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "重试成功"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 5,
                "completion_tokens": 5,
                "total_tokens": 10
              }
            }
            """;

        mockServer.expect(requestTo("http://localhost:8080/mock/chat/completions"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockServer.expect(requestTo("http://localhost:8080/mock/chat/completions"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockServer.expect(requestTo("http://localhost:8080/mock/chat/completions"))
            .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));

        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(ChatRequest.Message.user("测试重试")));

        // 调用（应该在第3次成功）
        ChatResponse response = chatClient.call(request);

        assertNotNull(response);
        assertEquals("重试成功", response.getContent());

        // 验证调用了3次
        mockServer.verify();
    }

    @Test
    @DisplayName("应在所有重试失败后抛出异常")
    void testAllRetriesFailed() {
        // 所有3次都返回500
        mockServer.expect(requestTo("http://localhost:8080/mock/chat/completions"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockServer.expect(requestTo("http://localhost:8080/mock/chat/completions"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockServer.expect(requestTo("http://localhost:8080/mock/chat/completions"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(ChatRequest.Message.user("测试")));

        // 验证抛出异常
        DashScopeChatClient.DashScopeException exception = assertThrows(
            DashScopeChatClient.DashScopeException.class,
            () -> chatClient.call(request)
        );

        assertTrue(exception.getMessage().contains("已重试3次"));

        mockServer.verify();
    }

    @Test
    @DisplayName("ChatRequest.Message工厂方法应正常工作")
    void testMessageFactoryMethods() {
        ChatRequest.Message systemMsg = ChatRequest.Message.system("系统提示");
        assertEquals("system", systemMsg.getRole());
        assertEquals("系统提示", systemMsg.getContent());

        ChatRequest.Message userMsg = ChatRequest.Message.user("用户消息");
        assertEquals("user", userMsg.getRole());
        assertEquals("用户消息", userMsg.getContent());

        ChatRequest.Message assistantMsg = ChatRequest.Message.assistant("助手回复");
        assertEquals("assistant", assistantMsg.getRole());
        assertEquals("助手回复", assistantMsg.getContent());
    }

    @Test
    @DisplayName("ChatResponse应能正确判断成功状态")
    void testChatResponseSuccess() {
        // 成功的响应
        ChatResponse successResponse = new ChatResponse();
        ChatResponse.Choice choice = new ChatResponse.Choice();
        ChatRequest.Message message = new ChatRequest.Message();
        message.setContent("有内容");
        choice.setMessage(message);
        successResponse.setChoices(List.of(choice));

        assertTrue(successResponse.isSuccess());
        assertEquals("有内容", successResponse.getContent());

        // 失败的响应（没有choices）
        ChatResponse failedResponse1 = new ChatResponse();
        assertFalse(failedResponse1.isSuccess());
        assertNull(failedResponse1.getContent());

        // 失败的响应（choices为空）
        ChatResponse failedResponse2 = new ChatResponse();
        failedResponse2.setChoices(List.of());
        assertFalse(failedResponse2.isSuccess());
    }
}
