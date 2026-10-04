package com.ragserver.ai.dashscope;

import com.ragserver.config.DashScopeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * DashScope Embedding客户端
 *
 * <p>封装DashScope Embedding API调用，提供：</p>
 * <ul>
 *   <li>单条文本Embedding</li>
 *   <li>批量Embedding（自动分批，每批16条）</li>
 *   <li>并发调用优化</li>
 *   <li>重试机制</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Service
 * public class EmbeddingService {
 *     @Autowired
 *     private DashScopeEmbeddingClient embeddingClient;
 *
 *     public List<Double> embedText(String text) {
 *         return embeddingClient.embed(text);
 *     }
 *
 *     public List<List<Double>> embedBatch(List<String> texts) {
 *         return embeddingClient.embedBatch(texts);
 *     }
 * }
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class DashScopeEmbeddingClient {

    private final DashScopeProperties properties;
    private final RestTemplate restTemplate;
    private final ExecutorService executorService;
    private final RateLimiter rateLimiter;

    /**
     * Embedding API端点
     */
    private static final String EMBEDDING_ENDPOINT = "/embeddings";

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRIES = 3;

    /**
     * 初始重试延迟（毫秒）
     */
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    /**
     * 批量处理大小（DashScope限制最多16条）
     */
    private static final int BATCH_SIZE = 16;

    public DashScopeEmbeddingClient(DashScopeProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.rateLimiter = new RateLimiter(properties.getQps());

        // 创建线程池用于并发调用（核心线程数=CPU核心数，最大线程数=核心数*2）
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = corePoolSize * 2;
        this.executorService = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("DashScopeEmbeddingClient初始化完成，线程池大小：{}/{}，QPS限制：{}",
            corePoolSize, maxPoolSize, properties.getQps());
    }

    /**
     * 生成单条文本的Embedding
     *
     * <p>调用DashScope API生成2048维向量。</p>
     *
     * @param text 文本内容
     * @return 2048维Embedding向量
     * @throws DashScopeChatClient.DashScopeException 调用失败时抛出
     */
    public List<Double> embed(String text) {
        EmbeddingRequest request = EmbeddingRequest.single(properties.getEmbeddingModel(), text);
        EmbeddingResponse response = call(request);
        return response.getEmbedding();
    }

    /**
     * 批量生成Embedding（自动分批并发调用）
     *
     * <p>自动分批处理，每批最多16条文本，多批并发调用以提升性能。</p>
     *
     * <p>性能优化：</p>
     * <ul>
     *   <li>自动分批：每批16条（DashScope限制）</li>
     *   <li>并发调用：多批同时发送请求</li>
     *   <li>线程池：复用线程资源</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 100条文本，自动分7批（6批16条 + 1批4条），并发调用
     * List<String> texts = List.of("文本1", "文本2", ..., "文本100");
     * List<List<Double>> embeddings = embeddingClient.embedBatch(texts);
     * }</pre>
     *
     * @param texts 文本列表
     * @return Embedding向量列表（与输入顺序对应）
     * @throws DashScopeChatClient.DashScopeException 调用失败时抛出
     */
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        long startTime = System.currentTimeMillis();

        // 分批
        List<List<String>> batches = splitIntoBatches(texts, BATCH_SIZE);
        log.info("批量Embedding：共{}条文本，分{}批处理", texts.size(), batches.size());

        // 并发调用
        List<CompletableFuture<List<List<Double>>>> futures = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            final int batchIndex = i;
            List<String> batch = batches.get(i);

            CompletableFuture<List<List<Double>>> future = CompletableFuture.supplyAsync(() -> {
                log.debug("处理第{}批：{}条文本", batchIndex + 1, batch.size());
                EmbeddingRequest request = EmbeddingRequest.batch(properties.getEmbeddingModel(), batch);
                EmbeddingResponse response = call(request);
                return response.getAllEmbeddings();
            }, executorService);

            futures.add(future);
        }

        // 等待所有批次完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DashScopeChatClient.DashScopeException("批量Embedding被中断", e);
        } catch (ExecutionException e) {
            throw new DashScopeChatClient.DashScopeException("批量Embedding失败", e.getCause());
        }

        // 合并结果
        List<List<Double>> allEmbeddings = new ArrayList<>();
        for (CompletableFuture<List<List<Double>>> future : futures) {
            try {
                allEmbeddings.addAll(future.get());
            } catch (Exception e) {
                // 不应该发生，因为已经等待过了
                throw new DashScopeChatClient.DashScopeException("获取批次结果失败", e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("批量Embedding完成：{}条文本，耗时{}ms，平均{:.2f}ms/条",
            texts.size(), duration, (double) duration / texts.size());

        return allEmbeddings;
    }

    /**
     * 调用Embedding API
     *
     * <p>发送Embedding请求并返回响应，支持自动重试。</p>
     *
     * @param request Embedding请求
     * @return Embedding响应
     * @throws DashScopeChatClient.DashScopeException 调用失败时抛出
     */
    private EmbeddingResponse call(EmbeddingRequest request) {
        // 设置默认模型
        if (request.getModel() == null) {
            request.setModel(properties.getEmbeddingModel());
        }

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                log.debug("调用DashScope Embedding API，尝试次数：{}/{}", attempt + 1, MAX_RETRIES);
                return doCall(request);
            } catch (HttpClientErrorException e) {
                // 4xx错误不重试
                log.error("DashScope Embedding API调用失败（客户端错误）：{} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
                throw new DashScopeChatClient.DashScopeException(
                    "DashScope Embedding API调用失败：" + e.getMessage(), e);
            } catch (HttpServerErrorException e) {
                // 5xx错误可重试
                lastException = e;
                log.warn("DashScope Embedding API调用失败（服务器错误），尝试次数：{}/{}，错误：{}",
                    attempt + 1, MAX_RETRIES, e.getMessage());
            } catch (Exception e) {
                // 其他异常可重试
                lastException = e;
                log.warn("DashScope Embedding API调用失败，尝试次数：{}/{}，错误：{}",
                    attempt + 1, MAX_RETRIES, e.getMessage());
            }

            attempt++;

            // 如果还有重试机会，等待后重试
            if (attempt < MAX_RETRIES) {
                long delayMs = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1));
                log.info("等待{}毫秒后重试...", delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new DashScopeChatClient.DashScopeException("重试被中断", ie);
                }
            }
        }

        // 所有重试都失败
        throw new DashScopeChatClient.DashScopeException(
            String.format("DashScope Embedding API调用失败，已重试%d次", MAX_RETRIES),
            lastException
        );
    }

    /**
     * 执行实际的HTTP调用
     *
     * @param request Embedding请求
     * @return Embedding响应
     */
    private EmbeddingResponse doCall(EmbeddingRequest request) {
        // 限流：获取令牌（可能阻塞等待）
        rateLimiter.acquire();

        // 构建请求URL
        String url = properties.getBaseUrl() + EMBEDDING_ENDPOINT;

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        // 构建请求体
        HttpEntity<EmbeddingRequest> entity = new HttpEntity<>(request, headers);

        // 发送请求
        log.debug("发送请求到DashScope：URL={}, Model={}, Input={}条",
            url, request.getModel(), request.getInput().size());

        ResponseEntity<EmbeddingResponse> responseEntity = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            EmbeddingResponse.class
        );

        EmbeddingResponse response = responseEntity.getBody();

        if (response == null) {
            throw new DashScopeChatClient.DashScopeException("DashScope Embedding API返回空响应");
        }

        if (!response.isSuccess()) {
            throw new DashScopeChatClient.DashScopeException(
                "DashScope Embedding API返回无效响应：" + response);
        }

        log.debug("DashScope Embedding API调用成功，维度：{}，Token使用：{}",
            response.getDimension(), response.getUsage());

        return response;
    }

    /**
     * 将列表分批
     *
     * @param list 原列表
     * @param batchSize 每批大小
     * @return 分批后的列表
     */
    private <T> List<List<T>> splitIntoBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(list.subList(i, end));
        }
        return batches;
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
