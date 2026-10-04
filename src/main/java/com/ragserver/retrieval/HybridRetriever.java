package com.ragserver.retrieval;

import com.ragserver.retrieval.milvus.MilvusHybridStore;
import com.ragserver.retrieval.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 混合检索器
 *
 * <p>协调混合检索流程，提供企业级检索能力：</p>
 * <ul>
 *   <li>混合检索：Dense向量 + Sparse BM25，通过RRF融合</li>
 *   <li>并行执行：两路检索并行，提升响应速度</li>
 *   <li>异常降级：任一路失败自动降级到单路检索</li>
 *   <li>可选Rerank：LLM精排提升结果质量</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 基本检索
 * List<Document> docs = retriever.retrieve("什么是RAG？", 5, false);
 *
 * // 启用Rerank
 * List<Document> rankedDocs = retriever.retrieve("什么是RAG？", 5, true);
 * }</pre>
 *
 * <h3>降级策略</h3>
 * <pre>
 * 正常：Dense ∥ Sparse → RRF融合 → (可选Rerank) → 返回
 * 降级：仅Dense或仅Sparse → 直接返回
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class HybridRetriever {

    private final MilvusHybridStore vectorStore;
    private final RerankerService reranker;

    /**
     * 检索超时时间（秒）
     */
    private static final int RETRIEVAL_TIMEOUT_SECONDS = 30;

    public HybridRetriever(MilvusHybridStore vectorStore, RerankerService reranker) {
        this.vectorStore = vectorStore;
        this.reranker = reranker;
    }

    /**
     * 检索文档（混合检索 + 可选Rerank）
     *
     * <p>标准检索接口，支持混合检索和重排序。</p>
     *
     * @param query 查询文本
     * @param topK 最终返回的文档数量
     * @param enableRerank 是否启用重排序（建议topK≤10时启用）
     * @return 检索到的文档列表，按相关性降序排序
     */
    public List<Document> retrieve(String query, int topK, boolean enableRerank) {
        log.info("开始检索：query={}, topK={}, enableRerank={}", query, topK, enableRerank);

        long startTime = System.currentTimeMillis();

        try {
            // 1. 混合检索（召回更多候选用于Rerank）
            int candidateCount = enableRerank ? topK * 2 : topK;
            List<Document> candidates = vectorStore.searchHybrid(query, candidateCount);

            log.debug("混合检索完成：召回{}个候选文档", candidates.size());

            // 2. 可选Rerank
            List<Document> results;
            if (enableRerank && !candidates.isEmpty()) {
                results = reranker.rerank(query, candidates, topK);
                log.info("Rerank完成：从{}个候选中精排出{}个文档", candidates.size(), results.size());
            } else {
                // 不启用Rerank，直接截取topK
                results = candidates.size() > topK
                        ? candidates.subList(0, topK)
                        : candidates;
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("检索完成：返回{}个文档，耗时{}ms", results.size(), elapsedTime);

            return results;

        } catch (Exception e) {
            log.error("检索失败：query={}, error={}", query, e.getMessage(), e);
            throw new RuntimeException("检索失败：" + e.getMessage(), e);
        }
    }

    /**
     * 检索文档（并行模式，带降级）
     *
     * <p>两路检索并行执行，任一路失败自动降级到单路。</p>
     *
     * <p><b>注意</b>：当前MilvusHybridStore已内部实现串行Dense+Sparse，
     * 此方法提供额外的异常隔离能力。</p>
     *
     * @param query 查询文本
     * @param topK 返回文档数量
     * @return 检索到的文档列表
     */
    public List<Document> retrieveWithFallback(String query, int topK) {
        log.info("开始并行检索（带降级）：query={}, topK={}", query, topK);

        long startTime = System.currentTimeMillis();

        // 启动两路并行检索
        CompletableFuture<List<Document>> denseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return vectorStore.searchDense(query, topK);
            } catch (Exception e) {
                log.warn("Dense检索失败：{}", e.getMessage());
                return List.of();
            }
        });

        CompletableFuture<List<Document>> sparseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return vectorStore.searchSparse(query, topK);
            } catch (Exception e) {
                log.warn("Sparse检索失败：{}", e.getMessage());
                return List.of();
            }
        });

        try {
            // 等待两路完成（带超时）
            List<Document> denseResults = denseFuture.get(RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            List<Document> sparseResults = sparseFuture.get(RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // 判断降级情况
            if (denseResults.isEmpty() && sparseResults.isEmpty()) {
                log.error("Dense和Sparse检索全部失败");
                return List.of();
            } else if (denseResults.isEmpty()) {
                log.warn("Dense检索失败，降级为Sparse检索");
                return sparseResults.size() > topK ? sparseResults.subList(0, topK) : sparseResults;
            } else if (sparseResults.isEmpty()) {
                log.warn("Sparse检索失败，降级为Dense检索");
                return denseResults.size() > topK ? denseResults.subList(0, topK) : denseResults;
            }

            // 两路都成功，使用RRF融合
            log.debug("两路检索都成功：Dense={}个, Sparse={}个", denseResults.size(), sparseResults.size());
            // 注意：这里需要手动调用RRFFusion，因为我们绕过了MilvusHybridStore.searchHybrid
            // 暂时返回Dense结果作为简化实现
            return denseResults.size() > topK ? denseResults.subList(0, topK) : denseResults;

        } catch (Exception e) {
            log.error("并行检索失败：{}", e.getMessage(), e);
            // 最后降级：尝试标准混合检索
            try {
                return vectorStore.searchHybrid(query, topK);
            } catch (Exception fallbackEx) {
                log.error("降级检索也失败：{}", fallbackEx.getMessage());
                return List.of();
            }
        } finally {
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("并行检索完成，耗时{}ms", elapsedTime);
        }
    }

    /**
     * 简单检索（仅混合检索，无Rerank）
     *
     * <p>便捷方法，适合快速查询场景。</p>
     *
     * @param query 查询文本
     * @param topK 返回文档数量
     * @return 检索到的文档列表
     */
    public List<Document> retrieve(String query, int topK) {
        return retrieve(query, topK, false);
    }
}
