package com.ragserver.service;

import com.ragserver.config.DashScopeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

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

    @BeforeEach
    void setUp() {
        when(properties.getBaseUrl()).thenReturn("https://dashscope.aliyuncs.com/compatible-mode/v1");
        when(properties.getApiKey()).thenReturn("test-api-key");
        when(properties.getModel()).thenReturn("qwen-max");

        streamingService = new StreamingService(properties);
    }

    /**
     * 测试：构建RAG Prompt
     */
    @Test
    void testBuildRagPrompt() {
        String question = "什么是RAG？";
        String context = "RAG是检索增强生成技术...";

        Flux<String> result = streamingService.streamRagQuery(question, context);

        // 验证返回的是Flux
        StepVerifier.create(result.take(1))
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 测试：空问题处理
     */
    @Test
    void testStreamRagQueryWithEmptyQuestion() {
        String question = "";
        String context = "一些上下文";

        Flux<String> result = streamingService.streamRagQuery(question, context);

        // 应该能正常处理
        StepVerifier.create(result.take(1))
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 测试：空上下文处理
     */
    @Test
    void testStreamRagQueryWithEmptyContext() {
        String question = "什么是RAG？";
        String context = "";

        Flux<String> result = streamingService.streamRagQuery(question, context);

        // 应该能正常处理
        StepVerifier.create(result.take(1))
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 测试：长文本处理
     */
    @Test
    void testStreamRagQueryWithLongText() {
        String question = "详细解释RAG技术？";
        String context = "A".repeat(5000); // 5000字符的上下文

        Flux<String> result = streamingService.streamRagQuery(question, context);

        // 应该能正常处理长文本
        StepVerifier.create(result.take(1))
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 测试：特殊字符处理
     */
    @Test
    void testStreamRagQueryWithSpecialCharacters() {
        String question = "什么是\"RAG\"技术？\n换行测试";
        String context = "包含特殊字符：\"引号\"\n换行\t制表符";

        Flux<String> result = streamingService.streamRagQuery(question, context);

        // 应该能正常处理特殊字符
        StepVerifier.create(result.take(1))
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 测试：中文字符处理
     */
    @Test
    void testStreamRagQueryWithChineseCharacters() {
        String question = "什么是检索增强生成（RAG）？";
        String context = "检索增强生成（Retrieval-Augmented Generation，RAG）是一种结合了检索和生成的技术。";

        Flux<String> result = streamingService.streamRagQuery(question, context);

        // 应该能正常处理中文
        StepVerifier.create(result.take(1))
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 测试：JSON转义处理
     */
    @Test
    void testUnescapeJson() {
        // 通过streamRagQuery间接测试（内部会调用unescapeJson）
        String question = "测试转义";
        String context = "包含\\n换行和\\t制表符";

        Flux<String> result = streamingService.streamRagQuery(question, context);

        StepVerifier.create(result.take(1))
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 测试：流式服务初始化
     */
    @Test
    void testServiceInitialization() {
        // 验证服务能正常初始化
        StreamingService service = new StreamingService(properties);

        // 调用方法验证不抛异常
        Flux<String> result = service.streamRagQuery("测试", "上下文");

        StepVerifier.create(result.take(1))
                .expectNextCount(1)
                .verifyComplete();
    }
}
