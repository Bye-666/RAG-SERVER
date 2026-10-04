package com.ragserver.retrieval;

import com.ragserver.retrieval.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RRF融合器（Reciprocal Rank Fusion）
 *
 * <p>融合多路检索结果，综合考虑不同检索策略的排名。</p>
 *
 * <h3>RRF算法</h3>
 * <p>RRF是一种简单有效的结果融合算法，对每个文档在不同检索列表中的排名进行加权求和。</p>
 *
 * <h4>公式</h4>
 * <pre>
 * RRF_score(doc) = Σ 1 / (k + rank_i)
 *
 * 其中：
 * - rank_i: 文档在第i个检索列表中的排名（从1开始）
 * - k: 常数，用于平滑排名影响（默认60）
 * - Σ: 对所有包含该文档的检索列表求和
 * </pre>
 *
 * <h3>算法特点</h3>
 * <ul>
 *   <li>无需归一化不同检索算法的分数</li>
 *   <li>对排名靠前的文档给予更高权重</li>
 *   <li>自动处理文档去重</li>
 *   <li>对缺失文档有容错性</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // Dense检索结果
 * List<Document> denseResults = hybridStore.searchDense("RAG技术", 10);
 *
 * // Sparse检索结果
 * List<Document> sparseResults = hybridStore.searchSparse("RAG技术", 10);
 *
 * // RRF融合
 * RRFFusion fusion = new RRFFusion();
 * List<Document> hybridResults = fusion.fuse(
 *     Arrays.asList(denseResults, sparseResults),
 *     10
 * );
 * }</pre>
 *
 * <h3>参数说明</h3>
 * <ul>
 *   <li>k=60: 标准RRF常数，来源于TREC实验最佳实践</li>
 *   <li>较小的k会增加排名靠前文档的权重</li>
 *   <li>较大的k会使排名影响更平滑</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class RRFFusion {

    /**
     * RRF常数k
     *
     * <p>用于平滑排名影响，标准值为60。</p>
     */
    private static final int K = 60;

    /**
     * 融合多路检索结果
     *
     * <p>使用RRF算法融合多个检索列表，返回TopK个文档。</p>
     *
     * @param resultLists 多路检索结果列表
     * @param topK 返回前K个文档
     * @return 融合后的文档列表，按RRF分数降序排序
     */
    public List<Document> fuse(List<List<Document>> resultLists, int topK) {
        if (resultLists == null || resultLists.isEmpty()) {
            log.warn("检索结果列表为空");
            return Collections.emptyList();
        }

        log.debug("开始RRF融合：{}路结果，topK={}", resultLists.size(), topK);

        // 1. 计算每个文档的RRF分数
        Map<String, FusionScore> docScores = new HashMap<>();

        for (int listIndex = 0; listIndex < resultLists.size(); listIndex++) {
            List<Document> results = resultLists.get(listIndex);

            for (int rank = 0; rank < results.size(); rank++) {
                Document doc = results.get(rank);
                String docId = doc.getId();

                // 计算RRF分数：1 / (k + rank)
                // rank从0开始，转换为从1开始：rank + 1
                double rrfScore = 1.0 / (K + rank + 1);

                // 累加分数
                FusionScore fusionScore = docScores.computeIfAbsent(docId, k -> new FusionScore(doc));
                fusionScore.addScore(rrfScore, listIndex);

                log.trace("文档 {} 在列表{} 排名{}: RRF分数={}", docId, listIndex, rank + 1, rrfScore);
            }
        }

        log.debug("融合前去重：{}个文档", docScores.size());

        // 2. 按RRF分数排序
        List<Document> fusedResults = docScores.values().stream()
            .sorted(Comparator.comparingDouble(FusionScore::getTotalScore).reversed())
            .limit(topK)
            .map(FusionScore::toDocument)
            .collect(Collectors.toList());

        log.info("RRF融合完成：返回{}个文档", fusedResults.size());

        // 打印融合统计
        if (log.isDebugEnabled()) {
            logFusionStats(docScores, resultLists.size());
        }

        return fusedResults;
    }

    /**
     * 融合两路检索结果（便捷方法）
     *
     * <p>适用于Dense + Sparse混合检索场景。</p>
     *
     * @param denseResults Dense检索结果
     * @param sparseResults Sparse检索结果
     * @param topK 返回前K个文档
     * @return 融合后的文档列表
     */
    public List<Document> fuseTwoWay(List<Document> denseResults, List<Document> sparseResults, int topK) {
        return fuse(Arrays.asList(denseResults, sparseResults), topK);
    }

    /**
     * 打印融合统计信息
     *
     * @param docScores 文档分数映射
     * @param numLists 检索列表数量
     */
    private void logFusionStats(Map<String, FusionScore> docScores, int numLists) {
        // 统计出现在多个列表中的文档数量
        Map<Integer, Long> appearanceCounts = docScores.values().stream()
            .collect(Collectors.groupingBy(
                fs -> fs.getAppearances().size(),
                Collectors.counting()
            ));

        log.debug("融合统计：");
        for (int i = 1; i <= numLists; i++) {
            long count = appearanceCounts.getOrDefault(i, 0L);
            log.debug("  出现在{}个列表中的文档：{}个", i, count);
        }
    }

    /**
     * 融合分数记录
     *
     * <p>记录单个文档在多个检索列表中的分数和出现情况。</p>
     */
    private static class FusionScore {
        private final Document document;
        private double totalScore = 0.0;
        private final Set<Integer> appearances = new HashSet<>();

        public FusionScore(Document document) {
            this.document = document;
        }

        public void addScore(double score, int listIndex) {
            this.totalScore += score;
            this.appearances.add(listIndex);
        }

        public double getTotalScore() {
            return totalScore;
        }

        public Set<Integer> getAppearances() {
            return appearances;
        }

        public Document toDocument() {
            // 创建新文档，保留原始信息，更新分数为RRF分数
            return Document.builder()
                .id(document.getId())
                .text(document.getText())
                .denseVector(document.getDenseVector())
                .sparseVector(document.getSparseVector())
                .metadata(document.getMetadata())
                .score((float) totalScore)  // RRF融合分数
                .build();
        }
    }

    /**
     * 获取RRF常数k
     *
     * @return RRF常数k
     */
    public int getK() {
        return K;
    }
}
