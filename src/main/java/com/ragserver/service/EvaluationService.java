package com.ragserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragserver.evaluation.MetricsCalculator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 评估服务
 *
 * <p>提供RAG系统的批量评估功能：</p>
 * <ul>
 *   <li>加载黄金测试集</li>
 *   <li>批量执行RAG查询</li>
 *   <li>计算评估指标（Hit Rate、MRR、NDCG等）</li>
 *   <li>生成评估报告</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * EvaluationReport report = evaluationService.evaluate(
 *     "classpath:fixtures/golden_test_set.json",
 *     10,  // topK
 *     false  // enableRerank
 * );
 *
 * System.out.println("Hit@5: " + report.getHitRate5());
 * System.out.println("MRR: " + report.getMrr());
 * System.out.println("NDCG@10: " + report.getNdcg10());
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class EvaluationService {

    private final RagService ragService;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public EvaluationService(RagService ragService,
                            ResourceLoader resourceLoader,
                            ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量评估RAG系统
     *
     * <p>加载黄金测试集，对每个测试用例执行检索，计算评估指标。</p>
     *
     * @param testSetPath 测试集文件路径（支持classpath:）
     * @param topK 检索的Top-K数量
     * @param enableRerank 是否启用Rerank
     * @return 评估报告
     */
    public EvaluationReport evaluate(String testSetPath, int topK, boolean enableRerank) {
        log.info("开始评估：testSet={}, topK={}, rerank={}", testSetPath, topK, enableRerank);

        try {
            // 1. 加载测试集
            GoldenTestSet testSet = loadTestSet(testSetPath);
            log.info("加载测试集：共{}个测试用例", testSet.getTestCases().size());

            // 2. 批量执行检索
            List<TestResult> results = new ArrayList<>();
            for (TestCase testCase : testSet.getTestCases()) {
                TestResult result = evaluateTestCase(testCase, topK, enableRerank);
                results.add(result);
            }

            // 3. 计算汇总指标
            EvaluationReport report = calculateMetrics(results, topK);
            report.setTestSetPath(testSetPath);
            report.setTopK(topK);
            report.setEnableRerank(enableRerank);
            report.setTotalTestCases(testSet.getTestCases().size());

            log.info("评估完成：Hit@{} = {:.3f}, MRR = {:.3f}, NDCG@{} = {:.3f}",
                    topK, report.getHitRate(), report.getMrr(), topK, report.getNdcg());

            return report;

        } catch (Exception e) {
            log.error("评估失败：{}", e.getMessage(), e);
            throw new RuntimeException("评估失败：" + e.getMessage(), e);
        }
    }

    /**
     * 评估单个测试用例
     */
    private TestResult evaluateTestCase(TestCase testCase, int topK, boolean enableRerank) {
        log.debug("评估测试用例：id={}, query={}", testCase.getId(), testCase.getQuery());

        TestResult result = new TestResult();
        result.setTestCaseId(testCase.getId());
        result.setQuery(testCase.getQuery());
        result.setExpectedChunkIds(testCase.getExpectedChunkIds());

        try {
            // 执行RAG查询（这里简化为模拟，实际需要调用检索服务）
            // 注意：实际实现需要从RAG流程中提取检索到的chunk IDs
            // 这里使用空列表作为占位符
            List<String> retrievedChunkIds = new ArrayList<>();

            // TODO: 实际实现需要调用HybridRetriever并获取chunk IDs
            // 由于当前RagService.query()返回的是最终答案字符串，
            // 需要修改为返回包含chunk IDs的详细结果
            log.warn("检索功能待实现：当前使用空结果");

            result.setRetrievedChunkIds(retrievedChunkIds);

            // 计算指标
            result.setHitRate(MetricsCalculator.calculateHitRate(retrievedChunkIds, testCase.getExpectedChunkIds()));
            result.setMrr(MetricsCalculator.calculateMRR(retrievedChunkIds, testCase.getExpectedChunkIds()));
            result.setNdcg(MetricsCalculator.calculateNDCG(retrievedChunkIds, testCase.getExpectedChunkIds(), topK));
            result.setPrecision(MetricsCalculator.calculatePrecisionAtK(retrievedChunkIds, testCase.getExpectedChunkIds(), topK));
            result.setRecall(MetricsCalculator.calculateRecallAtK(retrievedChunkIds, testCase.getExpectedChunkIds(), topK));

        } catch (Exception e) {
            log.error("评估测试用例失败：id={}, error={}", testCase.getId(), e.getMessage(), e);
            result.setError(e.getMessage());
        }

        return result;
    }

    /**
     * 计算汇总指标
     */
    private EvaluationReport calculateMetrics(List<TestResult> results, int topK) {
        EvaluationReport report = new EvaluationReport();

        int successCount = 0;
        double totalHitRate = 0.0;
        double totalMrr = 0.0;
        double totalNdcg = 0.0;
        double totalPrecision = 0.0;
        double totalRecall = 0.0;

        for (TestResult result : results) {
            if (result.getError() == null) {
                successCount++;
                totalHitRate += result.getHitRate();
                totalMrr += result.getMrr();
                totalNdcg += result.getNdcg();
                totalPrecision += result.getPrecision();
                totalRecall += result.getRecall();
            }
        }

        if (successCount > 0) {
            report.setHitRate(totalHitRate / successCount);
            report.setMrr(totalMrr / successCount);
            report.setNdcg(totalNdcg / successCount);
            report.setPrecision(totalPrecision / successCount);
            report.setRecall(totalRecall / successCount);
            report.setF1(MetricsCalculator.calculateF1(report.getPrecision(), report.getRecall()));
        }

        report.setSuccessCount(successCount);
        report.setFailedCount(results.size() - successCount);
        report.setTestResults(results);

        return report;
    }

    /**
     * 加载黄金测试集
     */
    private GoldenTestSet loadTestSet(String path) throws IOException {
        log.debug("加载测试集：{}", path);

        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            throw new IOException("测试集文件不存在：" + path);
        }

        return objectMapper.readValue(resource.getInputStream(), GoldenTestSet.class);
    }

    /**
     * 黄金测试集
     */
    @Data
    public static class GoldenTestSet {
        private String description;
        private String version;
        private String createdAt;
        private List<TestCase> testCases;
        private Map<String, Object> statistics;
    }

    /**
     * 测试用例
     */
    @Data
    public static class TestCase {
        private String id;
        private String query;
        private List<String> expectedChunkIds;
        private List<String> expectedSources;
        private String category;
        private String difficulty;
    }

    /**
     * 测试结果
     */
    @Data
    public static class TestResult {
        private String testCaseId;
        private String query;
        private List<String> expectedChunkIds;
        private List<String> retrievedChunkIds;
        private double hitRate;
        private double mrr;
        private double ndcg;
        private double precision;
        private double recall;
        private String error;
    }

    /**
     * 评估报告
     */
    @Data
    public static class EvaluationReport {
        private String testSetPath;
        private int topK;
        private boolean enableRerank;
        private int totalTestCases;
        private int successCount;
        private int failedCount;

        // 汇总指标
        private double hitRate;
        private double mrr;
        private double ndcg;
        private double precision;
        private double recall;
        private double f1;

        // 详细结果
        private List<TestResult> testResults;

        // 便捷方法
        public double getHitRate5() {
            return hitRate;
        }

        public double getNdcg10() {
            return ndcg;
        }
    }
}
