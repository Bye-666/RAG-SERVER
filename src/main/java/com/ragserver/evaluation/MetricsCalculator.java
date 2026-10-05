package com.ragserver.evaluation;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * 评估指标计算器
 *
 * <p>提供RAG系统的核心评估指标计算：</p>
 * <ul>
 *   <li>Hit Rate (HR): 命中率，检索结果中包含正确答案的比例</li>
 *   <li>MRR (Mean Reciprocal Rank): 平均倒数排名，衡量正确答案的平均位置</li>
 *   <li>NDCG (Normalized Discounted Cumulative Gain): 归一化折损累积增益</li>
 *   <li>Precision@K: 前K个结果中的准确率</li>
 *   <li>Recall@K: 前K个结果中的召回率</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * List<String> retrieved = List.of("doc1", "doc2", "doc3");
 * List<String> expected = List.of("doc2", "doc4");
 *
 * double hitRate = MetricsCalculator.calculateHitRate(retrieved, expected);
 * double mrr = MetricsCalculator.calculateMRR(retrieved, expected);
 * double ndcg = MetricsCalculator.calculateNDCG(retrieved, expected, 3);
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
public class MetricsCalculator {

    /**
     * 计算Hit Rate（命中率）
     *
     * <p>公式：HR@K = (检索到的相关文档数量 > 0) ? 1 : 0</p>
     *
     * <p>示例：</p>
     * <ul>
     *   <li>检索结果：[doc1, doc2, doc3]</li>
     *   <li>期望结果：[doc2, doc4]</li>
     *   <li>HR = 1（因为检索到了doc2）</li>
     * </ul>
     *
     * @param retrievedIds 检索返回的文档ID列表
     * @param expectedIds 期望的文档ID列表
     * @return Hit Rate (0.0 或 1.0)
     */
    public static double calculateHitRate(List<String> retrievedIds, List<String> expectedIds) {
        if (retrievedIds == null || retrievedIds.isEmpty() || expectedIds == null || expectedIds.isEmpty()) {
            return 0.0;
        }

        Set<String> expectedSet = Set.copyOf(expectedIds);

        for (String retrievedId : retrievedIds) {
            if (expectedSet.contains(retrievedId)) {
                return 1.0;
            }
        }

        return 0.0;
    }

    /**
     * 计算MRR（平均倒数排名）
     *
     * <p>公式：MRR = 1 / rank（第一个相关文档的位置）</p>
     *
     * <p>示例：</p>
     * <ul>
     *   <li>检索结果：[doc1, doc2, doc3]</li>
     *   <li>期望结果：[doc2]</li>
     *   <li>MRR = 1/2 = 0.5（doc2在第2位）</li>
     * </ul>
     *
     * @param retrievedIds 检索返回的文档ID列表
     * @param expectedIds 期望的文档ID列表
     * @return MRR值（0.0 到 1.0）
     */
    public static double calculateMRR(List<String> retrievedIds, List<String> expectedIds) {
        if (retrievedIds == null || retrievedIds.isEmpty() || expectedIds == null || expectedIds.isEmpty()) {
            return 0.0;
        }

        Set<String> expectedSet = Set.copyOf(expectedIds);

        for (int i = 0; i < retrievedIds.size(); i++) {
            if (expectedSet.contains(retrievedIds.get(i))) {
                return 1.0 / (i + 1);
            }
        }

        return 0.0;
    }

