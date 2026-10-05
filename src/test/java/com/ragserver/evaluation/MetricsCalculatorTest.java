package com.ragserver.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * MetricsCalculator 单元测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
class MetricsCalculatorTest {

    /**
     * 测试：Hit Rate - 命中
     */
    @Test
    void testCalculateHitRate_Hit() {
        // Given: 检索结果包含期望文档
        List<String> retrieved = List.of("doc1", "doc2", "doc3");
        List<String> expected = List.of("doc2", "doc4");

        // When: 计算Hit Rate
        double hitRate = MetricsCalculator.calculateHitRate(retrieved, expected);

        // Then: 命中率为1
        assertThat(hitRate).isEqualTo(1.0);
    }

    /**
     * 测试：Hit Rate - 未命中
     */
    @Test
    void testCalculateHitRate_Miss() {
        // Given: 检索结果不包含期望文档
        List<String> retrieved = List.of("doc1", "doc3", "doc5");
        List<String> expected = List.of("doc2", "doc4");

        // When: 计算Hit Rate
        double hitRate = MetricsCalculator.calculateHitRate(retrieved, expected);

        // Then: 命中率为0
        assertThat(hitRate).isEqualTo(0.0);
    }

    /**
     * 测试：MRR - 第一位命中
     */
    @Test
    void testCalculateMRR_FirstPosition() {
        // Given: 第一个结果就是期望文档
        List<String> retrieved = List.of("doc2", "doc1", "doc3");
        List<String> expected = List.of("doc2");

        // When: 计算MRR
        double mrr = MetricsCalculator.calculateMRR(retrieved, expected);

        // Then: MRR = 1/1 = 1.0
        assertThat(mrr).isEqualTo(1.0);
    }

    /**
     * 测试：MRR - 第二位命中
     */
    @Test
    void testCalculateMRR_SecondPosition() {
        // Given: 第二个结果是期望文档
        List<String> retrieved = List.of("doc1", "doc2", "doc3");
        List<String> expected = List.of("doc2");

        // When: 计算MRR
        double mrr = MetricsCalculator.calculateMRR(retrieved, expected);

        // Then: MRR = 1/2 = 0.5
        assertThat(mrr).isEqualTo(0.5);
    }

    /**
     * 测试：MRR - 未命中
     */
    @Test
    void testCalculateMRR_Miss() {
        // Given: 没有命中
        List<String> retrieved = List.of("doc1", "doc3", "doc5");
        List<String> expected = List.of("doc2");

        // When: 计算MRR
        double mrr = MetricsCalculator.calculateMRR(retrieved, expected);

        // Then: MRR = 0
        assertThat(mrr).isEqualTo(0.0);
    }

    /**
     * 测试：NDCG - 完美排序
     */
    @Test
    void testCalculateNDCG_PerfectRanking() {
        // Given: 所有相关文档都在前面
        List<String> retrieved = List.of("doc2", "doc4", "doc1", "doc3");
        List<String> expected = List.of("doc2", "doc4");

        // When: 计算NDCG@3
        double ndcg = MetricsCalculator.calculateNDCG(retrieved, expected, 3);

        // Then: NDCG = 1.0（完美排序）
        assertThat(ndcg).isEqualTo(1.0);
    }

    /**
     * 测试：NDCG - 部分命中
     */
    @Test
    void testCalculateNDCG_PartialRanking() {
        // Given: 只有部分相关文档在前面
        List<String> retrieved = List.of("doc1", "doc2", "doc3");
        List<String> expected = List.of("doc2", "doc4");

        // When: 计算NDCG@3
        double ndcg = MetricsCalculator.calculateNDCG(retrieved, expected, 3);

        // Then: NDCG > 0 但 < 1
        assertThat(ndcg).isGreaterThan(0.0).isLessThan(1.0);
    }

    /**
     * 测试：NDCG - 无命中
     */
    @Test
    void testCalculateNDCG_NoHit() {
        // Given: 没有相关文档
        List<String> retrieved = List.of("doc1", "doc3", "doc5");
        List<String> expected = List.of("doc2", "doc4");

        // When: 计算NDCG@3
        double ndcg = MetricsCalculator.calculateNDCG(retrieved, expected, 3);

        // Then: NDCG = 0
        assertThat(ndcg).isEqualTo(0.0);
    }

    /**
     * 测试：Precision@K
     */
    @Test
    void testCalculatePrecisionAtK() {
        // Given: 前5个结果中有2个相关
        List<String> retrieved = List.of("doc1", "doc2", "doc3", "doc4", "doc5");
        List<String> expected = List.of("doc2", "doc4");

        // When: 计算Precision@5
        double precision = MetricsCalculator.calculatePrecisionAtK(retrieved, expected, 5);

        // Then: Precision = 2/5 = 0.4
        assertThat(precision).isEqualTo(0.4);
    }

    /**
     * 测试：Recall@K
     */
    @Test
    void testCalculateRecallAtK() {
        // Given: 总共3个相关文档，检索到1个
        List<String> retrieved = List.of("doc1", "doc2", "doc3");
        List<String> expected = List.of("doc2", "doc4", "doc5");

        // When: 计算Recall@3
        double recall = MetricsCalculator.calculateRecallAtK(retrieved, expected, 3);

        // Then: Recall = 1/3 ≈ 0.333
        assertThat(recall).isCloseTo(0.333, within(0.001));
    }

    /**
     * 测试：F1分数
     */
    @Test
    void testCalculateF1() {
        // Given: Precision和Recall
        double precision = 0.5;
        double recall = 0.4;

        // When: 计算F1
        double f1 = MetricsCalculator.calculateF1(precision, recall);

        // Then: F1 = 2 * 0.5 * 0.4 / (0.5 + 0.4) ≈ 0.444
        assertThat(f1).isCloseTo(0.444, within(0.001));
    }

    /**
     * 测试：空列表边界条件
     */
    @Test
    void testEmptyLists() {
        List<String> empty = List.of();
        List<String> nonEmpty = List.of("doc1");

        // Hit Rate
        assertThat(MetricsCalculator.calculateHitRate(empty, nonEmpty)).isEqualTo(0.0);
        assertThat(MetricsCalculator.calculateHitRate(nonEmpty, empty)).isEqualTo(0.0);

        // MRR
        assertThat(MetricsCalculator.calculateMRR(empty, nonEmpty)).isEqualTo(0.0);
        assertThat(MetricsCalculator.calculateMRR(nonEmpty, empty)).isEqualTo(0.0);

        // NDCG
        assertThat(MetricsCalculator.calculateNDCG(empty, nonEmpty, 5)).isEqualTo(0.0);
        assertThat(MetricsCalculator.calculateNDCG(nonEmpty, empty, 5)).isEqualTo(0.0);

        // Precision
        assertThat(MetricsCalculator.calculatePrecisionAtK(empty, nonEmpty, 5)).isEqualTo(0.0);

        // Recall
        assertThat(MetricsCalculator.calculateRecallAtK(empty, nonEmpty, 5)).isEqualTo(0.0);
    }

    /**
     * 测试：F1分数边界条件
     */
    @Test
    void testF1_ZeroDenominator() {
        // When: Precision和Recall都为0
        double f1 = MetricsCalculator.calculateF1(0.0, 0.0);

        // Then: F1 = 0
        assertThat(f1).isEqualTo(0.0);
    }
}
