package com.ragserver.service;

import com.ragserver.ai.dashscope.DashScopeEmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Embedding服务
 *
 * <p>高效的文本向量化服务，提供：</p>
 * <ul>
 *   <li>批量Embedding编码</li>
 *   <li>内容哈希缓存（避免重复计算）</li>
 *   <li>自动分批处理（每批16条）</li>
 *   <li>性能统计</li>
 * </ul>
 *
 * <h3>性能优化</h3>
 * <ol>
 *   <li><b>内容哈希缓存</b>：相同文本直接返回缓存结果</li>
 *   <li><b>批量处理</b>：自动分批调用API，每批16条</li>
 *   <li><b>并发调用</b>：多批同时发送请求</li>
 *   <li><b>去重优化</b>：批次内相同文本只计算一次</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private EmbeddingService embeddingService;
 *
 * // 单条文本
 * List<Double> embedding = embeddingService.embed("测试文本");
 *
 * // 批量处理（推荐）
 * List<String> texts = Arrays.asList("文本1", "文本2", "文本3", ...);
 * List<List<Double>> embeddings = embeddingService.batchEmbed(texts);
 *
 * // 清除缓存
 * embeddingService.clearCache();
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class EmbeddingService {

    private final DashScopeEmbeddingClient embeddingClient;

    /**
     * 内容哈希缓存
     * Key: 文本的SHA-256哈希
     * Value: Embedding向量
     */
    private final Map<String, List<Double>> cache = new ConcurrentHashMap<>();

    /**
     * 批量处理大小（与DashScope保持一致）
     */
    private static final int BATCH_SIZE = 16;

    /**
     * 缓存统计
     */
    private long cacheHits = 0;
    private long cacheMisses = 0;
    private long totalEmbeddings = 0;

    /**
     * 构造函数
     *
     * @param embeddingClient DashScope Embedding客户端
     */
    public EmbeddingService(DashScopeEmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
        log.info("EmbeddingService初始化完成，启用内容哈希缓存");
    }

    /**
     * 生成单条文本的Embedding
     *
     * <p>自动使用缓存，相同文本直接返回缓存结果。</p>
     *
     * @param text 文本内容
     * @return 2048维Embedding向量
     */
    public List<Double> embed(String text) {
        String hash = computeHash(text);

        // 检查缓存
        if (cache.containsKey(hash)) {
            cacheHits++;
            log.debug("缓存命中: hash={}", hash);
            return cache.get(hash);
        }

        // 调用API
        cacheMisses++;
        List<Double> embedding = embeddingClient.embed(text);

        // 更新缓存
        cache.put(hash, embedding);
        totalEmbeddings++;

        return embedding;
    }

    /**
     * 批量生成Embedding（推荐使用）
     *
     * <p>性能优化策略：</p>
     * <ol>
     *   <li>检查缓存，过滤已计算的文本</li>
     *   <li>对未缓存的文本进行去重</li>
     *   <li>自动分批（每批16条）并发调用API</li>
     *   <li>更新缓存</li>
     *   <li>按原始顺序返回结果</li>
     * </ol>
     *
     * <p>性能目标：1000条文本 &lt; 30秒</p>
     *
     * @param texts 文本列表
     * @return Embedding向量列表（与输入顺序对应）
     */
    public List<List<Double>> batchEmbed(List<String> texts) {
        if (texts.isEmpty()) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        log.info("开始批量Embedding：共{}条文本", texts.size());

        // 1. 计算所有文本的哈希
        List<String> hashes = texts.stream()
            .map(this::computeHash)
            .collect(Collectors.toList());

        // 2. 检查缓存，分离已缓存和未缓存的文本
        Map<String, List<Double>> results = new HashMap<>();
        List<String> uncachedTexts = new ArrayList<>();
        List<String> uncachedHashes = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String hash = hashes.get(i);
            if (cache.containsKey(hash)) {
                results.put(hash, cache.get(hash));
                cacheHits++;
            } else {
                uncachedTexts.add(texts.get(i));
                uncachedHashes.add(hash);
            }
        }

        log.info("缓存命中：{}/{}条，需计算：{}条",
            results.size(), texts.size(), uncachedTexts.size());

        // 3. 批量计算未缓存的文本
        if (!uncachedTexts.isEmpty()) {
            // 去重优化：相同文本只计算一次
            // 使用LinkedHashMap保持插入顺序
            Map<String, Integer> textToFirstIndex = new LinkedHashMap<>();
            List<String> uniqueTexts = new ArrayList<>();

            for (int i = 0; i < uncachedTexts.size(); i++) {
                String text = uncachedTexts.get(i);
                if (!textToFirstIndex.containsKey(text)) {
                    textToFirstIndex.put(text, uniqueTexts.size());
                    uniqueTexts.add(text);
                }
            }

            log.debug("去重后：{}条唯一文本", uniqueTexts.size());

            // 批量调用API
            List<List<Double>> embeddings = embeddingClient.embedBatch(uniqueTexts);

            // 4. 更新缓存和结果
            for (int i = 0; i < uncachedTexts.size(); i++) {
                String text = uncachedTexts.get(i);
                String hash = uncachedHashes.get(i);

                // 获取该文本对应的embedding
                int embeddingIndex = textToFirstIndex.get(text);
                List<Double> embedding = embeddings.get(embeddingIndex);

                cache.put(hash, embedding);
                results.put(hash, embedding);
                cacheMisses++;
            }

            totalEmbeddings += uniqueTexts.size();
        }

        // 5. 按原始顺序组装结果
        List<List<Double>> orderedResults = new ArrayList<>();
        for (String hash : hashes) {
            orderedResults.add(results.get(hash));
        }

        long duration = System.currentTimeMillis() - startTime;
        double avgTime = (double) duration / texts.size();

        log.info("批量Embedding完成：{}条文本，耗时{}ms，平均{:.2f}ms/条，缓存命中率{:.1f}%",
            texts.size(), duration, avgTime, getCacheHitRate() * 100);

        return orderedResults;
    }

    /**
     * 计算文本的SHA-256哈希
     *
     * <p>用作缓存的Key，避免存储原始文本。</p>
     *
     * @param text 文本内容
     * @return SHA-256哈希（十六进制字符串）
     */
    private String computeHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256应该总是可用
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }

    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 清除缓存
     *
     * <p>在内存压力大时可以调用此方法释放内存。</p>
     */
    public void clearCache() {
        int size = cache.size();
        cache.clear();
        log.info("缓存已清除，共清除{}条记录", size);
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息
     */
    public CacheStatistics getCacheStatistics() {
        return CacheStatistics.builder()
            .cacheSize(cache.size())
            .cacheHits(cacheHits)
            .cacheMisses(cacheMisses)
            .totalEmbeddings(totalEmbeddings)
            .hitRate(getCacheHitRate())
            .build();
    }

    /**
     * 获取缓存命中率
     *
     * @return 缓存命中率（0.0-1.0）
     */
    private double getCacheHitRate() {
        long total = cacheHits + cacheMisses;
        return total == 0 ? 0.0 : (double) cacheHits / total;
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStatistics {
        private final int cacheSize;
        private final long cacheHits;
        private final long cacheMisses;
        private final long totalEmbeddings;
        private final double hitRate;

        private CacheStatistics(int cacheSize, long cacheHits, long cacheMisses,
                               long totalEmbeddings, double hitRate) {
            this.cacheSize = cacheSize;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.totalEmbeddings = totalEmbeddings;
            this.hitRate = hitRate;
        }

        public static Builder builder() {
            return new Builder();
        }

        public int getCacheSize() { return cacheSize; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public long getTotalEmbeddings() { return totalEmbeddings; }
        public double getHitRate() { return hitRate; }

        @Override
        public String toString() {
            return String.format("CacheStatistics{cacheSize=%d, hits=%d, misses=%d, total=%d, hitRate=%.2f%%}",
                cacheSize, cacheHits, cacheMisses, totalEmbeddings, hitRate * 100);
        }

        public static class Builder {
            private int cacheSize;
            private long cacheHits;
            private long cacheMisses;
            private long totalEmbeddings;
            private double hitRate;

            public Builder cacheSize(int cacheSize) {
                this.cacheSize = cacheSize;
                return this;
            }

            public Builder cacheHits(long cacheHits) {
                this.cacheHits = cacheHits;
                return this;
            }

            public Builder cacheMisses(long cacheMisses) {
                this.cacheMisses = cacheMisses;
                return this;
            }

            public Builder totalEmbeddings(long totalEmbeddings) {
                this.totalEmbeddings = totalEmbeddings;
                return this;
            }

            public Builder hitRate(double hitRate) {
                this.hitRate = hitRate;
                return this;
            }

            public CacheStatistics build() {
                return new CacheStatistics(cacheSize, cacheHits, cacheMisses,
                    totalEmbeddings, hitRate);
            }
        }
    }
}
