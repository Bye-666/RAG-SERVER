package com.ragserver.service;

import com.ragserver.ai.dashscope.DashScopeEmbeddingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * EmbeddingService单元测试
 *
 * @author RAG-SERVER开发团队
 */
@DisplayName("EmbeddingService - Embedding服务测试")
class EmbeddingServiceTest {

    private EmbeddingService embeddingService;
    private DashScopeEmbeddingClient mockClient;

    @BeforeEach
    void setUp() {
        mockClient = mock(DashScopeEmbeddingClient.class);
        embeddingService = new EmbeddingService(mockClient);
    }

    @Test
    @DisplayName("测试1: 单条文本Embedding")
    void testSingleEmbed() {
        // Mock返回2048维向量
        List<Double> mockEmbedding = generateMockEmbedding(2048);
        when(mockClient.embed(anyString())).thenReturn(mockEmbedding);

        List<Double> result = embeddingService.embed("测试文本");

        assertNotNull(result);
        assertEquals(2048, result.size());
        verify(mockClient, times(1)).embed("测试文本");
    }

    @Test
    @DisplayName("测试2: 相同文本使用缓存")
    void testCacheHit() {
        List<Double> mockEmbedding = generateMockEmbedding(2048);
        when(mockClient.embed(anyString())).thenReturn(mockEmbedding);

        // 第一次调用
        List<Double> result1 = embeddingService.embed("测试文本");

        // 第二次调用相同文本
        List<Double> result2 = embeddingService.embed("测试文本");

        // 应该返回相同的结果
        assertEquals(result1, result2);

        // API应该只被调用一次
        verify(mockClient, times(1)).embed("测试文本");

        // 检查缓存命中率
        EmbeddingService.CacheStatistics stats = embeddingService.getCacheStatistics();
        assertEquals(1, stats.getCacheHits());
        assertEquals(1, stats.getCacheMisses());
        assertEquals(0.5, stats.getHitRate(), 0.01);
    }

    @Test
    @DisplayName("测试3: 不同文本不使用缓存")
    void testCacheMiss() {
        List<Double> mockEmbedding1 = generateMockEmbedding(2048);
        List<Double> mockEmbedding2 = generateMockEmbedding(2048);

        when(mockClient.embed("文本1")).thenReturn(mockEmbedding1);
        when(mockClient.embed("文本2")).thenReturn(mockEmbedding2);

        embeddingService.embed("文本1");
        embeddingService.embed("文本2");

        // 应该调用API两次
        verify(mockClient, times(1)).embed("文本1");
        verify(mockClient, times(1)).embed("文本2");

        // 缓存统计
        EmbeddingService.CacheStatistics stats = embeddingService.getCacheStatistics();
        assertEquals(0, stats.getCacheHits());
        assertEquals(2, stats.getCacheMisses());
    }

    @Test
    @DisplayName("测试4: 批量Embedding - 空列表")
    void testBatchEmbedEmpty() {
        List<List<Double>> result = embeddingService.batchEmbed(Collections.emptyList());

        assertTrue(result.isEmpty());
        verify(mockClient, never()).embedBatch(anyList());
    }

    @Test
    @DisplayName("测试5: 批量Embedding - 少量文本")
    void testBatchEmbedSmall() {
        List<String> texts = Arrays.asList("文本1", "文本2", "文本3");
        List<List<Double>> mockEmbeddings = texts.stream()
            .map(t -> generateMockEmbedding(2048))
            .collect(Collectors.toList());

        when(mockClient.embedBatch(anyList())).thenReturn(mockEmbeddings);

        List<List<Double>> results = embeddingService.batchEmbed(texts);

        assertNotNull(results);
        assertEquals(3, results.size());
        verify(mockClient, times(1)).embedBatch(texts);
    }

    @Test
    @DisplayName("测试6: 批量Embedding - 包含重复文本")
    void testBatchEmbedWithDuplicates() {
        List<String> texts = Arrays.asList("文本A", "文本B", "文本A", "文本C", "文本B");

        // 只有3个唯一文本
        List<List<Double>> mockEmbeddings = Arrays.asList(
            generateMockEmbedding(2048),
            generateMockEmbedding(2048),
            generateMockEmbedding(2048)
        );

        when(mockClient.embedBatch(anyList())).thenReturn(mockEmbeddings);

        List<List<Double>> results = embeddingService.batchEmbed(texts);

        // 应该返回5个结果（与输入对应）
        assertEquals(5, results.size());

        // 相同文本应该有相同的Embedding
        assertEquals(results.get(0), results.get(2)); // 文本A
        assertEquals(results.get(1), results.get(4)); // 文本B

        // API应该只被调用一次，且只处理3个唯一文本
        verify(mockClient, times(1)).embedBatch(anyList());
    }

    @Test
    @DisplayName("测试7: 批量Embedding - 部分缓存命中")
    void testBatchEmbedPartialCache() {
        // 先缓存一些文本
        List<Double> cachedEmbedding = generateMockEmbedding(2048);
        when(mockClient.embed("已缓存文本")).thenReturn(cachedEmbedding);
        embeddingService.embed("已缓存文本");

        // 批量处理，包含已缓存和未缓存的文本
        List<String> texts = Arrays.asList("已缓存文本", "新文本1", "新文本2");
        List<List<Double>> mockNewEmbeddings = Arrays.asList(
            generateMockEmbedding(2048),
            generateMockEmbedding(2048)
        );

        when(mockClient.embedBatch(Arrays.asList("新文本1", "新文本2")))
            .thenReturn(mockNewEmbeddings);

        List<List<Double>> results = embeddingService.batchEmbed(texts);

        assertEquals(3, results.size());

        // 第一个结果应该来自缓存
        assertEquals(cachedEmbedding, results.get(0));

        // 应该只调用API处理未缓存的文本
        verify(mockClient, times(1)).embedBatch(Arrays.asList("新文本1", "新文本2"));
    }