    /**
     * 计算NDCG（归一化折损累积增益）
     *
     * <p>NDCG考虑了文档的排序位置，位置越靠前权重越高。</p>
     *
     * <p>公式：</p>
     * <pre>
     * DCG@K = Σ(rel_i / log2(i + 1))
     * NDCG@K = DCG@K / IDCG@K
     * </pre>
     *
     * <p>其中：</p>
     * <ul>
     *   <li>rel_i: 第i个位置的相关性（1=相关，0=不相关）</li>
     *   <li>IDCG: 理想情况下的DCG（所有相关文档都排在前面）</li>
     * </ul>
     *
     * @param retrievedIds 检索返回的文档ID列表
     * @param expectedIds 期望的文档ID列表
     * @param k 评估的前K个结果
     * @return NDCG值（0.0 到 1.0）
     */
    public static double calculateNDCG(List<String> retrievedIds, List<String> expectedIds, int k) {
        if (retrievedIds == null || retrievedIds.isEmpty() || expectedIds == null || expectedIds.isEmpty() || k <= 0) {
            return 0.0;
        }

        Set<String> expectedSet = Set.copyOf(expectedIds);

        // 计算DCG@K
        double dcg = 0.0;
        int limit = Math.min(k, retrievedIds.size());
        for (int i = 0; i < limit; i++) {
            if (expectedSet.contains(retrievedIds.get(i))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2)); // log2(i+2)
            }
        }

        // 计算IDCG@K（理想情况：所有相关文档都在前面）
        double idcg = 0.0;
        int relevantCount = Math.min(k, expectedIds.size());
        for (int i = 0; i < relevantCount; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }

        if (idcg == 0.0) {
            return 0.0;
        }

        return dcg / idcg;
    }

    /**
     * 计算Precision@K（准确率）
     *
     * <p>公式：Precision@K = 检索到的相关文档数 / K</p>
     *
     * <p>示例：</p>
     * <ul>
     *   <li>检索结果：[doc1, doc2, doc3, doc4, doc5]</li>
     *   <li>期望结果：[doc2, doc4]</li>
     *   <li>Precision@5 = 2/5 = 0.4</li>
     * </ul>
     *
     * @param retrievedIds 检索返回的文档ID列表
     * @param expectedIds 期望的文档ID列表
     * @param k 评估的前K个结果
     * @return Precision@K值（0.0 到 1.0）
     */
    public static double calculatePrecisionAtK(List<String> retrievedIds, List<String> expectedIds, int k) {
        if (retrievedIds == null || retrievedIds.isEmpty() || expectedIds == null || expectedIds.isEmpty() || k <= 0) {
            return 0.0;
        }

        Set<String> expectedSet = Set.copyOf(expectedIds);

        int hitCount = 0;
        int limit = Math.min(k, retrievedIds.size());

        for (int i = 0; i < limit; i++) {
            if (expectedSet.contains(retrievedIds.get(i))) {
                hitCount++;
            }
        }

        return (double) hitCount / k;
    }

    /**
     * 计算Recall@K（召回率）
     *
     * <p>公式：Recall@K = 检索到的相关文档数 / 总相关文档数</p>
     *
     * <p>示例：</p>
     * <ul>
     *   <li>检索结果：[doc1, doc2, doc3]</li>
     *   <li>期望结果：[doc2, doc4, doc5]</li>
     *   <li>Recall@3 = 1/3 = 0.333（只检索到doc2）</li>
     * </ul>
     *
     * @param retrievedIds 检索返回的文档ID列表
     * @param expectedIds 期望的文档ID列表
     * @param k 评估的前K个结果
     * @return Recall@K值（0.0 到 1.0）
     */
    public static double calculateRecallAtK(List<String> retrievedIds, List<String> expectedIds, int k) {
        if (retrievedIds == null || retrievedIds.isEmpty() || expectedIds == null || expectedIds.isEmpty() || k <= 0) {
            return 0.0;
        }

        Set<String> expectedSet = Set.copyOf(expectedIds);

        int hitCount = 0;
        int limit = Math.min(k, retrievedIds.size());

        for (int i = 0; i < limit; i++) {
            if (expectedSet.contains(retrievedIds.get(i))) {
                hitCount++;
            }
        }

        return (double) hitCount / expectedSet.size();
    }

    /**
     * 计算F1分数
     *
     * <p>F1 = 2 * (Precision * Recall) / (Precision + Recall)</p>
     *
     * @param precision 准确率
     * @param recall 召回率
     * @return F1分数（0.0 到 1.0）
     */
    public static double calculateF1(double precision, double recall) {
        if (precision + recall == 0.0) {
            return 0.0;
        }
        return 2.0 * (precision * recall) / (precision + recall);
    }
}
