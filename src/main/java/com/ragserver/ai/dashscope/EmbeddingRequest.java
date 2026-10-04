package com.ragserver.ai.dashscope;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * DashScope Embedding API 请求对象
 *
 * <p>封装DashScope兼容模式Embedding API的请求参数。</p>
 *
 * <h3>API文档</h3>
 * <pre>
 * POST https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings
 * Authorization: Bearer {api-key}
 * Content-Type: application/json
 *
 * {
 *   "model": "text-embedding-v4",
 *   "input": ["文本1", "文本2"],
 *   "encoding_format": "float"
 * }
 * </pre>
 *
 * <h3>特点</h3>
 * <ul>
 *   <li>支持批量处理：最多16条文本</li>
 *   <li>输出2048维向量</li>
 *   <li>最大输入长度：2048 tokens</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
public class EmbeddingRequest {

    /**
     * 模型名称
     *
     * <p>默认值：text-embedding-v4</p>
     *
     * <p>DashScope Embedding模型：</p>
     * <ul>
     *   <li>text-embedding-v4：2048维，支持中英文</li>
     * </ul>
     */
    private String model;

    /**
     * 输入文本
     *
     * <p>可以是单个字符串或字符串数组。</p>
     *
     * <p>批量限制：</p>
     * <ul>
     *   <li>最多16条文本</li>
     *   <li>每条最大2048 tokens</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>
     * // 单条文本
     * request.setInput(List.of("这是一段测试文本"));
     *
     * // 批量文本
     * request.setInput(List.of("文本1", "文本2", "文本3"));
     * </pre>
     */
    private List<String> input;

    /**
     * 编码格式
     *
     * <p>可选值：</p>
     * <ul>
     *   <li>float：浮点数数组（默认）</li>
     *   <li>base64：Base64编码字符串</li>
     * </ul>
     *
     * <p>推荐使用float格式，便于直接使用。</p>
     */
    @JsonProperty("encoding_format")
    private String encodingFormat = "float";

    /**
     * 创建单条文本的请求
     *
     * @param model 模型名称
     * @param text 文本内容
     * @return EmbeddingRequest实例
     */
    public static EmbeddingRequest single(String model, String text) {
        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel(model);
        request.setInput(List.of(text));
        return request;
    }

    /**
     * 创建批量文本的请求
     *
     * @param model 模型名称
     * @param texts 文本列表（最多16条）
     * @return EmbeddingRequest实例
     */
    public static EmbeddingRequest batch(String model, List<String> texts) {
        if (texts.size() > 16) {
            throw new IllegalArgumentException("批量Embedding最多支持16条文本，当前：" + texts.size());
        }
        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel(model);
        request.setInput(texts);
        return request;
    }
}
