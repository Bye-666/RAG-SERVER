package com.ragserver.ai.dashscope;

import com.ragserver.config.DashScopeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DashScopeVisionClient 单元测试
 *
 * <p>测试Vision客户端的核心功能：</p>
 * <ul>
 *   <li>图片分析</li>
 *   <li>图片问答</li>
 *   <li>OCR文字识别</li>
 *   <li>批量处理</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DashScopeVisionClientTest {

    @Mock
    private DashScopeProperties properties;

    @Mock
    private RestTemplate restTemplate;

    private DashScopeVisionClient visionClient;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getBaseUrl()).thenReturn("https://dashscope.aliyuncs.com/compatible-mode/v1");
        lenient().when(properties.getApiKey()).thenReturn("test-api-key");
        lenient().when(properties.getQps()).thenReturn(10);

        visionClient = new DashScopeVisionClient(properties, restTemplate);
    }

    /**
     * 测试：分析图片（成功）
     */
    @Test
    void testAnalyzeImage_Success() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String prompt = "描述这张图片";

        // Mock响应
        Map<String, Object> mockResponse = Map.of(
            "output", Map.of(
                "choices", List.of(
                    Map.of("message", Map.of(
                        "role", "assistant",
                        "content", "这是一张美丽的风景照片，包含山川和湖泊。"
                    ))
                )
            )
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        String result = visionClient.analyzeImage(imageUrl, prompt);

        // Then
        assertThat(result).isEqualTo("这是一张美丽的风景照片，包含山川和湖泊。");
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    /**
     * 测试：分析图片（使用默认提示词）
     */
    @Test
    void testAnalyzeImage_DefaultPrompt() {
        // Given
        String imageUrl = "https://example.com/image.jpg";

        Map<String, Object> mockResponse = Map.of(
            "output", Map.of(
                "choices", List.of(
                    Map.of("message", Map.of(
                        "role", "assistant",
                        "content", "图片描述"
                    ))
                )
            )
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        String result = visionClient.analyzeImage(imageUrl);

        // Then
        assertThat(result).isEqualTo("图片描述");
    }

    /**
     * 测试：图片URL为空
     */
    @Test
    void testAnalyzeImage_EmptyUrl() {
        // When & Then
        assertThatThrownBy(() -> visionClient.analyzeImage("", "描述图片"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("图片URL不能为空");
    }

    /**
     * 测试：图片URL为null
     */
    @Test
    void testAnalyzeImage_NullUrl() {
        // When & Then
        assertThatThrownBy(() -> visionClient.analyzeImage(null, "描述图片"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("图片URL不能为空");
    }

    /**
     * 测试：图片问答
     */
    @Test
    void testAskAboutImage() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String question = "图中有几个人？";

        Map<String, Object> mockResponse = Map.of(
            "output", Map.of(
                "choices", List.of(
                    Map.of("message", Map.of(
                        "role", "assistant",
                        "content", "图中有3个人。"
                    ))
                )
            )
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        String answer = visionClient.askAboutImage(imageUrl, question);

        // Then
        assertThat(answer).isEqualTo("图中有3个人。");
    }

    /**
     * 测试：OCR文字识别
     */
    @Test
    void testExtractText() {
        // Given
        String imageUrl = "https://example.com/document.jpg";

        Map<String, Object> mockResponse = Map.of(
            "output", Map.of(
                "choices", List.of(
                    Map.of("message", Map.of(
                        "role", "assistant",
                        "content", "提取的文字内容：这是一份文档。"
                    ))
                )
            )
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        String text = visionClient.extractText(imageUrl);

        // Then
        assertThat(text).contains("提取的文字内容");
    }

    /**
     * 测试：批量分析图片
     */
    @Test
    void testAnalyzeImages_Batch() {
        // Given
        List<String> imageUrls = List.of(
            "https://example.com/image1.jpg",
            "https://example.com/image2.jpg"
        );
        String prompt = "描述图片";

        Map<String, Object> mockResponse = Map.of(
            "output", Map.of(
                "choices", List.of(
                    Map.of("message", Map.of(
                        "role", "assistant",
                        "content", "图片描述"
                    ))
                )
            )
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        List<String> results = visionClient.analyzeImages(imageUrls, prompt);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.equals("图片描述"));
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    /**
     * 测试：API调用失败
     */
    @Test
    void testAnalyzeImage_ApiFailure() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String prompt = "描述图片";

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> visionClient.analyzeImage(imageUrl, prompt))
            .isInstanceOf(RuntimeException.class);
    }

    /**
     * 测试：批量处理中部分失败
     */
    @Test
    void testAnalyzeImages_PartialFailure() {
        // Given
        List<String> imageUrls = List.of(
            "https://example.com/image1.jpg",
            "https://example.com/image2.jpg"
        );
        String prompt = "描述图片";

        Map<String, Object> mockResponse = Map.of(
            "output", Map.of(
                "choices", List.of(
                    Map.of("message", Map.of(
                        "role", "assistant",
                        "content", "图片描述"
                    ))
                )
            )
        );

        // 第一次成功，第二次失败
        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(Map.class)
        ))
        .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        // When
        List<String> results = visionClient.analyzeImages(imageUrls, prompt);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isEqualTo("图片描述");
        assertThat(results.get(1)).contains("分析失败");
    }

    /**
     * 测试：空提示词使用默认值
     */
    @Test
    void testAnalyzeImage_EmptyPromptUsesDefault() {
        // Given
        String imageUrl = "https://example.com/image.jpg";

        Map<String, Object> mockResponse = Map.of(
            "output", Map.of(
                "choices", List.of(
                    Map.of("message", Map.of(
                        "role", "assistant",
                        "content", "默认描述"
                    ))
                )
            )
        );

        when(restTemplate.postForEntity(
            anyString(),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        String result = visionClient.analyzeImage(imageUrl, "");

        // Then
        assertThat(result).isNotEmpty();
    }
}
