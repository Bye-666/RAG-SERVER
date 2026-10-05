package com.ragserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.*;

/**
 * EvaluationService 集成测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@SpringBootTest
class EvaluationServiceTest {

    @Autowired
    private EvaluationService evaluationService;

    @MockBean
    private RagService ragService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试：加载黄金测试集并评估
     */
    @Test
    void testEvaluate() {
        // When: 执行评估
        EvaluationService.EvaluationReport report = evaluationService.evaluate(
                "classpath:fixtures/golden_test_set.json",
                10,
                false
        );

        // Then: 评估报告生成
        assertThat(report).isNotNull();
        assertThat(report.getTotalTestCases()).isEqualTo(25);
        assertThat(report.getTopK()).isEqualTo(10);
        assertThat(report.isEnableRerank()).isFalse();

        // 指标应该存在（即使是0，因为我们mock了检索结果）
        assertThat(report.getHitRate()).isGreaterThanOrEqualTo(0.0);
        assertThat(report.getMrr()).isGreaterThanOrEqualTo(0.0);
        assertThat(report.getNdcg()).isGreaterThanOrEqualTo(0.0);
    }

    /**
     * 测试：测试集文件不存在
     */
    @Test
    void testEvaluateFileNotFound() {
        // When & Then: 文件不存在应抛出异常
        assertThatThrownBy(() -> evaluationService.evaluate(
                "classpath:fixtures/nonexistent.json",
                10,
                false
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("评估失败");
    }

    /**
     * 测试：评估报告的便捷方法
     */
    @Test
    void testEvaluationReportConvenienceMethods() {
        // Given: 创建评估报告
        EvaluationService.EvaluationReport report = new EvaluationService.EvaluationReport();
        report.setHitRate(0.85);
        report.setNdcg(0.75);

        // When & Then: 便捷方法返回正确的值
        assertThat(report.getHitRate5()).isEqualTo(0.85);
        assertThat(report.getNdcg10()).isEqualTo(0.75);
    }

    /**
     * 测试：加载测试集的数据结构
     */
    @Test
    void testGoldenTestSetStructure() throws Exception {
        // When: 加载测试集
        EvaluationService.EvaluationReport report = evaluationService.evaluate(
                "classpath:fixtures/golden_test_set.json",
                5,
                false
        );

        // Then: 测试结果包含所有测试用例
        assertThat(report.getTestResults()).hasSize(25);

        // 验证第一个测试用例的结构
        EvaluationService.TestResult firstResult = report.getTestResults().get(0);
        assertThat(firstResult.getTestCaseId()).isNotNull();
        assertThat(firstResult.getQuery()).isNotNull();
        assertThat(firstResult.getExpectedChunkIds()).isNotEmpty();
    }

    /**
     * 测试：评估报告统计信息
     */
    @Test
    void testEvaluationReportStatistics() {
        // When: 执行评估
        EvaluationService.EvaluationReport report = evaluationService.evaluate(
                "classpath:fixtures/golden_test_set.json",
                10,
                false
        );

        // Then: 统计信息正确
        assertThat(report.getSuccessCount() + report.getFailedCount())
                .isEqualTo(report.getTotalTestCases());

        // F1分数计算正确
        if (report.getPrecision() > 0 || report.getRecall() > 0) {
            double expectedF1 = 2.0 * (report.getPrecision() * report.getRecall())
                    / (report.getPrecision() + report.getRecall());
            assertThat(report.getF1()).isCloseTo(expectedF1, within(0.001));
        }
    }
}
