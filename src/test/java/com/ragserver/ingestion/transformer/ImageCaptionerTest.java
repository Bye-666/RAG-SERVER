package com.ragserver.ingestion.transformer;

import com.ragserver.ai.dashscope.DashScopeVisionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ImageCaptioner 单元测试
 *
 * <p>测试图片描述生成器的核心功能：</p>
 * <ul>
 *   <li>默认描述生成</li>
 *   <li>简短描述生成</li>
 *   <li>详细描述生成</li>
 *   <li>批量处理</li>
 *   <li>错误处理</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ImageCaptionerTest {

    @Mock
    private DashScopeVisionClient visionClient;

    private ImageCaptioner imageCaptioner;

    @BeforeEach
    void setUp() {
        imageCaptioner = new ImageCaptioner(visionClient);
    }

    /**
     * 测试：生成默认描述
     */
    @Test
    void testGenerateCaption_Success() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String expectedCaption = "这是一张美丽的风景照片，包含山川、湖泊和蓝天白云。";

        when(visionClient.analyzeImage(eq(imageUrl), anyString()))
            .thenReturn(expectedCaption);

        // When
        String caption = imageCaptioner.generateCaption(imageUrl);

        // Then
        assertThat(caption).isEqualTo(expectedCaption);
        verify(visionClient, times(1)).analyzeImage(eq(imageUrl), anyString());
    }

    /**
     * 测试：生成简短描述
     */
    @Test
    void testGenerateShortCaption() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String expectedCaption = "一张风景照片";

        when(visionClient.analyzeImage(eq(imageUrl), anyString()))
            .thenReturn(expectedCaption);

        // When
        String caption = imageCaptioner.generateShortCaption(imageUrl);

        // Then
        assertThat(caption).isEqualTo(expectedCaption);
        assertThat(caption.length()).isLessThan(100);
    }

    /**
     * 测试：生成详细描述
     */
    @Test
    void testGenerateDetailedCaption() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String expectedCaption = "详细描述：\n1. 主要对象：山川湖泊\n2. 场景：自然风光\n3. 颜色：蓝绿色调";

        when(visionClient.analyzeImage(eq(imageUrl), anyString()))
            .thenReturn(expectedCaption);

        // When
        String caption = imageCaptioner.generateDetailedCaption(imageUrl);

        // Then
        assertThat(caption).isEqualTo(expectedCaption);
        assertThat(caption).contains("主要对象");
    }

    /**
     * 测试：批量生成描述
     */
    @Test
    void testGenerateCaptions_Batch() {
        // Given
        List<String> imageUrls = List.of(
            "https://example.com/image1.jpg",
            "https://example.com/image2.jpg"
        );
        List<String> expectedCaptions = List.of(
            "图片1描述",
            "图片2描述"
        );

        when(visionClient.analyzeImages(eq(imageUrls), anyString()))
            .thenReturn(expectedCaptions);

        // When
        List<String> captions = imageCaptioner.generateCaptions(imageUrls);

        // Then
        assertThat(captions).hasSize(2);
        assertThat(captions).isEqualTo(expectedCaptions);
    }

    /**
     * 测试：生成描述失败
     */
    @Test
    void testGenerateCaption_Failure() {
        // Given
        String imageUrl = "https://example.com/invalid.jpg";

        when(visionClient.analyzeImage(eq(imageUrl), anyString()))
            .thenThrow(new RuntimeException("图片分析失败"));

        // When
        String caption = imageCaptioner.generateCaption(imageUrl);

        // Then
        assertThat(caption).contains("失败");
    }

    /**
     * 测试：生成带元数据的描述
     */
    @Test
    void testGenerateCaptionWithMetadata_Success() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String fullCaption = "完整描述";
        String shortCaption = "简短描述";

        when(visionClient.analyzeImage(eq(imageUrl), anyString()))
            .thenReturn(fullCaption, shortCaption);

        // When
        Map<String, Object> result = imageCaptioner.generateCaptionWithMetadata(imageUrl);

        // Then
        assertThat(result).containsKey("caption");
        assertThat(result).containsKey("shortCaption");
        assertThat(result).containsKey("imageUrl");
        assertThat(result).containsKey("timestamp");
        assertThat(result.get("status")).isEqualTo("SUCCESS");
    }

    /**
     * 测试：生成带元数据的描述失败
     */
    @Test
    void testGenerateCaptionWithMetadata_Failure() {
        // Given
        String imageUrl = "https://example.com/invalid.jpg";

        when(visionClient.analyzeImage(eq(imageUrl), anyString()))
            .thenThrow(new RuntimeException("分析失败"));

        // When
        Map<String, Object> result = imageCaptioner.generateCaptionWithMetadata(imageUrl);

        // Then
        assertThat(result.get("status")).isEqualTo("FAILED");
        assertThat(result).containsKey("error");
    }

    /**
     * 测试：提取图片文字
     */
    @Test
    void testExtractText() {
        // Given
        String imageUrl = "https://example.com/document.jpg";
        String expectedText = "提取的文字内容";

        when(visionClient.extractText(imageUrl))
            .thenReturn(expectedText);

        // When
        String text = imageCaptioner.extractText(imageUrl);

        // Then
        assertThat(text).isEqualTo(expectedText);
        verify(visionClient, times(1)).extractText(imageUrl);
    }

    /**
     * 测试：提取文字失败
     */
    @Test
    void testExtractText_Failure() {
        // Given
        String imageUrl = "https://example.com/invalid.jpg";

        when(visionClient.extractText(imageUrl))
            .thenThrow(new RuntimeException("提取失败"));

        // When
        String text = imageCaptioner.extractText(imageUrl);

        // Then
        assertThat(text).isEmpty();
    }

    /**
     * 测试：图片问答
     */
    @Test
    void testAskAboutImage() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String question = "图中有几个人？";
        String expectedAnswer = "图中有3个人";

        when(visionClient.askAboutImage(imageUrl, question))
            .thenReturn(expectedAnswer);

        // When
        String answer = imageCaptioner.askAboutImage(imageUrl, question);

        // Then
        assertThat(answer).isEqualTo(expectedAnswer);
    }

    /**
     * 测试：图片问答失败
     */
    @Test
    void testAskAboutImage_Failure() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String question = "问题";

        when(visionClient.askAboutImage(imageUrl, question))
            .thenThrow(new RuntimeException("问答失败"));

        // When
        String answer = imageCaptioner.askAboutImage(imageUrl, question);

        // Then
        assertThat(answer).contains("失败");
    }

    /**
     * 测试：多语言描述生成
     */
    @Test
    void testGenerateCaptionInLanguage() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String language = "English";
        String expectedCaption = "A beautiful landscape photo with mountains and lakes.";

        when(visionClient.analyzeImage(eq(imageUrl), anyString()))
            .thenReturn(expectedCaption);

        // When
        String caption = imageCaptioner.generateCaptionInLanguage(imageUrl, language);

        // Then
        assertThat(caption).isEqualTo(expectedCaption);
    }

    /**
     * 测试：多语言描述生成失败
     */
    @Test
    void testGenerateCaptionInLanguage_Failure() {
        // Given
        String imageUrl = "https://example.com/image.jpg";
        String language = "French";

        when(visionClient.analyzeImage(eq(imageUrl), anyString()))
            .thenThrow(new RuntimeException("生成失败"));

        // When
        String caption = imageCaptioner.generateCaptionInLanguage(imageUrl, language);

        // Then
        assertThat(caption).contains("失败");
    }

    /**
     * 测试：简短描述失败时返回默认值
     */
    @Test
    void testGenerateShortCaption_FailureReturnsDefault() {
        // Given
        String imageUrl = "https://example.com/image.jpg";

        when(visionClient.analyzeImage(eq(imageUrl), anyString()))
            .thenThrow(new RuntimeException("失败"));

        // When
        String caption = imageCaptioner.generateShortCaption(imageUrl);

        // Then
        assertThat(caption).isEqualTo("图片");
    }

    /**
     * 测试：批量处理空列表
     */
    @Test
    void testGenerateCaptions_EmptyList() {
        // Given
        List<String> imageUrls = List.of();

        when(visionClient.analyzeImages(eq(imageUrls), anyString()))
            .thenReturn(List.of());

        // When
        List<String> captions = imageCaptioner.generateCaptions(imageUrls);

        // Then
        assertThat(captions).isEmpty();
    }
}
