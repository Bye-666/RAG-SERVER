package com.ragserver.ingestion.transformer;

import com.ragserver.ai.dashscope.DashScopeVisionClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 图片描述生成器
 *
 * <p>为图片生成文本描述，用于：</p>
 * <ul>
 *   <li>图片内容索引：将视觉信息转换为可搜索的文本</li>
 *   <li>多模态RAG：支持图文混合检索</li>
 *   <li>图片摘要：提供图片内容概览</li>
 *   <li>辅助理解：为视障用户提供图片描述</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Service
 * public class DocumentProcessor {
 *     @Autowired
 *     private ImageCaptioner captioner;
 *
 *     public void processDocument(Document doc) {
 *         for (Image image : doc.getImages()) {
 *             String caption = captioner.generateCaption(image.getUrl());
 *             image.setCaption(caption);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>接收图片URL</li>
 *   <li>调用Vision模型分析图片</li>
 *   <li>生成结构化描述</li>
 *   <li>返回文本描述</li>
 * </ol>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class ImageCaptioner {

    private final DashScopeVisionClient visionClient;

    /**
     * 默认描述提示词
     */
    private static final String DEFAULT_CAPTION_PROMPT =
        "请详细描述这张图片的内容，包括：主要对象、场景、颜色、位置关系等。用简洁的语言，不超过200字。";

    /**
     * 简短描述提示词
     */
    private static final String SHORT_CAPTION_PROMPT =
        "用一句话简要描述这张图片的主要内容，不超过50字。";

    /**
     * 详细描述提示词
     */
    private static final String DETAILED_CAPTION_PROMPT =
        "请详细描述这张图片，包括：\n" +
        "1. 主要对象和人物\n" +
        "2. 场景和环境\n" +
        "3. 颜色和光线\n" +
        "4. 构图和视角\n" +
        "5. 情绪和氛围\n" +
        "6. 文字内容（如有）";

    public ImageCaptioner(DashScopeVisionClient visionClient) {
        this.visionClient = visionClient;
        log.info("ImageCaptioner初始化完成");
    }

    /**
     * 生成图片描述（默认模式）
     *
     * <p>使用默认提示词生成中等长度的图片描述。</p>
     *
     * @param imageUrl 图片URL
     * @return 图片描述文本
     */
    public String generateCaption(String imageUrl) {
        log.info("生成图片描述：imageUrl={}", imageUrl);

        try {
            String caption = visionClient.analyzeImage(imageUrl, DEFAULT_CAPTION_PROMPT);
            log.debug("图片描述生成完成：长度={}", caption.length());
            return caption;

        } catch (Exception e) {
            log.error("图片描述生成失败：imageUrl={}, error={}", imageUrl, e.getMessage(), e);
            return "图片描述生成失败：" + e.getMessage();
        }
    }

    /**
     * 生成简短描述
     *
     * <p>生成一句话概括的简短描述，适用于：</p>
     * <ul>
     *   <li>图片标题</li>
     *   <li>缩略信息</li>
     *   <li>快速预览</li>
     * </ul>
     *
     * @param imageUrl 图片URL
     * @return 简短描述（约50字）
     */
    public String generateShortCaption(String imageUrl) {
        log.info("生成简短图片描述：imageUrl={}", imageUrl);

        try {
            return visionClient.analyzeImage(imageUrl, SHORT_CAPTION_PROMPT);
        } catch (Exception e) {
            log.error("简短描述生成失败：imageUrl={}, error={}", imageUrl, e.getMessage(), e);
            return "图片";
        }
    }

    /**
     * 生成详细描述
     *
     * <p>生成详细的结构化描述，适用于：</p>
     * <ul>
     *   <li>档案记录</li>
     *   <li>详细索引</li>
     *   <li>辅助功能</li>
     * </ul>
     *
     * @param imageUrl 图片URL
     * @return 详细描述
     */
    public String generateDetailedCaption(String imageUrl) {
        log.info("生成详细图片描述：imageUrl={}", imageUrl);

        try {
            return visionClient.analyzeImage(imageUrl, DETAILED_CAPTION_PROMPT);
        } catch (Exception e) {
            log.error("详细描述生成失败：imageUrl={}, error={}", imageUrl, e.getMessage(), e);
            return "详细描述生成失败：" + e.getMessage();
        }
    }

    /**
     * 批量生成图片描述
     *
     * <p>批量处理多张图片，适用于文档中包含多个图片的场景。</p>
     *
     * @param imageUrls 图片URL列表
     * @return 描述列表（与输入顺序对应）
     */
    public List<String> generateCaptions(List<String> imageUrls) {
        log.info("批量生成图片描述：count={}", imageUrls.size());

        return visionClient.analyzeImages(imageUrls, DEFAULT_CAPTION_PROMPT);
    }

    /**
     * 生成带元数据的描述
     *
     * <p>生成包含结构化元数据的描述结果。</p>
     *
     * @param imageUrl 图片URL
     * @return 包含描述和元数据的Map
     */
    public Map<String, Object> generateCaptionWithMetadata(String imageUrl) {
        log.info("生成带元数据的图片描述：imageUrl={}", imageUrl);

        try {
            // 直接调用visionClient，让异常向上抛出
            String caption = visionClient.analyzeImage(imageUrl, DEFAULT_CAPTION_PROMPT);
            String shortCaption = visionClient.analyzeImage(imageUrl, SHORT_CAPTION_PROMPT);

            return Map.of(
                "imageUrl", imageUrl,
                "caption", caption,
                "shortCaption", shortCaption,
                "captionLength", caption.length(),
                "timestamp", System.currentTimeMillis(),
                "status", "SUCCESS"
            );

        } catch (Exception e) {
            log.error("带元数据的描述生成失败：imageUrl={}, error={}", imageUrl, e.getMessage(), e);
            return Map.of(
                "imageUrl", imageUrl,
                "caption", "",
                "shortCaption", "",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis(),
                "status", "FAILED"
            );
        }
    }

    /**
     * 提取图片中的文字
     *
     * <p>使用OCR功能提取图片中的文字内容。</p>
     *
     * @param imageUrl 图片URL
     * @return 提取的文字
     */
    public String extractText(String imageUrl) {
        log.info("提取图片文字：imageUrl={}", imageUrl);

        try {
            return visionClient.extractText(imageUrl);
        } catch (Exception e) {
            log.error("文字提取失败：imageUrl={}, error={}", imageUrl, e.getMessage(), e);
            return "";
        }
    }

    /**
     * 图片问答
     *
     * <p>针对图片内容提问并获取答案。</p>
     *
     * @param imageUrl 图片URL
     * @param question 问题
     * @return 答案
     */
    public String askAboutImage(String imageUrl, String question) {
        log.info("图片问答：imageUrl={}, question={}", imageUrl, question);

        try {
            return visionClient.askAboutImage(imageUrl, question);
        } catch (Exception e) {
            log.error("图片问答失败：imageUrl={}, error={}", imageUrl, e.getMessage(), e);
            return "问答失败：" + e.getMessage();
        }
    }

    /**
     * 生成多语言描述
     *
     * <p>生成指定语言的图片描述。</p>
     *
     * @param imageUrl 图片URL
     * @param language 语言（如：中文、English、日本語）
     * @return 指定语言的描述
     */
    public String generateCaptionInLanguage(String imageUrl, String language) {
        log.info("生成多语言图片描述：imageUrl={}, language={}", imageUrl, language);

        String prompt = String.format("请用%s详细描述这张图片的内容。", language);

        try {
            return visionClient.analyzeImage(imageUrl, prompt);
        } catch (Exception e) {
            log.error("多语言描述生成失败：imageUrl={}, language={}, error={}",
                imageUrl, language, e.getMessage(), e);
            return "描述生成失败：" + e.getMessage();
        }
    }
}
