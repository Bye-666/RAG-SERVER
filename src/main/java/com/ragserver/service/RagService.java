package com.ragserver.service;

import com.ragserver.ai.dashscope.DashScopeChatClient;
import com.ragserver.entity.QueryHistory;
import com.ragserver.repository.QueryHistoryRepository;
import com.ragserver.retrieval.HybridRetriever;
import com.ragserver.retrieval.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG核心服务
 *
 * <p>整合检索、重排序、生成的完整RAG流程：</p>
 * <ol>
 *   <li>混合检索：通过HybridRetriever获取相关文档</li>
 *   <li>可选Rerank：LLM精排提升结果质量</li>
 *   <li>Prompt构建：将上下文和问题渲染到模板</li>
 *   <li>LLM生成：调用大模型生成答案</li>
 *   <li>Citation添加：附加文档引用信息</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 基本查询
 * String answer = ragService.query("什么是RAG？");
 *
 * // 启用Rerank
 * String answer = ragService.query("什么是RAG？", 10, true);
 * }</pre>
 *
 * <h3>流程图</h3>
 * <pre>
 * 用户问题 → 混合检索 → (可选Rerank) → Prompt构建 → LLM生成 → 添加Citation → 返回答案
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
     * RAG查询（使用默认参数）
     *
     * <p>默认检索10个文档，不启用Rerank。</p>
     *
     * @param question 用户问题
     * @return 生成的答案（带引用）
     */
    public String query(String question) {
        return query(question, DEFAULT_TOP_K, DEFAULT_ENABLE_RERANK);
    }

    /**
     * RAG查询（完整参数）
     *
     * <p>完整的RAG流程，支持自定义参数。</p>
     *
     * @param question 用户问题
     * @param topK 检索文档数量
     * @param enableRerank 是否启用重排序
     * @return 生成的答案（带引用）
     */
    public String query(String question, int topK, boolean enableRerank) {
        log.info("开始RAG查询：question={}, topK={}, enableRerank={}", question, topK, enableRerank);

        long startTime = System.currentTimeMillis();
        String answer = null;
        String status = "SUCCESS";
        String errorMessage = null;
        int retrievedDocsCount = 0;

        try {
            // 1. 检索相关文档
            List<Document> context = retriever.retrieve(question, topK, enableRerank);
            retrievedDocsCount = context.size();
            log.debug("检索完成：获取{}个文档", context.size());

            // 检查是否有检索结果
            if (context.isEmpty()) {
                log.warn("未检索到相关文档");
                status = "NO_RESULTS";
                answer = "抱歉，在知识库中未找到与您问题相关的信息。";

                // 记录查询历史
                saveQueryHistory(question, answer, topK, enableRerank,
                    System.currentTimeMillis() - startTime, status, retrievedDocsCount, null);

                return answer;
            }

            // 2. 构建Prompt
            String prompt = promptService.buildRagPrompt(question, context);
            log.debug("Prompt构建完成，长度：{}", prompt.length());

            // 3. LLM生成答案
            answer = chatClient.chat(prompt);
            log.debug("LLM生成完成，答案长度：{}", answer.length());

            // 4. 添加引用
            String citations = promptService.buildCitations(context);

            // 5. 组合答案和引用
            String result = answer + "\n\n" + citations;

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("RAG查询完成：耗时{}ms", elapsedTime);

            // 记录查询历史
            saveQueryHistory(question, result, topK, enableRerank, elapsedTime, status, retrievedDocsCount, null);

            return result;

        } catch (Exception e) {
            log.error("RAG查询失败：question={}, error={}", question, e.getMessage(), e);
            status = "FAILED";
            errorMessage = e.getMessage();

            // 记录失败的查询历史
            saveQueryHistory(question, null, topK, enableRerank,
                System.currentTimeMillis() - startTime, status, retrievedDocsCount, errorMessage);

            throw new RuntimeException("RAG查询失败：" + e.getMessage(), e);
        }
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
            List<Document> documents = retriever.retrieve(question, topK, enableRerank);
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
            List<Document> context = retriever.retrieve(question, topK, enableRerank);

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
     * <p>支持自定义检索参数的流式查询。</p>
     *
     * @param question 用户问题
     * @param topK 检索文档数量
     * @param enableRerank 是否启用重排序
     * @return 流式答案文本
     */
    public Flux<String> queryStream(String question, int topK, boolean enableRerank) {
        log.info("开始流式RAG查询：question={}, topK={}, enableRerank={}", question, topK, enableRerank);

        return Flux.defer(() -> {
            try {
                // 1. 检索相关文档
                List<Document> context = retriever.retrieve(question, topK, enableRerank);
                log.debug("检索完成：获取{}个文档", context.size());

                // 检查是否有检索结果
                if (context.isEmpty()) {
                    log.warn("未检索到相关文档");
                    return Flux.just("抱歉，在知识库中未找到与您问题相关的信息。");
                }

                // 2. 构建Prompt
                String prompt = promptService.buildRagPrompt(question, context);
                log.debug("Prompt构建完成，长度：{}", prompt.length());

                // 3. 流式生成答案
                Flux<String> answerStream = streamingService.streamChat(prompt);

                // 4. 在流结束时添加引用
                String citations = promptService.buildCitations(context);

                return answerStream.concatWith(Flux.just("\n\n" + citations));

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
}