    @Test
    @DisplayName("测试8: 批量Embedding - 保持顺序")
    void testBatchEmbedOrder() {
        List<String> texts = Arrays.asList("文本1", "文本2", "文本3", "文本4", "文本5");
        List<List<Double>> mockEmbeddings = IntStream.range(0, 5)
            .mapToObj(i -> generateMockEmbeddingWithValue(2048, i))
            .collect(Collectors.toList());

        when(mockClient.embedBatch(anyList())).thenReturn(mockEmbeddings);

        List<List<Double>> results = embeddingService.batchEmbed(texts);

        // 验证顺序
        for (int i = 0; i < 5; i++) {
            assertEquals(mockEmbeddings.get(i), results.get(i));
        }
    }

    @Test
    @DisplayName("测试9: 批量Embedding - 大量文本（性能测试）")
    void testBatchEmbedLargeScale() {
        // 模拟1000条文本
        List<String> texts = IntStream.range(0, 1000)
            .mapToObj(i -> "文本" + i)
            .collect(Collectors.toList());

        // Mock批量返回
        when(mockClient.embedBatch(anyList())).thenAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            return batch.stream()
                .map(t -> generateMockEmbedding(2048))
                .collect(Collectors.toList());
        });

        long startTime = System.currentTimeMillis();
        List<List<Double>> results = embeddingService.batchEmbed(texts);
        long duration = System.currentTimeMillis() - startTime;

        assertEquals(1000, results.size());
        System.out.println("处理1000条文本耗时: " + duration + "ms");

        // 验收标准：1000条文本 < 30秒（实际应该远小于这个值，因为是mock）
        assertTrue(duration < 30000, "处理1000条文本应该在30秒内完成");
    }

    @Test
    @DisplayName("测试10: 清除缓存")
    void testClearCache() {
        List<Double> mockEmbedding = generateMockEmbedding(2048);
        when(mockClient.embed(anyString())).thenReturn(mockEmbedding);

        // 添加一些缓存
        embeddingService.embed("文本1");
        embeddingService.embed("文本2");
        embeddingService.embed("文本3");

        EmbeddingService.CacheStatistics statsBefore = embeddingService.getCacheStatistics();
        assertEquals(3, statsBefore.getCacheSize());

        // 清除缓存
        embeddingService.clearCache();

        EmbeddingService.CacheStatistics statsAfter = embeddingService.getCacheStatistics();
        assertEquals(0, statsAfter.getCacheSize());

        // 再次调用相同文本，应该重新计算
        embeddingService.embed("文本1");
        verify(mockClient, times(2)).embed("文本1"); // 清除前1次 + 清除后1次
    }

    @Test
    @DisplayName("测试11: 缓存统计信息")
    void testCacheStatistics() {
        List<Double> mockEmbedding = generateMockEmbedding(2048);
        when(mockClient.embed(anyString())).thenReturn(mockEmbedding);

        // 第一次调用（miss）
        embeddingService.embed("文本A");

        // 第二次调用相同文本（hit）
        embeddingService.embed("文本A");

        // 第三次调用新文本（miss）
        embeddingService.embed("文本B");

        EmbeddingService.CacheStatistics stats = embeddingService.getCacheStatistics();

        assertEquals(2, stats.getCacheSize());
        assertEquals(1, stats.getCacheHits());
        assertEquals(2, stats.getCacheMisses());
        assertEquals(2, stats.getTotalEmbeddings());
        assertEquals(1.0 / 3.0, stats.getHitRate(), 0.01);

        // 测试toString
        String statsStr = stats.toString();
        assertNotNull(statsStr);
        assertTrue(statsStr.contains("cacheSize=2"));
    }

    @Test
    @DisplayName("测试12: 批量Embedding - 全部缓存命中")
    void testBatchEmbedAllCached() {
        List<Double> mockEmbedding1 = generateMockEmbedding(2048);
        List<Double> mockEmbedding2 = generateMockEmbedding(2048);

        when(mockClient.embed("文本1")).thenReturn(mockEmbedding1);
        when(mockClient.embed("文本2")).thenReturn(mockEmbedding2);

        // 先缓存
        embeddingService.embed("文本1");
        embeddingService.embed("文本2");

        // 批量调用，应该全部命中缓存
        List<String> texts = Arrays.asList("文本1", "文本2", "文本1");
        List<List<Double>> results = embeddingService.batchEmbed(texts);

        assertEquals(3, results.size());

        // 不应该调用embedBatch
        verify(mockClient, never()).embedBatch(anyList());

        // 缓存命中率应该提高
        EmbeddingService.CacheStatistics stats = embeddingService.getCacheStatistics();
        assertTrue(stats.getHitRate() > 0.5);
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成模拟的Embedding向量
     */
    private List<Double> generateMockEmbedding(int dimension) {
        List<Double> embedding = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < dimension; i++) {
            embedding.add(random.nextDouble());
        }
        return embedding;
    }

    /**
     * 生成带特定值的模拟Embedding向量（用于测试顺序）
     */
    private List<Double> generateMockEmbeddingWithValue(int dimension, double value) {
        List<Double> embedding = new ArrayList<>();
        for (int i = 0; i < dimension; i++) {
            embedding.add(value);
        }
        return embedding;
    }
}
