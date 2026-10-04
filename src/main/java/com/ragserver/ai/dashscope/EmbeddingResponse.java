package com.ragserver.ai.dashscope;

import lombok.Data;
import java.util.List;

/**
 * DashScope Embedding API 响应对象
 *
 * <p>封装DashScope兼容模式Embedding API的响应数据。</p>
 *
 * <h3>响应示例</h3>
 * <pre>
 * {
 *   "object": "list",
 *   "data": [
 *     {
 *       "object": "embedding",
 *       "index": 0,
 *       "embedding": [0.123, -0.456, 0.789, ...]
 *     }
 *   ],
 *   "model": "text-embedding-v4",
 *   "usage": {
 *     "prompt_tokens": 10,
 *     "total_tokens": 10
 *   }
 * }
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
public class EmbeddingResponse {

    /**
     * 对象类型（固定为"list"）
     */
    private String object;

    /**
     * Embedding结果列表
     *
     * <p>每个输入文本对应一个EmbeddingData。</p>
     */
    private List<EmbeddingData> data;

    /**
     * 使用的模型名称
     */
    private String model;

    /**
     * Token使用统计
     */
    private Usage usage;

    /**
     * Embedding数据
     */
    @Data
    public static class EmbeddingData {
        /**
         * 对象类型（固定为"embedding"）
         */
        private String object;

        /**
         * 索引（对应input数组的索引）
         */
        private Integer index;

        /**
         * Embedding向量（2048维）
         */
        private List<Double> embedding;
    }

    /**
     * Token使用统计
     */
    @Data
    public static class Usage {
        /**
         * 输入token数
         */
        private Integer promptTokens;

        /**
         * 总token数（Embedding API中 = prompt_tokens）
         */
        private Integer totalTokens;
    }

    /**
     * 获取第一个Embedding向量
     *
     * <p>快捷方法，适合单条文本场景。</p>
     *
     * @return Embedding向量，如果没有结果则返回null
     */
    public List<Double> getEmbedding() {
        if (data != null && !data.isEmpty()) {
            return data.get(0).getEmbedding();
        }
        return null;
    }

    /**
     * 获取所有Embedding向量
     *
     * <p>按索引顺序返回所有向量。</p>
     *
     * @return Embedding向量列表
     */
    public List<List<Double>> getAllEmbeddings() {
        if (data == null) {
            return List.of();
        }
        return data.stream()
            .sorted((a, b) -> Integer.compare(a.getIndex(), b.getIndex()))
            .map(EmbeddingData::getEmbedding)
            .toList();
    }

    /**
     * 检查是否成功生成
     *
     * @return 是否有有效的Embedding结果
     */
    public boolean isSuccess() {
        return data != null && !data.isEmpty() && getEmbedding() != null;
    }

    /**
     * 获取向量维度
     *
     * @return 向量维度，如果没有结果则返回0
     */
    public int getDimension() {
        List<Double> embedding = getEmbedding();
        return embedding != null ? embedding.size() : 0;
    }
}
