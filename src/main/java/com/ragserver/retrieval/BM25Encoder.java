package com.ragserver.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BM25编码器
 *
 * <p>将文本转换为稀疏向量（Sparse Vector），用于关键词检索。</p>
 *
 * <h3>BM25算法</h3>
 * <p>BM25（Best Matching 25）是一种基于概率的信息检索算法，计算文档与查询的相关度。</p>
 *
 * <h4>公式</h4>
 * <pre>
 * score(D,Q) = Σ IDF(qi) * (f(qi,D) * (k1 + 1)) / (f(qi,D) + k1 * (1 - b + b * |D| / avgdl))
 *
 * 其中：
 * - D: 文档
 * - Q: 查询
 * - qi: 查询中的第i个词
 * - f(qi,D): qi在文档D中的词频
 * - |D|: 文档D的长度
 * - avgdl: 平均文档长度
 * - k1: 调节词频饱和度（默认1.5）
 * - b: 调节文档长度归一化（默认0.75）
 * - IDF(qi): 逆文档频率
 * </pre>
 *
 * <h3>稀疏向量格式</h3>
 * <p>稀疏向量表示为 Map&lt;Integer, Float&gt;，其中：</p>
 * <ul>
 *   <li>Key：词的ID（词表索引）</li>
 *   <li>Value：BM25分数</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * BM25Encoder encoder = new BM25Encoder();
 *
 * // 编码查询
 * Map<Integer, Float> sparseVector = encoder.encode("搜索引擎优化");
 * // 结果：{101: 2.5, 203: 1.8, 405: 3.2}
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class BM25Encoder {

    /**
     * BM25参数k1：调节词频饱和度
     *
     * <p>k1越大，词频的影响越大。</p>
     * <p>典型值：1.2 - 2.0，默认1.5</p>
     */
    private static final double K1 = 1.5;

    /**
     * BM25参数b：调节文档长度归一化
     *
     * <p>b=0表示不考虑文档长度，b=1表示完全归一化。</p>
     * <p>典型值：0.75</p>
     */
    private static final double B = 0.75;

    /**
     * 词表：词 -> ID
     *
     * <p>动态构建，第一次出现的词分配新ID。</p>
     */
    private final Map<String, Integer> vocabulary = new HashMap<>();

    /**
     * 词频统计：词ID -> 文档频率
     *
     * <p>记录每个词在多少个文档中出现过。</p>
     */
    private final Map<Integer, Integer> documentFrequency = new HashMap<>();

    /**
     * 文档总数
     */
    private int totalDocuments = 0;

    /**
     * 所有文档的总长度（用于计算平均文档长度）
     */
    private long totalDocumentLength = 0;

    /**
     * 下一个可用的词ID
     */
    private int nextTermId = 1;

    /**
     * 编码文本为稀疏向量
     *
     * <p>将输入文本分词后，计算每个词的BM25权重。</p>
     *
     * @param text 输入文本
     * @return 稀疏向量（词ID -> BM25分数）
     */
    public Map<Integer, Float> encode(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 分词
        List<String> tokens = tokenize(text);

        // 2. 计算词频
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String token : tokens) {
            termFrequency.merge(token, 1, Integer::sum);
        }

        // 3. 转换为稀疏向量
        Map<Integer, Float> sparseVector = new HashMap<>();
        double avgDocLength = totalDocuments > 0 ? (double) totalDocumentLength / totalDocuments : 0;
        int docLength = tokens.size();

        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();

            // 获取或分配词ID
            int termId = getOrCreateTermId(term);

            // 计算IDF
            int df = documentFrequency.getOrDefault(termId, 1);
            double idf = Math.log((totalDocuments - df + 0.5) / (df + 0.5) + 1.0);

            // 计算BM25分数
            double k1Factor = K1 * (1 - B + B * docLength / (avgDocLength + 1.0));
            double bm25Score = idf * (tf * (K1 + 1)) / (tf + k1Factor);

            sparseVector.put(termId, (float) bm25Score);
        }

        log.debug("编码文本：{}词 -> {}维稀疏向量", tokens.size(), sparseVector.size());

        return sparseVector;
    }

    /**
     * 更新文档统计信息
     *
     * <p>用于训练BM25模型，更新文档频率等统计信息。</p>
     *
     * @param text 文档文本
     */
    public void fit(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        // 分词
        List<String> tokens = tokenize(text);

        // 更新统计信息
        totalDocuments++;
        totalDocumentLength += tokens.size();

        // 更新文档频率（每个词在这个文档中只计数一次）
        Set<String> uniqueTerms = new HashSet<>(tokens);
        for (String term : uniqueTerms) {
            int termId = getOrCreateTermId(term);
            documentFrequency.merge(termId, 1, Integer::sum);
        }

        log.debug("更新统计：文档数={}, 词表大小={}", totalDocuments, vocabulary.size());
    }

    /**
     * 批量训练
     *
     * @param texts 文档列表
     */
    public void fitBatch(List<String> texts) {
        for (String text : texts) {
            fit(text);
        }
        log.info("批量训练完成：{}个文档，词表大小={}", texts.size(), vocabulary.size());
    }

    /**
     * 简单分词器
     *
     * <p>将文本分词为单个字符（适合中文）。</p>
     *
     * <p>生产环境建议使用专业分词器：</p>
     * <ul>
     *   <li>HanLP</li>
     *   <li>IK Analyzer</li>
     *   <li>jieba</li>
     * </ul>
     *
     * @param text 输入文本
     * @return 分词结果
     */
    private List<String> tokenize(String text) {
        // 移除标点符号和特殊字符
        String cleaned = text.replaceAll("[\\p{Punct}\\s]+", "");

        // 按字符分词（简单实现）
        List<String> tokens = new ArrayList<>();
        for (char c : cleaned.toCharArray()) {
            String token = String.valueOf(c);
            // 过滤停用词（简单示例）
            if (!isStopWord(token)) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    /**
     * 判断是否为停用词
     *
     * <p>简单实现，生产环境应使用完整停用词表。</p>
     *
     * @param word 词
     * @return 是否为停用词
     */
    private boolean isStopWord(String word) {
        // 简单示例：常见的中文停用词
        Set<String> stopWords = Set.of("的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这");
        return stopWords.contains(word);
    }

    /**
     * 获取或创建词ID
     *
     * @param term 词
     * @return 词ID
     */
    private synchronized int getOrCreateTermId(String term) {
        return vocabulary.computeIfAbsent(term, k -> nextTermId++);
    }

    /**
     * 获取词表大小
     *
     * @return 词表大小
     */
    public int getVocabularySize() {
        return vocabulary.size();
    }

    /**
     * 获取文档总数
     *
     * @return 文档总数
     */
    public int getTotalDocuments() {
        return totalDocuments;
    }

    /**
     * 清空统计信息
     */
    public void clear() {
        vocabulary.clear();
        documentFrequency.clear();
        totalDocuments = 0;
        totalDocumentLength = 0;
        nextTermId = 1;
        log.info("BM25编码器已清空");
    }
}
