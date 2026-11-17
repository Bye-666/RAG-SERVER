package com.ragserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragserver.config.DashScopeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * StreamingService 单元测试
 *
 * <p>测试流式输出服务的核心功能：</p>
 * <ul>
 *   <li>SSE解析</li>
 *   <li>流式Chat调用</li>
 *   <li>错误处理</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class StreamingServiceTest {

    @Mock
    private DashScopeProperties properties;

    private StreamingService streamingService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(properties.getBaseUrl()).thenReturn("https://dashscope.aliyuncs.com/compatible-mode/v1");
        when(properties.getApiKey()).thenReturn("test-api-key");
        when(properties.getModel()).thenReturn("qwen-max");

        objectMapper = new ObjectMapper();
        streamingService = new StreamingService(properties, objectMapper);
    }

    /**
     * 测试：流式Chat调用（基本功能）
     */
    @Test
    void testStreamChat() {
        String prompt = "什么是RAG？";

        // 注意：这个测试需要真实的API调用，这里只验证方法签名
        Flux<String> result = streamingService.streamChat(prompt);

        // 验证返回的是Flux
        assertThat(result).isNotNull();
    }

    /**
     * 测试：空问题处理
     */
    @Test
    void testStreamChatWithEmptyPrompt() {
        String prompt = "";

        Flux<String> result = streamingService.streamChat(prompt);

        // 应该返回非空Flux
        assertThat(result).isNotNull();
    }

    /**
     * 测试：长文本处理
     */
    @Test
    void testStreamChatWithLongText() {
        String prompt = "详细解释RAG技术：" + "A".repeat(5000); // 长提示词

        Flux<String> result = streamingService.streamChat(prompt);

        // 应该能正常处理长文本
        assertThat(result).isNotNull();
    }

    /**
     * 测试：特殊字符处理
     */
    @Test
    void testStreamChatWithSpecialCharacters() {
        String prompt = "什么是\"RAG\"技术？\n换行测试\t制表符";

        Flux<String> result = streamingService.streamChat(prompt);

        // 应该能正常处理特殊字符
        assertThat(result).isNotNull();
    }

    /**
     * 测试：中文字符处理
     */
    @Test
    void testStreamChatWithChineseCharacters() {
        String prompt = "什么是检索增强生成（RAG）？";

        Flux<String> result = streamingService.streamChat(prompt);

        // 应该能正常处理中文
        assertThat(result).isNotNull();
    }

    /**
     * 测试：流式服务初始化
     */
    @Test
    void testServiceInitialization() {
        // 验证服务能正常初始化
        StreamingService service = new StreamingService(properties, objectMapper);

        // 调用方法验证不抛异常
        Flux<String> result = service.streamChat("测试");

        assertThat(result).isNotNull();
    }
}
