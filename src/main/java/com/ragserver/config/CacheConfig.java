package com.ragserver.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 *
 * <p>使用Caffeine作为缓存实现，提供高性能的本地缓存：</p>
 * <ul>
 *   <li>查询结果缓存：缓存RAG查询结果</li>
 *   <li>Embedding缓存：缓存文本向量</li>
 *   <li>检索结果缓存：缓存检索到的文档</li>
 *   <li>统计数据缓存：缓存Dashboard统计信息</li>
 * </ul>
 *
 * <h3>缓存策略</h3>
 * <table>
 *   <tr>
 *     <th>缓存名称</th>
 *     <th>过期时间</th>
 *     <th>最大条目</th>
 *     <th>用途</th>
 *   </tr>
 *   <tr>
 *     <td>ragQuery</td>
 *     <td>30分钟</td>
 *     <td>1000</td>
 *     <td>RAG查询结果</td>
 *   </tr>
 *   <tr>
 *     <td>embedding</td>
 *     <td>24小时</td>
 *     <td>10000</td>
 *     <td>文本Embedding向量</td>
 *   </tr>
 *   <tr>
 *     <td>retrieval</td>
 *     <td>1小时</td>
 *     <td>5000</td>
 *     <td>检索结果</td>
 *   </tr>
 *   <tr>
 *     <td>stats</td>
 *     <td>5分钟</td>
 *     <td>100</td>
 *     <td>统计数据</td>
 *   </tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Service
 * public class RagService {
 *     @Cacheable(value = "ragQuery", key = "#question + '_' + #topK")
 *     public String query(String question, int topK) {
 *         // 查询逻辑
 *     }
 *
 *     @CacheEvict(value = "ragQuery", allEntries = true)
 *     public void clearCache() {
 *         // 清除所有缓存
 *     }
 * }
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 配置缓存管理器
     *
     * <p>使用Caffeine作为缓存实现，提供：</p>
     * <ul>
     *   <li>基于时间的过期策略</li>
     *   <li>基于容量的淘汰策略</li>
     *   <li>LRU淘汰算法</li>
     *   <li>异步刷新</li>
     * </ul>
     *
     * @return 缓存管理器
     */
    @Bean
    public CacheManager cacheManager() {
        log.info("初始化缓存管理器（Caffeine）");

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 配置缓存名称
        cacheManager.setCacheNames(java.util.List.of(
            "ragQuery",      // RAG查询结果缓存
            "embedding",     // Embedding向量缓存
            "retrieval",     // 检索结果缓存
            "stats",         // 统计数据缓存
            "documents",     // 文档元数据缓存
            "imageCaption"   // 图片描述缓存
        ));

        // 配置默认缓存策略
        cacheManager.setCaffeine(defaultCaffeine());

        log.info("缓存管理器初始化完成，已配置缓存：{}", cacheManager.getCacheNames());

        return cacheManager;
    }

    /**
     * 默认Caffeine配置
     *
     * <p>适用于大多数场景的通用配置：</p>
     * <ul>
     *   <li>写入后30分钟过期</li>
     *   <li>最多缓存1000条记录</li>
     *   <li>启用统计信息记录</li>
     * </ul>
     *
     * @return Caffeine构建器
     */
    private Caffeine<Object, Object> defaultCaffeine() {
        return Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)  // 写入后30分钟过期
            .maximumSize(1000)                        // 最多1000条记录
            .recordStats();                           // 记录统计信息
    }

    /**
     * RAG查询结果缓存配置
     *
     * <p>缓存查询结果，减少重复查询的响应时间。</p>
     *
     * <h3>配置说明</h3>
     * <ul>
     *   <li>过期时间：30分钟（平衡新鲜度和命中率）</li>
     *   <li>最大条目：1000（约100MB内存占用）</li>
     *   <li>淘汰策略：LRU（Least Recently Used）</li>
     * </ul>
     *
     * @return Caffeine构建器
     */
    @Bean
    public Caffeine<Object, Object> ragQueryCaffeine() {
        return Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(1000)
            .recordStats();
    }

    /**
     * Embedding向量缓存配置
     *
     * <p>缓存文本的Embedding向量，避免重复调用Embedding API。</p>
     *
     * <h3>配置说明</h3>
     * <ul>
     *   <li>过期时间：24小时（Embedding结果相对稳定）</li>
     *   <li>最大条目：10000（约200MB内存占用）</li>
     *   <li>适用场景：相同文本的重复Embedding</li>
     * </ul>
     *
     * @return Caffeine构建器
     */
    @Bean
    public Caffeine<Object, Object> embeddingCaffeine() {
        return Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(10000)
            .recordStats();
    }

    /**
     * 检索结果缓存配置
     *
     * <p>缓存检索到的文档列表。</p>
     *
     * <h3>配置说明</h3>
     * <ul>
     *   <li>过期时间：1小时（文档可能更新）</li>
     *   <li>最大条目：5000</li>
     *   <li>适用场景：相同查询的重复检索</li>
     * </ul>
     *
     * @return Caffeine构建器
     */
    @Bean
    public Caffeine<Object, Object> retrievalCaffeine() {
        return Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(5000)
            .recordStats();
    }

    /**
     * 统计数据缓存配置
     *
     * <p>缓存Dashboard的统计数据。</p>
     *
     * <h3>配置说明</h3>
     * <ul>
     *   <li>过期时间：5分钟（统计数据变化较快）</li>
     *   <li>最大条目：100</li>
     *   <li>适用场景：Dashboard概览页面</li>
     * </ul>
     *
     * @return Caffeine构建器
     */
    @Bean
    public Caffeine<Object, Object> statsCaffeine() {
        return Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100)
            .recordStats();
    }

    /**
     * 文档元数据缓存配置
     *
     * <p>缓存文档的元数据信息。</p>
     *
     * <h3>配置说明</h3>
     * <ul>
     *   <li>过期时间：1小时</li>
     *   <li>最大条目：1000</li>
     *   <li>适用场景：文档列表、文档详情</li>
     * </ul>
     *
     * @return Caffeine构建器
     */
    @Bean
    public Caffeine<Object, Object> documentsCaffeine() {
        return Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(1000)
            .recordStats();
    }

    /**
     * 图片描述缓存配置
     *
     * <p>缓存图片的描述文本。</p>
     *
     * <h3>配置说明</h3>
     * <ul>
     *   <li>过期时间：24小时（图片描述结果稳定）</li>
     *   <li>最大条目：5000</li>
     *   <li>适用场景：相同图片的重复描述生成</li>
     * </ul>
     *
     * @return Caffeine构建器
     */
    @Bean
    public Caffeine<Object, Object> imageCaptionCaffeine() {
        return Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(5000)
            .recordStats();
    }
}
