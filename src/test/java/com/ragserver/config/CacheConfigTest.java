package com.ragserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * CacheConfig 单元测试
 *
 * <p>测试缓存配置的正确性：</p>
 * <ul>
 *   <li>缓存管理器初始化</li>
 *   <li>缓存名称配置</li>
 *   <li>缓存策略验证</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
class CacheConfigTest {

    @Autowired
    private CacheManager cacheManager;

    /**
     * 测试：缓存管理器初始化
     */
    @Test
    void testCacheManagerInitialized() {
        // Then
        assertThat(cacheManager).isNotNull();
    }

    /**
     * 测试：缓存名称配置
     */
    @Test
    void testCacheNames() {
        // Then
        assertThat(cacheManager.getCacheNames())
            .contains("ragQuery", "embedding", "retrieval", "stats", "documents", "imageCaption");
    }

    /**
     * 测试：获取RAG查询缓存
     */
    @Test
    void testGetRagQueryCache() {
        // When
        var cache = cacheManager.getCache("ragQuery");

        // Then
        assertThat(cache).isNotNull();
    }

    /**
     * 测试：获取Embedding缓存
     */
    @Test
    void testGetEmbeddingCache() {
        // When
        var cache = cacheManager.getCache("embedding");

        // Then
        assertThat(cache).isNotNull();
    }

    /**
     * 测试：获取检索结果缓存
     */
    @Test
    void testGetRetrievalCache() {
        // When
        var cache = cacheManager.getCache("retrieval");

        // Then
        assertThat(cache).isNotNull();
    }

    /**
     * 测试：获取统计数据缓存
     */
    @Test
    void testGetStatsCache() {
        // When
        var cache = cacheManager.getCache("stats");

        // Then
        assertThat(cache).isNotNull();
    }

    /**
     * 测试：获取文档缓存
     */
    @Test
    void testGetDocumentsCache() {
        // When
        var cache = cacheManager.getCache("documents");

        // Then
        assertThat(cache).isNotNull();
    }

    /**
     * 测试：获取图片描述缓存
     */
    @Test
    void testGetImageCaptionCache() {
        // When
        var cache = cacheManager.getCache("imageCaption");

        // Then
        assertThat(cache).isNotNull();
    }

    /**
     * 测试：缓存基本功能
     */
    @Test
    void testCacheBasicFunctionality() {
        // Given
        var cache = cacheManager.getCache("ragQuery");
        String key = "testKey";
        String value = "testValue";

        // When
        cache.put(key, value);
        String cached = cache.get(key, String.class);

        // Then
        assertThat(cached).isEqualTo(value);
    }

    /**
     * 测试：缓存清除
     */
    @Test
    void testCacheClear() {
        // Given
        var cache = cacheManager.getCache("ragQuery");
        String key = "testKey";
        String value = "testValue";
        cache.put(key, value);

        // When
        cache.clear();
        String cached = cache.get(key, String.class);

        // Then
        assertThat(cached).isNull();
    }

    /**
     * 测试：缓存淘汰
     */
    @Test
    void testCacheEviction() {
        // Given
        var cache = cacheManager.getCache("ragQuery");
        String key = "testKey";
        String value = "testValue";
        cache.put(key, value);

        // When
        cache.evict(key);
        String cached = cache.get(key, String.class);

        // Then
        assertThat(cached).isNull();
    }
}
