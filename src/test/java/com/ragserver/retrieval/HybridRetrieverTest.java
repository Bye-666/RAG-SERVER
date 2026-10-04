package com.ragserver.retrieval;

import com.ragserver.retrieval.milvus.MilvusHybridStore;
import com.ragserver.retrieval.model.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HybridRetriever 单元测试
 *
 * <p>测试混合检索器的核心功能：</p>
 * <ul>
 *   <li>基本检索流程</li>
 *   <li>Rerank集成</li>
 *   <li>并行检索与降级</li>
 *   <li>异常处理</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class HybridRetrieverTest {

    @Mock
    private MilvusHybridStore vectorStore;

    @Mock
    private RerankerService reranker;

    private HybridRetriever hybridRetriever;

    @BeforeEach
    void setUp() {
        hybridRetriever = new HybridRetriever(vectorStore, reranker);
    }

    /**
     * 测试：基本检索（无Rerank）
     */
    @Test
    void testRetrieveWithoutRerank() {
        // Given: Mock返回3个文档
        String query = "什么是RAG？";
        int topK = 3;
        List<Document> mockResults = createMockDocuments(3);
        when(vectorStore.searchHybrid(eq(query), eq(topK))).thenReturn(mockResults);

        // When: 检索（不启用Rerank）
        List<Document> results = hybridRetriever.retrieve(query, topK, false);

        // Then: 返回结果
        assertThat(results).isNotNull();
        assertThat(results).hasSize(3);
        verify(vectorStore).searchHybrid(eq(query), eq(topK));
        verify(reranker, never()).rerank(anyString(), anyList(), anyInt());
    }

    /**
     * 测试：启用Rerank
     */
    @Test
    void testRetrieveWithRerank() {
        // Given: Mock返回10个候选，Rerank返回5个
        String query = "什么是RAG？";
        int topK = 5;
        List<Document> candidates = createMockDocuments(10);
        List<Document> rerankedResults = createMockDocuments(5);

        when(vectorStore.searchHybrid(eq(query), eq(topK * 2))).thenReturn(candidates);
        when(reranker.rerank(eq(query), eq(candidates), eq(topK))).thenReturn(rerankedResults);

        // When: 检索（启用Rerank）
        List<Document> results = hybridRetriever.retrieve(query, topK, true);

        // Then: 返回Rerank结果
        assertThat(results).isNotNull();
        assertThat(results).hasSize(5);
        verify(vectorStore).searchHybrid(eq(query), eq(topK * 2));
        verify(reranker).rerank(eq(query), eq(candidates), eq(topK));
    }

    /**
     * 测试：空结果处理
     */
    @Test
    void testRetrieveWithEmptyResults() {
        // Given: Mock返回空列表
        String query = "不存在的内容";
        when(vectorStore.searchHybrid(anyString(), anyInt())).thenReturn(List.of());

        // When: 检索
        List<Document> results = hybridRetriever.retrieve(query, 5, false);

        // Then: 返回空列表（不会调用Rerank）
        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
        verify(reranker, never()).rerank(anyString(), anyList(), anyInt());
    }

    /**
     * 测试：检索失败异常处理
     */
    @Test
    void testRetrieveWithException() {
        // Given: Mock抛出异常
        String query = "测试查询";
        when(vectorStore.searchHybrid(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Milvus连接失败"));

        // When & Then: 抛出异常
        assertThatThrownBy(() -> hybridRetriever.retrieve(query, 5, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("检索失败");
    }

    /**
     * 测试：简单检索（便捷方法）
     */
    @Test
    void testSimpleRetrieve() {
        // Given: Mock返回结果
        String query = "测试";
        List<Document> mockResults = createMockDocuments(3);
        when(vectorStore.searchHybrid(eq(query), eq(5))).thenReturn(mockResults);

        // When: 调用简单检索
        List<Document> results = hybridRetriever.retrieve(query, 5);

        // Then: 返回结果（未启用Rerank）
        assertThat(results).hasSize(3);
        verify(reranker, never()).rerank(anyString(), anyList(), anyInt());
    }

    /**
     * 测试：并行检索成功
     */
    @Test
    void testRetrieveWithFallbackSuccess() {
        // Given: Mock Dense和Sparse都成功
        String query = "测试并行检索";
        List<Document> denseResults = createMockDocuments(5);
        List<Document> sparseResults = createMockDocuments(5);

        when(vectorStore.searchDense(eq(query), eq(5))).thenReturn(denseResults);
        when(vectorStore.searchSparse(eq(query), eq(5))).thenReturn(sparseResults);

        // When: 并行检索
        List<Document> results = hybridRetriever.retrieveWithFallback(query, 5);

        // Then: 返回Dense结果（简化实现）
        assertThat(results).isNotNull();
        assertThat(results).hasSize(5);
        verify(vectorStore).searchDense(eq(query), eq(5));
        verify(vectorStore).searchSparse(eq(query), eq(5));
    }

    /**
     * 测试：Dense失败降级到Sparse
     */
    @Test
    void testRetrieveWithFallbackDenseFails() {
        // Given: Dense失败，Sparse成功
        String query = "测试降级";
        List<Document> sparseResults = createMockDocuments(5);

        when(vectorStore.searchDense(eq(query), eq(5)))
                .thenThrow(new RuntimeException("Dense检索失败"));
        when(vectorStore.searchSparse(eq(query), eq(5))).thenReturn(sparseResults);

        // When: 并行检索（降级）
        List<Document> results = hybridRetriever.retrieveWithFallback(query, 5);

        // Then: 返回Sparse结果
        assertThat(results).isNotNull();
        assertThat(results).hasSize(5);
    }

    /**
     * 测试：Sparse失败降级到Dense
     */
    @Test
    void testRetrieveWithFallbackSparseFails() {
        // Given: Sparse失败，Dense成功
        String query = "测试降级";
        List<Document> denseResults = createMockDocuments(5);

        when(vectorStore.searchDense(eq(query), eq(5))).thenReturn(denseResults);
        when(vectorStore.searchSparse(eq(query), eq(5)))
                .thenThrow(new RuntimeException("Sparse检索失败"));

        // When: 并行检索（降级）
        List<Document> results = hybridRetriever.retrieveWithFallback(query, 5);

        // Then: 返回Dense结果
        assertThat(results).isNotNull();
        assertThat(results).hasSize(5);
    }

    /**
     * 测试：两路都返回空结果
     */
    @Test
    void testRetrieveWithFallbackBothEmpty() {
        // Given: 两路都返回空列表（不是抛异常）
        String query = "测试两路都空";

        when(vectorStore.searchDense(eq(query), eq(5))).thenReturn(List.of());
        when(vectorStore.searchSparse(eq(query), eq(5))).thenReturn(List.of());

        // When: 并行检索
        List<Document> results = hybridRetriever.retrieveWithFallback(query, 5);

        // Then: 返回空列表
        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    /**
     * 创建Mock文档列表
     */
    private List<Document> createMockDocuments(int count) {
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Document doc = Document.builder()
                    .id("doc_" + i)
                    .text("这是第" + i + "个测试文档的内容")
                    .metadata(Map.of("index", i))
                    .score(0.9f - i * 0.1f)
                    .build();
            documents.add(doc);
        }
        return documents;
    }
}
