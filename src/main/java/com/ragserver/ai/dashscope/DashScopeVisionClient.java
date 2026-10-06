package com.ragserver.ai.dashscope;

import com.ragserver.config.DashScopeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * DashScope Vision客户端
 *
 * <p>封装DashScope Vision API调用，提供图片理解能力：</p>
 * <ul>
 *   <li>图片描述生成</li>
 *   <li>图片内容问答</li>
 *   <li>OCR文字识别</li>
 *   <li>多模态理解</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Service
 * public class ImageService {
 *     @Autowired
 *     private DashScopeVisionClient visionClient;
 *
 *     public String describeImage(String imageUrl) {
 *         return visionClient.analyzeImage(imageUrl, "描述这张图片");
 *     }
 * }
 * }</pre>
 *
 * <h3>支持的模型</h3>
 * <ul>
 *   <li>qwen-vl-max - 最强视觉理解能力</li>
 *   <li>qwen-vl-plus - 平衡性能和成本</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class DashScopeVisionClient {

    private final DashScopeProperties properties;
    private final RestTemplate restTemplate;
    private final RateLimiter rateLimiter;

    /**
     * Vision模型名称
     */
    private static final String VISION_MODEL = "qwen-vl-max";

    /**
     * 默认提示词
     */
    private static final String DEFAULT_PROMPT = "请详细描述这张图片的内容";

    public DashScopeVisionClient(DashScopeProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.rateLimiter = new RateLimiter(properties.getQps());
        log.info("DashScopeVisionClient初始化完成，模型：{}", VISION_MODEL);
    }

    /**
     * 分析图片
     *
     * <p>使用Vision模型分析图片内容并生成描述。</p>
     *
     * <h3>工作流程</h3>
     * <ol>
     *   <li>构建多模态消息（文本+图片）</li>
     *   <li>调用qwen-vl-max模型</li>
     *   <li>解析并返回描述文本</li>
     * </ol>
     *
     * <h3>图片URL要求</h3>
     * <ul>
     *   <li>支持HTTP/HTTPS URL</li>
     *   <li>支持常见图片格式（JPG、PNG、GIF等）</li>
     *   <li>图片大小建议不超过10MB</li>
     * </ul>
     *
     * @param imageUrl 图片URL
     * @param prompt 提示词（可选，默认"请详细描述这张图片的内容"）
     * @return 图片描述文本
     * @throws DashScopeException 调用失败时抛出
     */
    public String analyzeImage(String imageUrl, String prompt) {
        log.info("开始分析图片：imageUrl={}, prompt={}", imageUrl, prompt);

        // 参数校验
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("图片URL不能为空");
        }

        if (prompt == null || prompt.trim().isEmpty()) {
            prompt = DEFAULT_PROMPT;
        }

        // QPS限流
        rateLimiter.acquire();

        try {
            // 构建Vision请求
            VisionRequest request = buildVisionRequest(imageUrl, prompt);

            // 调用API
            ChatResponse response = call(request);

            // 提取描述文本
            String description = response.getChoices().get(0).getMessage().getContent();
            log.info("图片分析完成，描述长度：{}", description.length());

            return description;

        } catch (Exception e) {
            log.error("图片分析失败：imageUrl={}, error={}", imageUrl, e.getMessage(), e);
            throw new RuntimeException("图片分析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 分析图片（使用默认提示词）
     *
     * @param imageUrl 图片URL
     * @return 图片描述文本
     */
    public String analyzeImage(String imageUrl) {
        return analyzeImage(imageUrl, DEFAULT_PROMPT);
    }

    /**
     * 批量分析图片
     *
     * <p>批量处理多张图片，每张图片独立分析。</p>
     *
     * @param imageUrls 图片URL列表
     * @param prompt 提示词
     * @return 描述文本列表
     */
    public List<String> analyzeImages(List<String> imageUrls, String prompt) {
        log.info("批量分析图片：count={}", imageUrls.size());

        return imageUrls.stream()
                .map(url -> {
                    try {
                        return analyzeImage(url, prompt);
                    } catch (Exception e) {
                        log.warn("图片分析失败：url={}, error={}", url, e.getMessage());
                        return "分析失败：" + e.getMessage();
                    }
                })
                .toList();
    }

    /**
     * 图片问答
     *
     * <p>针对图片内容提问并获取答案。</p>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>图片内容问答："图中有几个人？"</li>
     *   <li>文字识别："提取图中的文字"</li>
     *   <li>细节分析："图中的物体是什么颜色？"</li>
     * </ul>
     *
     * @param imageUrl 图片URL
     * @param question 问题
     * @return 答案文本
     */
    public String askAboutImage(String imageUrl, String question) {
        log.info("图片问答：imageUrl={}, question={}", imageUrl, question);
        return analyzeImage(imageUrl, question);
    }

    /**
     * OCR文字识别
     *
     * <p>从图片中提取文字内容。</p>
     *
     * @param imageUrl 图片URL
     * @return 识别的文字
     */
    public String extractText(String imageUrl) {
        log.info("OCR文字识别：imageUrl={}", imageUrl);
        return analyzeImage(imageUrl, "提取图片中的所有文字，保持原有格式");
    }

    /**
     * 构建Vision请求
     *
     * <p>构建包含图片和文本的多模态消息。</p>
     *
     * @param imageUrl 图片URL
     * @param prompt 提示词
     * @return Vision请求对象
     */
    private VisionRequest buildVisionRequest(String imageUrl, String prompt) {
        VisionRequest request = new VisionRequest();
        request.setModel(VISION_MODEL);

        // 构建多模态消息
        VisionRequest.VisionMessage message = new VisionRequest.VisionMessage();
        message.setRole("user");

        // 消息内容包含文本和图片
        message.setContent(List.of(
            Map.of("type", "text", "text", prompt),
            Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
        ));

        request.setMessages(List.of(message));

        return request;
    }

    /**
     * 调用Vision API
     *
     * @param request Vision请求
     * @return 响应
     */
    private ChatResponse call(VisionRequest request) {
        // 构建HTTP请求
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 构建请求体
        Map<String, Object> body = Map.of(
            "model", request.getModel(),
            "input", Map.of("messages", request.getMessages())
        );

        HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(body, headers);

        try {
            // 发送请求
            ResponseEntity<Map> response = restTemplate.postForEntity(
                properties.getBaseUrl() + "/chat/completions",
                httpRequest,
                Map.class
            );

            // 检查响应
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("API调用失败：HTTP " + response.getStatusCode());
            }

            // 解析响应
            return parseResponse(response.getBody());

        } catch (Exception e) {
            log.error("Vision API调用失败", e);
            throw new RuntimeException("Vision API调用失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解析响应
     *
     * @param responseBody 响应体
     * @return ChatResponse
     */
    private ChatResponse parseResponse(Map<String, Object> responseBody) {
        try {
            Map<String, Object> output = (Map<String, Object>) responseBody.get("output");
            List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");

            ChatResponse response = new ChatResponse();
            response.setChoices(
                choices.stream()
                    .map(choice -> {
                        Map<String, Object> message = (Map<String, Object>) choice.get("message");
                        ChatResponse.Choice c = new ChatResponse.Choice();
                        ChatRequest.Message m = new ChatRequest.Message();
                        m.setRole((String) message.get("role"));
                        m.setContent((String) message.get("content"));
                        c.setMessage(m);
                        return c;
                    })
                    .toList()
            );

            return response;

        } catch (Exception e) {
            log.error("解析响应失败", e);
            throw new RuntimeException("解析响应失败：" + e.getMessage(), e);
        }
    }

    /**
     * Vision请求类
     */
    public static class VisionRequest {
        private String model;
        private List<VisionMessage> messages;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<VisionMessage> getMessages() {
            return messages;
        }

        public void setMessages(List<VisionMessage> messages) {
            this.messages = messages;
        }

        /**
         * 多模态消息
         */
        public static class VisionMessage {
            private String role;
            private List<Map<String, Object>> content;

            public String getRole() {
                return role;
            }

            public void setRole(String role) {
                this.role = role;
            }

            public List<Map<String, Object>> getContent() {
                return content;
            }

            public void setContent(List<Map<String, Object>> content) {
                this.content = content;
            }
        }
    }
}
