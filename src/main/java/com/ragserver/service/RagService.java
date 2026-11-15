package com.ragserver.service;

import com.ragserver.ai.dashscope.DashScopeChatClient;
import com.ragserver.entity.QueryHistory;
import com.ragserver.repository.QueryHistoryRepository;
import com.ragserver.retrieval.HybridRetriever;
import com.ragserver.retrieval.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG核心服务
 *
 * <p>整合检索、重排序、生成的完整RAG流程：</p>
 * <ol>
 *   <li>混合检索：通过HybridRetriever获取相关文档</li>
 *   <li>可选Rerank：LLM精排提升结果质量</li>
 *   <li>Prompt构建：将上下文和问题渲染到模板</li>
 *   <li>LLM生成：调用大模型生成答案（流式输出）</li>
 *   <li>Citation添加：附加文档引用信息</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 流式查询
 * Flux<String> answerStream = ragService.queryStream("什么是RAG？", 10, false);
 * }</pre>
 *
 * <h3>流程图</h3>
 * <pre>
 * 用户问题 → 混合检索 → (可选Rerank) → Prompt构建 → LLM流式生成 → 返回答案流
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class RagService {

    private final HybridRetriever retriever;
    private final DashScopeChatClient chatClient;
    private final PromptService promptService;
    private final StreamingService streamingService;
    private final QueryHistoryRepository queryHistoryRepository;

    /**
     * 默认检索文档数量
     */
    private static final int DEFAULT_TOP_K = 10;

    /**
     * 默认是否启用Rerank
     */
    private static final boolean DEFAULT_ENABLE_RERANK = false;

    public RagService(HybridRetriever retriever,
                      DashScopeChatClient chatClient,
                      PromptService promptService,
                      StreamingService streamingService,
                      QueryHistoryRepository queryHistoryRepository) {
        this.retriever = retriever;
        this.chatClient = chatClient;
        this.promptService = promptService;
        this.streamingService = streamingService;
        this.queryHistoryRepository = queryHistoryRepository;
    }

    /**
     * RAG查询（仅检索，不生成）
     *
     * <p>用于测试检索效果，返回检索到的文档列表。</p>
     *
     * @param question 用户问题
     * @param topK 检索文档数量
     * @param enableRerank 是否启用重排序
     * @return 检索到的文档列表
     */
    public List<Document> retrieveOnly(String question, int topK, boolean enableRerank) {
        log.info("仅检索模式：question={}, topK={}, enableRerank={}", question, topK, enableRerank);

        try {
            HybridRetriever.RetrievalResult result = retriever.retrieve(question, topK, enableRerank);
            List<Document> documents = result.getDocuments();
            log.info("检索完成：获取{}个文档", documents.size());
            return documents;

        } catch (Exception e) {
            log.error("检索失败：question={}, error={}", question, e.getMessage(), e);
            throw new RuntimeException("检索失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构建RAG Prompt（仅构建，不查询）
     *
     * <p>用于测试Prompt构建效果。</p>
     *
     * @param question 用户问题
     * @param topK 检索文档数量
     * @param enableRerank 是否启用重排序
     * @return 构建的Prompt字符串
     */
    public String buildPromptOnly(String question, int topK, boolean enableRerank) {
        log.info("仅构建Prompt模式：question={}, topK={}, enableRerank={}", question, topK, enableRerank);

        try {
            // 1. 检索
            HybridRetriever.RetrievalResult result = retriever.retrieve(question, topK, enableRerank);
            List<Document> context = result.getDocuments();

            // 2. 构建Prompt
            String prompt = promptService.buildRagPrompt(question, context);

            log.info("Prompt构建完成，长度：{}", prompt.length());
            return prompt;

        } catch (Exception e) {
            log.error("Prompt构建失败：question={}, error={}", question, e.getMessage(), e);
            throw new RuntimeException("Prompt构建失败：" + e.getMessage(), e);
        }
    }

    /**
     * 流式RAG查询
     *
     * <p>使用SSE（Server-Sent Events）流式返回答案，提供更好的用户体验。</p>
     *
     * <h3>工作流程</h3>
     * <ol>
     *   <li>检索相关文档</li>
     *   <li>构建Prompt</li>
     *   <li>流式生成答案</li>
     *   <li>最后添加引用信息</li>
     * </ol>
     *
     * <h3>优势</h3>
     * <ul>
     *   <li>降低首字延迟</li>
     *   <li>实时显示生成过程</li>
     *   <li>更好的用户体验</li>
     * </ul>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * @GetMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
     * public Flux<String> queryStream(@RequestParam String question) {
     *     return ragService.queryStream(question);
     * }
     * }</pre>
     *
     * @param question 用户问题
     * @return 流式答案文本
     */
    public Flux<String> queryStream(String question) {
        return queryStream(question, DEFAULT_TOP_K, DEFAULT_ENABLE_RERANK);
    }

    /**
     * 流式RAG查询（完整参数）
     *
     * <p>支持自定义检索参数的流式查询，并返回详细的耗时统计。</p>
     *
     * @param question 用户问题
     * @param topK 检索文档数量
     * @param enableRerank 是否启用重排序
     * @return 流式答案文本（包含耗时统计）
     */
    public Flux<String> queryStream(String question, int topK, boolean enableRerank) {
        log.info("开始流式RAG查询：question={}, topK={}, enableRerank={}", question, topK, enableRerank);

        return Flux.defer(() -> {
            try {
                long totalStartTime = System.currentTimeMillis();

                // 1. 检索相关文档
                long retrievalStartTime = System.currentTimeMillis();
                HybridRetriever.RetrievalResult result = retriever.retrieve(question, topK, enableRerank);
                List<Document> context = result.getDocuments();
                long retrievalEndTime = System.currentTimeMillis();
                log.debug("检索完成：获取{}个文档", context.size());

                // 检查是否有检索结果
                if (context.isEmpty()) {
                    log.warn("未检索到相关文档");
                    return Flux.just("抱歉，在知识库中未找到与您问题相关的信息。");
                }

                // 2. 构建Prompt
                long promptStartTime = System.currentTimeMillis();
                String prompt = promptService.buildRagPrompt(question, context);
                long promptEndTime = System.currentTimeMillis();
                log.debug("Prompt构建完成，长度：{}", prompt.length());

                // 3. 流式生成答案（记录首字延迟和生成耗时）
                final long[] firstTokenTime = {0L};  // 首字延迟
                final long[] streamStartTime = {System.currentTimeMillis()};

                Flux<String> answerStream = streamingService.streamChat(prompt)
                    .doOnNext(token -> {
                        if (firstTokenTime[0] == 0L) {
                            firstTokenTime[0] = System.currentTimeMillis();
                            log.debug("收到第一个token，首字延迟：{}ms", firstTokenTime[0] - streamStartTime[0]);
                        }
                    });

                // 4. 在流结束时添加耗时统计（使用特殊标记，便于前端识别）
                return answerStream.concatWith(Flux.defer(() -> {
                    long totalEndTime = System.currentTimeMillis();

                    // 计算各阶段耗时
                    long denseSearchMs = result.getDenseSearchDurationMs();
                    long sparseSearchMs = result.getSparseSearchDurationMs();
                    long rrfFusionMs = result.getRrfFusionDurationMs();
                    long rerankerMs = result.getRerankerDurationMs();
                    long retrievalTotalMs = retrievalEndTime - retrievalStartTime;
                    long promptBuildMs = promptEndTime - promptStartTime;
                    long ttftMs = firstTokenTime[0] > 0 ? (firstTokenTime[0] - streamStartTime[0]) : 0;
                    long streamGenerationMs = firstTokenTime[0] > 0 ? (totalEndTime - firstTokenTime[0]) : 0;
                    long totalMs = totalEndTime - totalStartTime;

                    // 使用特殊标记前缀，便于前端识别（不会被渲染为文本）
                    String timingJson = String.format(
                        "\n__TIMING_DATA__{\"denseSearch\":%d,\"sparseSearch\":%d,\"rrfFusion\":%d," +
                        "\"reranker\":%d,\"retrieval\":%d,\"promptBuild\":%d,\"ttft\":%d," +
                        "\"streamGeneration\":%d,\"total\":%d}",
                        denseSearchMs, sparseSearchMs, rrfFusionMs, rerankerMs, retrievalTotalMs,
                        promptBuildMs, ttftMs, streamGenerationMs, totalMs
                    );

                    log.info("流式查询完成 - 总耗时: {}ms, 检索: {}ms, Prompt: {}ms, TTFT: {}ms, 生成: {}ms",
                        totalMs, retrievalTotalMs, promptBuildMs, ttftMs, streamGenerationMs);

                    return Flux.just(timingJson);
                }));

            } catch (Exception e) {
                log.error("流式RAG查询失败：question={}, error={}", question, e.getMessage(), e);
                return Flux.just("错误：" + e.getMessage());
            }
        });
    }

    /**
     * 保存查询历史
     *
     * <p>异步保存查询历史，失败不影响主流程。</p>
     *
     * @param question 用户问题
     * @param answer 生成的答案
     * @param topK 检索文档数量
     * @param enableRerank 是否启用重排序
     * @param responseTimeMs 响应时间
     * @param status 查询状态
     * @param retrievedDocsCount 检索到的文档数量
     * @param errorMessage 错误消息（如果失败）
     */
    private void saveQueryHistory(String question, String answer, Integer topK,
                                  Boolean enableRerank, Long responseTimeMs,
                                  String status, Integer retrievedDocsCount,
                                  String errorMessage) {
        try {
            QueryHistory history = QueryHistory.create(question, answer, topK,
                enableRerank, responseTimeMs, status);
            history.setRetrievedDocsCount(retrievedDocsCount);

            if (errorMessage != null) {
                history.setErrorMessage(errorMessage);
            }

            queryHistoryRepository.save(history);
            log.debug("查询历史已保存：question={}, status={}", question, status);

        } catch (Exception e) {
            log.warn("保存查询历史失败（不影响主流程）：{}", e.getMessage());
        }
    }

    /**
     * 查询结果（包含耗时统计）
     */
    public static class QueryResult {
        private String answer;
        private List<Document> retrievedDocuments;  // 检索到的文档

        // 检索阶段详细耗时
        private long denseSearchDurationMs;    // Dense向量检索耗时
        private long sparseSearchDurationMs;   // Sparse关键字检索耗时
        private long rrfFusionDurationMs;      // RRF融合耗时
        private long rerankerDurationMs;       // Reranker精排耗时
        private long retrievalDurationMs;      // 检索总耗时

        // 其他阶段耗时
        private long promptBuildDurationMs;    // Prompt构建耗时
        private long llmGenerationDurationMs;  // LLM生成耗时
        private long citationBuildDurationMs;  // 引用构建耗时
        private long totalDurationMs;          // 总耗时

        // Getters and Setters
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }

        public List<Document> getRetrievedDocuments() { return retrievedDocuments; }
        public void setRetrievedDocuments(List<Document> retrievedDocuments) { this.retrievedDocuments = retrievedDocuments; }

        public long getDenseSearchDurationMs() { return denseSearchDurationMs; }
        public void setDenseSearchDurationMs(long denseSearchDurationMs) { this.denseSearchDurationMs = denseSearchDurationMs; }

        public long getSparseSearchDurationMs() { return sparseSearchDurationMs; }
        public void setSparseSearchDurationMs(long sparseSearchDurationMs) { this.sparseSearchDurationMs = sparseSearchDurationMs; }

        public long getRrfFusionDurationMs() { return rrfFusionDurationMs; }
        public void setRrfFusionDurationMs(long rrfFusionDurationMs) { this.rrfFusionDurationMs = rrfFusionDurationMs; }

        public long getRerankerDurationMs() { return rerankerDurationMs; }
        public void setRerankerDurationMs(long rerankerDurationMs) { this.rerankerDurationMs = rerankerDurationMs; }

        public long getRetrievalDurationMs() { return retrievalDurationMs; }
        public void setRetrievalDurationMs(long retrievalDurationMs) { this.retrievalDurationMs = retrievalDurationMs; }

        public long getPromptBuildDurationMs() { return promptBuildDurationMs; }
        public void setPromptBuildDurationMs(long promptBuildDurationMs) { this.promptBuildDurationMs = promptBuildDurationMs; }

        public long getLlmGenerationDurationMs() { return llmGenerationDurationMs; }
        public void setLlmGenerationDurationMs(long llmGenerationDurationMs) { this.llmGenerationDurationMs = llmGenerationDurationMs; }

        public long getCitationBuildDurationMs() { return citationBuildDurationMs; }
        public void setCitationBuildDurationMs(long citationBuildDurationMs) { this.citationBuildDurationMs = citationBuildDurationMs; }

        public long getTotalDurationMs() { return totalDurationMs; }
        public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
    }
}


