package com.ragserver.service;

import com.ragserver.entity.IngestionHistory;
import com.ragserver.repository.IngestionHistoryRepository;
import com.ragserver.retrieval.milvus.MilvusHybridStore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DocumentService 集成测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@SpringBootTest
class DocumentServiceTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private IngestionHistoryRepository ingestionHistoryRepository;

    @MockBean
    private MilvusClientV2 milvusClient;

    @MockBean
    private MilvusHybridStore vectorStore;

    @MockBean
    private ImageStorageService imageStorageService;

    @BeforeEach
    void setUp() {
        // 清空测试数据
        ingestionHistoryRepository.deleteAll();
    }

    /**
     * 测试：列出所有文档
     */
    @Test
    void testListDocuments() {
        // Given: 插入测试数据
        createTestHistory("hash1", "/path/doc1.pdf", IngestionHistory.IngestionStatus.SUCCESS);
        createTestHistory("hash2", "/path/doc2.pdf", IngestionHistory.IngestionStatus.FAILED);

        // When: 列出所有文档
        List<DocumentService.DocumentInfo> docs = documentService.listDocuments();

        // Then: 返回所有文档
        assertThat(docs).hasSize(2);
    }

    /**
     * 测试：根据状态列出文档
     */
    @Test
    void testListDocumentsByStatus() {
        // Given: 插入不同状态的文档
        createTestHistory("hash1", "/path/doc1.pdf", IngestionHistory.IngestionStatus.SUCCESS);
        createTestHistory("hash2", "/path/doc2.pdf", IngestionHistory.IngestionStatus.SUCCESS);
        createTestHistory("hash3", "/path/doc3.pdf", IngestionHistory.IngestionStatus.FAILED);

        // When: 查询成功的文档
        List<DocumentService.DocumentInfo> successDocs =
                documentService.listDocumentsByStatus(IngestionHistory.IngestionStatus.SUCCESS);

        // Then: 只返回成功的文档
        assertThat(successDocs).hasSize(2);
        assertThat(successDocs).allMatch(doc -> "SUCCESS".equals(doc.getStatus()));
    }

    /**
     * 测试：获取文档详情
     */
    @Test
    void testGetDocumentDetail() {
        // Given: 插入测试数据
        String fileHash = "test_hash_123";
        createTestHistory(fileHash, "/path/test.pdf", IngestionHistory.IngestionStatus.SUCCESS);

        // When: 获取详情
        DocumentService.DocumentDetail detail = documentService.getDocumentDetail(fileHash);

        // Then: 返回详情
        assertThat(detail).isNotNull();
        assertThat(detail.getFileHash()).isEqualTo(fileHash);
        assertThat(detail.getFilePath()).isEqualTo("/path/test.pdf");
        assertThat(detail.getStatus()).isEqualTo("SUCCESS");
    }

    /**
     * 测试：获取不存在的文档详情
     */
    @Test
    void testGetDocumentDetailNotFound() {
        // When & Then: 查询不存在的文档
        assertThatThrownBy(() -> documentService.getDocumentDetail("nonexistent"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文档不存在");
    }

    /**
     * 测试：删除文档
     */
    @Test
    void testDeleteDocument() {
        // Given: 插入测试数据
        String filePath = "/path/test.pdf";
        createTestHistory("hash123", filePath, IngestionHistory.IngestionStatus.SUCCESS);

        // When: 删除文档
        documentService.deleteDocument(filePath);

        // Then: 文档已删除
        List<IngestionHistory> remaining = ingestionHistoryRepository.findAll();
        assertThat(remaining).isEmpty();
    }

    /**
     * 测试：删除不存在的文档
     */
    @Test
    void testDeleteNonexistentDocument() {
        // When: 删除不存在的文档（不应抛异常）
        documentService.deleteDocument("/path/nonexistent.pdf");

        // Then: 无异常
        assertThat(ingestionHistoryRepository.count()).isEqualTo(0);
    }

    /**
     * 测试：获取Collection统计信息
     */
    @Test
    void testGetCollectionStats() {
        // Given: 插入测试数据
        createTestHistory("hash1", "/path/doc1.pdf", IngestionHistory.IngestionStatus.SUCCESS);
        createTestHistory("hash2", "/path/doc2.pdf", IngestionHistory.IngestionStatus.SUCCESS);
        createTestHistory("hash3", "/path/doc3.pdf", IngestionHistory.IngestionStatus.FAILED);

        // Mock Milvus统计
        GetCollectionStatsResp mockResp = mock(GetCollectionStatsResp.class);
        when(mockResp.getStats()).thenReturn(Map.of("row_count", "100"));
        when(milvusClient.getCollectionStats(any())).thenReturn(mockResp);

        // When: 获取统计
        DocumentService.CollectionStats stats = documentService.getCollectionStats();

        // Then: 统计正确
        assertThat(stats).isNotNull();
        assertThat(stats.getVectorCount()).isEqualTo(100L);
        assertThat(stats.getTotalDocuments()).isEqualTo(3L);
        assertThat(stats.getSuccessDocuments()).isEqualTo(2L);
        assertThat(stats.getFailedDocuments()).isEqualTo(1L);
    }

    /**
     * 测试：Milvus统计失败时的降级
     */
    @Test
    void testGetCollectionStatsWithMilvusFailure() {
        // Given: Milvus调用失败
        when(milvusClient.getCollectionStats(any())).thenThrow(new RuntimeException("Milvus error"));

        // When: 获取统计
        DocumentService.CollectionStats stats = documentService.getCollectionStats();

        // Then: 向量数为0，其他统计正常
        assertThat(stats).isNotNull();
        assertThat(stats.getVectorCount()).isEqualTo(0L);
        assertThat(stats.getTotalDocuments()).isEqualTo(0L);
    }

    /**
     * 测试：批量删除Collection
     */
    @Test
    void testDeleteCollection() {
        // Given: Mock图片删除服务
        when(imageStorageService.deleteByCollection(anyString())).thenReturn(5);

        // When: 删除Collection
        documentService.deleteCollection("test_collection");

        // Then: 调用了相关删除方法
        verify(milvusClient).delete(any());
        verify(imageStorageService).deleteByCollection("test_collection");
    }

    /**
     * 测试：批量删除Collection - Milvus失败不影响其他清理
     */
    @Test
    void testDeleteCollectionWithMilvusFailure() {
        // Given: Milvus删除失败
        when(milvusClient.delete(any())).thenThrow(new RuntimeException("Milvus error"));
        when(imageStorageService.deleteByCollection(anyString())).thenReturn(3);

        // When: 删除Collection
        documentService.deleteCollection("test_collection");

        // Then: 图片仍然被删除
        verify(imageStorageService).deleteByCollection("test_collection");
    }

    /**
     * 创建测试历史记录
     */
    private void createTestHistory(String fileHash, String filePath, IngestionHistory.IngestionStatus status) {
        IngestionHistory history = new IngestionHistory();
        history.setFileHash(fileHash);
        history.setFilePath(filePath);
        history.setFileSize(1024L);
        history.setStatus(status);
        history.setProcessedAt(Instant.now());
        history.setChunkCount(10);
        if (status == IngestionHistory.IngestionStatus.FAILED) {
            history.setErrorMsg("Test error");
        }
        ingestionHistoryRepository.save(history);
    }
}
