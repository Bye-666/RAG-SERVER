package com.ragserver.retrieval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 检索文档模型
 *
 * <p>表示从Milvus检索到的文档，包含：</p>
 * <ul>
 *   <li>文档ID</li>
 *   <li>原始文本</li>
 *   <li>Dense向量（可选）</li>
 *   <li>Sparse向量（可选）</li>
 *   <li>元数据</li>
 *   <li>相似度分数</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * Document doc = Document.builder()
 *     .id("doc_001")
 *     .text("这是一篇关于AI的文章")
 *     .metadata(Map.of("source", "arxiv", "page", 1))
 *     .score(0.95f)
 *     .build();
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    /**
     * 文档唯一标识
     *
     * <p>格式建议：{source}_{id}，例如：arxiv_2301.12345</p>
     */
    private String id;

    /**
     * 原始文本内容
     *
     * <p>最大长度：65535字符（Milvus VARCHAR限制）</p>
     */
    private String text;

    /**
     * Dense向量（语义向量）
     *
     * <p>维度：2048（DashScope text-embedding-v4）</p>
     *
     * <p>可选字段，检索时不一定返回向量本身。</p>
     */
    private List<Float> denseVector;

    /**
     * Sparse向量（关键词向量）
     *
     * <p>稀疏向量格式：{index: value}</p>
     *
     * <p>可选字段，用于BM25关键词检索。</p>
     */
    private Map<Integer, Float> sparseVector;

    /**
     * 元数据
     *
     * <p>存储文档的附加信息，例如：</p>
     * <ul>
     *   <li>source：数据源（arxiv、wikipedia等）</li>
     *   <li>page：页码</li>
     *   <li>title：标题</li>
     *   <li>author：作者</li>
     *   <li>timestamp：时间戳</li>
     * </ul>
     */
    private Map<String, Object> metadata;

    /**
     * 相似度分数
     *
     * <p>检索时返回的相似度得分，范围：</p>
     * <ul>
     *   <li>COSINE：[-1, 1]，越接近1越相似</li>
     *   <li>L2：[0, +∞)，越小越相似</li>
     *   <li>IP：(-∞, +∞)，越大越相似</li>
     * </ul>
     */
    private Float score;

    /**
     * 获取元数据字段
     *
     * @param key 字段名
     * @return 字段值，不存在返回null
     */
    public Object getMetadataField(String key) {
        if (metadata == null) {
            return null;
        }
        return metadata.get(key);
    }

    /**
     * 设置元数据字段
     *
     * @param key 字段名
     * @param value 字段值
     */
    public void setMetadataField(String key, Object value) {
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
        metadata.put(key, value);
    }

    /**
     * 获取文档摘要（前100字符）
     *
     * @return 文档摘要
     */
    public String getSummary() {
        if (text == null) {
            return "";
        }
        if (text.length() <= 100) {
            return text;
        }
        return text.substring(0, 100) + "...";
    }
}
