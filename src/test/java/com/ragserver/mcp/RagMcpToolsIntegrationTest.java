package com.ragserver.mcp;

import com.ragserver.entity.IngestionHistory;
import com.ragserver.ingestion.IngestionPipeline;
import com.ragserver.mcp.exception.McpResourceNotFoundException;
import com.ragserver.mcp.exception.McpServiceException;
import com.ragserver.mcp.exception.McpValidationException;
import com.ragserver.service.DocumentService;
import com.ragserver.service.IngestionService;
import com.ragserver.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RAG MCP Tools 集成测试
 *
 * <p>测试 4 个 MCP Tools 的功能正确性：</p>
 * <ul>
 *   <li>ragQuery - 查询知识库</li>
 *   <li>ragIngest - 上传文档</li>
 *   <li>ragListDocs - 列出文档</li>
 *   <li>ragDeleteDoc - 删除文档</li>
 * </ul>
 *
 * <p>使用 @SpringBootTest 进行集成测试，Mock Service 层。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@SpringBootTest
@DisplayName("RAG MCP Tools 集成测试")
class RagMcpToolsIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private RagService ragService;

    @MockBean
    private IngestionService ingestionService;

    @MockBean
    private DocumentService documentService;

    private Function<RagMcpTools.RagQueryRequest, String> ragQuery;
    private Function<RagMcpTools.RagIngestRequest, RagMcpTools.RagIngestResponse> ragIngest;
    private Function<RagMcpTools.RagListDocsRequest, RagMcpTools.RagListDocsResponse> ragListDocs;
    private Function<RagMcpTools.RagDeleteDocRequest, RagMcpTools.RagDeleteDocResponse> ragDeleteDoc;

    @BeforeEach
    void setUp() {
        // 从 Spring 容器获取注册的 Function Bean
        ragQuery = (Function<RagMcpTools.RagQueryRequest, String>) applicationContext.getBean("ragQuery");
        ragIngest = (Function<RagMcpTools.RagIngestRequest, RagMcpTools.RagIngestResponse>) applicationContext.getBean("ragIngest");
        ragListDocs = (Function<RagMcpTools.RagListDocsRequest, RagMcpTools.RagListDocsResponse>) applicationContext.getBean("ragListDocs");
        ragDeleteDoc = (Function<RagMcpTools.RagDeleteDocRequest, RagMcpTools.RagDeleteDocResponse>) applicationContext.getBean("ragDeleteDoc");
    }

    // ========== ragQuery Tool 测试 ==========

    @Test
    @DisplayName("ragQuery - 正常查询流程")
    void testRagQuery_Success() {
        // Given
        String question = "什么是RAG？";
        RagService.QueryResult mockResult = createMockQueryResult();
        when(ragService.query(eq(question), eq(10), eq(false))).thenReturn(mockResult);

        // When
        var request = new RagMcpTools.RagQueryRequest(question, null, null);
        String response = ragQuery.apply(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response).contains("RAG是检索增强生成");
        assertThat(response).contains("Performance");
        assertThat(response).contains("Total: 1500 ms");
        assertThat(response).contains("Retrieval: 300 ms");
        assertThat(response).contains("LLM: 1200 ms");

        verify(ragService).query(eq(question), eq(10), eq(false));
    }

    @Test
    @DisplayName("ragQuery - 自定义参数（topK 和 enableRerank）")
    void testRagQuery_CustomParameters() {
        // Given
        String question = "测试问题";
        int topK = 20;
        boolean enableRerank = true;
        RagService.QueryResult mockResult = createMockQueryResult();
        when(ragService.query(eq(question), eq(topK), eq(enableRerank))).thenReturn(mockResult);

        // When
        var request = new RagMcpTools.RagQueryRequest(question, topK, enableRerank);
        String response = ragQuery.apply(request);

        // Then
        assertThat(response).isNotNull();
        verify(ragService).query(eq(question), eq(topK), eq(enableRerank));
    }

    @Test
    @DisplayName("ragQuery - 参数验证：question 为空")
    void testRagQuery_EmptyQuestion() {
        // When
        var request = new RagMcpTools.RagQueryRequest("", null, null);
        String response = ragQuery.apply(request);

        // Then
        assertThat(response).contains("Error [VALIDATION_ERROR]");
        assertThat(response).contains("Required parameter 'question' is missing or empty");
        verify(ragService, never()).query(anyString(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("ragQuery - 参数验证：question 为 null")
    void testRagQuery_NullQuestion() {
        // When
        var request = new RagMcpTools.RagQueryRequest(null, null, null);
        String response = ragQuery.apply(request);

        // Then
        assertThat(response).contains("Error [VALIDATION_ERROR]");
        assertThat(response).contains("Required parameter 'question' is missing or empty");
        verify(ragService, never()).query(anyString(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("ragQuery - 参数验证：topK 小于 1")
    void testRagQuery_TopKTooSmall() {
        // When
        var request = new RagMcpTools.RagQueryRequest("问题", 0, null);
        String response = ragQuery.apply(request);

        // Then
        assertThat(response).contains("Error [VALIDATION_ERROR]");
        assertThat(response).contains("out of range");
        verify(ragService, never()).query(anyString(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("ragQuery - 参数验证：topK 大于 50")
    void testRagQuery_TopKTooLarge() {
        // When
        var request = new RagMcpTools.RagQueryRequest("问题", 100, null);
        String response = ragQuery.apply(request);

        // Then
        assertThat(response).contains("Error [VALIDATION_ERROR]");
        assertThat(response).contains("out of range");
        verify(ragService, never()).query(anyString(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("ragQuery - 异常场景：Service 抛出异常")
    void testRagQuery_ServiceException() {
        // Given
        String question = "测试问题";
        when(ragService.query(anyString(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("Milvus connection failed"));

        // When
        var request = new RagMcpTools.RagQueryRequest(question, null, null);
        String response = ragQuery.apply(request);

        // Then
        assertThat(response).contains("Error [INTERNAL_ERROR]");
        assertThat(response).contains("An unexpected error occurred");
    }

    // ========== ragIngest Tool 测试 ==========

    @Test
    @DisplayName("ragIngest - 正常文档上传流程")
    void testRagIngest_Success() {
        // Given
        String filePath = "/test/doc.pdf";
        IngestionService.BatchIngestionResult mockResult = createMockBatchIngestionResult(true);
        when(ingestionService.ingestDocuments(anyList(), anyString())).thenReturn(mockResult);

        // When
        var request = new RagMcpTools.RagIngestRequest(filePath, null, null);
        RagMcpTools.RagIngestResponse response = ragIngest.apply(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Document ingested successfully");
        assertThat(response.filePath()).isEqualTo(filePath);
        assertThat(response.fileName()).isEqualTo("doc.pdf");
        assertThat(response.chunksProcessed()).isEqualTo(42);
        assertThat(response.durationMs()).isGreaterThan(0);

        verify(ingestionService).ingestDocuments(anyList(), eq("rag_collection"));
    }

    @Test
    @DisplayName("ragIngest - 自定义 Collection")
    void testRagIngest_CustomCollection() {
        // Given
        String filePath = "/test/doc.pdf";
        String collectionName = "custom_collection";
        IngestionService.BatchIngestionResult mockResult = createMockBatchIngestionResult(true);
        when(ingestionService.ingestDocuments(anyList(), eq(collectionName))).thenReturn(mockResult);

        // When
        var request = new RagMcpTools.RagIngestRequest(filePath, collectionName, null);
        RagMcpTools.RagIngestResponse response = ragIngest.apply(request);

        // Then
        assertThat(response.success()).isTrue();
        verify(ingestionService).ingestDocuments(anyList(), eq(collectionName));
    }

    @Test
    @DisplayName("ragIngest - 参数验证：filePath 为空")
    void testRagIngest_EmptyFilePath() {
        // When
        var request = new RagMcpTools.RagIngestRequest("", null, null);
        RagMcpTools.RagIngestResponse response = ragIngest.apply(request);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("[VALIDATION_ERROR]");
        assertThat(response.message()).contains("Required parameter 'filePath' is missing or empty");
        verify(ingestionService, never()).ingestDocuments(anyList(), anyString());
    }

    @Test
    @DisplayName("ragIngest - 参数验证：filePath 为 null")
    void testRagIngest_NullFilePath() {
        // When
        var request = new RagMcpTools.RagIngestRequest(null, null, null);
        RagMcpTools.RagIngestResponse response = ragIngest.apply(request);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("[VALIDATION_ERROR]");
        verify(ingestionService, never()).ingestDocuments(anyList(), anyString());
    }

    @Test
    @DisplayName("ragIngest - 异常场景：摄取失败")
    void testRagIngest_IngestionFailed() {
        // Given
        String filePath = "/test/doc.pdf";
        IngestionService.BatchIngestionResult mockResult = createMockBatchIngestionResult(false);
        when(ingestionService.ingestDocuments(anyList(), anyString())).thenReturn(mockResult);

        // When
        var request = new RagMcpTools.RagIngestRequest(filePath, null, null);
        RagMcpTools.RagIngestResponse response = ragIngest.apply(request);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("[SERVICE_ERROR]");
        assertThat(response.message()).contains("Ingestion failed");
        assertThat(response.chunksProcessed()).isEqualTo(0);
    }

    @Test
    @DisplayName("ragIngest - 异常场景：Service 抛出异常")
    void testRagIngest_ServiceException() {
        // Given
        String filePath = "/test/doc.pdf";
        when(ingestionService.ingestDocuments(anyList(), anyString()))
                .thenThrow(new RuntimeException("File not found"));

        // When
        var request = new RagMcpTools.RagIngestRequest(filePath, null, null);
        RagMcpTools.RagIngestResponse response = ragIngest.apply(request);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("Error");
        assertThat(response.message()).contains("File not found");
    }

    // ========== ragListDocs Tool 测试 ==========

    @Test
    @DisplayName("ragListDocs - 无参数查询（返回所有文档）")
    void testRagListDocs_AllDocuments() {
        // Given
        List<DocumentService.DocumentInfo> mockDocs = createMockDocumentInfoList();
        when(documentService.listDocuments()).thenReturn(mockDocs);

        // When
        var request = new RagMcpTools.RagListDocsRequest(null);
        RagMcpTools.RagListDocsResponse response = ragListDocs.apply(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Success");
        assertThat(response.documents()).hasSize(2);
        assertThat(response.documents().get(0).getSource()).isEqualTo("doc1.pdf");

        verify(documentService).listDocuments();
        verify(documentService, never()).listDocumentsByStatus(any());
    }

    @Test
    @DisplayName("ragListDocs - 按状态过滤（SUCCESS）")
    void testRagListDocs_FilterBySuccess() {
        // Given
        List<DocumentService.DocumentInfo> mockDocs = List.of(createMockDocumentInfoList().get(0));
        when(documentService.listDocumentsByStatus(IngestionHistory.IngestionStatus.SUCCESS))
                .thenReturn(mockDocs);

        // When
        var request = new RagMcpTools.RagListDocsRequest("SUCCESS");
        RagMcpTools.RagListDocsResponse response = ragListDocs.apply(request);

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.documents()).hasSize(1);
        verify(documentService).listDocumentsByStatus(IngestionHistory.IngestionStatus.SUCCESS);
    }

    @Test
    @DisplayName("ragListDocs - 按状态过滤（FAILED）")
    void testRagListDocs_FilterByFailed() {
        // Given
        List<DocumentService.DocumentInfo> mockDocs = List.of(createMockDocumentInfoList().get(1));
        when(documentService.listDocumentsByStatus(IngestionHistory.IngestionStatus.FAILED))
                .thenReturn(mockDocs);

        // When
        var request = new RagMcpTools.RagListDocsRequest("FAILED");
        RagMcpTools.RagListDocsResponse response = ragListDocs.apply(request);

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.documents()).hasSize(1);
        verify(documentService).listDocumentsByStatus(IngestionHistory.IngestionStatus.FAILED);
    }

    @Test
    @DisplayName("ragListDocs - 状态过滤器大小写容错")
    void testRagListDocs_CaseInsensitiveStatus() {
        // Given
        List<DocumentService.DocumentInfo> mockDocs = createMockDocumentInfoList();
        when(documentService.listDocumentsByStatus(IngestionHistory.IngestionStatus.SUCCESS))
                .thenReturn(mockDocs);

        // When
        var request = new RagMcpTools.RagListDocsRequest("success");
        RagMcpTools.RagListDocsResponse response = ragListDocs.apply(request);

        // Then
        assertThat(response.success()).isTrue();
        verify(documentService).listDocumentsByStatus(IngestionHistory.IngestionStatus.SUCCESS);
    }

    @Test
    @DisplayName("ragListDocs - 无效状态值")
    void testRagListDocs_InvalidStatus() {
        // When
        var request = new RagMcpTools.RagListDocsRequest("INVALID_STATUS");
        RagMcpTools.RagListDocsResponse response = ragListDocs.apply(request);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("[VALIDATION_ERROR]");
        assertThat(response.message()).contains("Invalid status value");
        assertThat(response.documents()).isEmpty();
        verify(documentService, never()).listDocuments();
        verify(documentService, never()).listDocumentsByStatus(any());
    }

    @Test
    @DisplayName("ragListDocs - 异常场景：Service 抛出异常")
    void testRagListDocs_ServiceException() {
        // Given
        when(documentService.listDocuments()).thenThrow(new RuntimeException("Database error"));

        // When
        var request = new RagMcpTools.RagListDocsRequest(null);
        RagMcpTools.RagListDocsResponse response = ragListDocs.apply(request);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("[INTERNAL_ERROR]");
        assertThat(response.documents()).isEmpty();
    }

    // ========== ragDeleteDoc Tool 测试 ==========

    @Test
    @DisplayName("ragDeleteDoc - 通过 ID 删除")
    void testRagDeleteDoc_ById() {
        // Given
        String docId = "abc123def456";
        doNothing().when(documentService).deleteDocument(docId);

        // When
        var request = new RagMcpTools.RagDeleteDocRequest(docId);
        RagMcpTools.RagDeleteDocResponse response = ragDeleteDoc.apply(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Document deleted successfully");
        assertThat(response.idOrPath()).isEqualTo(docId);

        verify(documentService).deleteDocument(docId);
    }

    @Test
    @DisplayName("ragDeleteDoc - 通过路径删除")
    void testRagDeleteDoc_ByPath() {
        // Given
        String filePath = "/test/doc.pdf";
        doNothing().when(documentService).deleteDocument(filePath);

        // When
        var request = new RagMcpTools.RagDeleteDocRequest(filePath);
        RagMcpTools.RagDeleteDocResponse response = ragDeleteDoc.apply(request);

        // Then
        assertThat(response.success()).isTrue();
        assertThat(response.idOrPath()).isEqualTo(filePath);
        verify(documentService).deleteDocument(filePath);
    }

    @Test
    @DisplayName("ragDeleteDoc - 参数验证：idOrPath 为空")
    void testRagDeleteDoc_EmptyIdOrPath() {
        // When
        var request = new RagMcpTools.RagDeleteDocRequest("");
        RagMcpTools.RagDeleteDocResponse response = ragDeleteDoc.apply(request);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("[VALIDATION_ERROR]");
        assertThat(response.message()).contains("Required parameter 'idOrPath' is missing or empty");
        verify(documentService, never()).deleteDocument(anyString());
    }

    @Test
    @DisplayName("ragDeleteDoc - 参数验证：idOrPath 为 null")
    void testRagDeleteDoc_NullIdOrPath() {
        // When
        var request = new RagMcpTools.RagDeleteDocRequest(null);
        RagMcpTools.RagDeleteDocResponse response = ragDeleteDoc.apply(request);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("[VALIDATION_ERROR]");
        verify(documentService, never()).deleteDocument(anyString());
    }

    @Test
    @DisplayName("ragDeleteDoc - 资源不存在场景")
    void testRagDeleteDoc_ResourceNotFound() {
        // Given
        String docId = "nonexistent";
        doThrow(McpResourceNotFoundException.documentNotFound(docId))
                .when(documentService).deleteDocument(docId);

        // When
        var request = new RagMcpTools.RagDeleteDocRequest(docId);
        RagMcpTools.RagDeleteDocResponse response = ragDeleteDoc.apply(request);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("[RESOURCE_NOT_FOUND]");
        assertThat(response.message()).contains("Document not found");
    }

    @Test
    @DisplayName("ragDeleteDoc - 异常场景：Service 抛出通用异常")
    void testRagDeleteDoc_ServiceException() {
        // Given
        String docId = "test123";
        doThrow(new RuntimeException("Database connection failed"))
                .when(documentService).deleteDocument(docId);

        // When
        var request = new RagMcpTools.RagDeleteDocRequest(docId);
        RagMcpTools.RagDeleteDocResponse response = ragDeleteDoc.apply(request);

        // Then
        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("Delete failed");
        assertThat(response.message()).contains("Database connection failed");
    }

    // ========== 辅助方法 ==========

    /**
     * 创建 Mock QueryResult
     */
    private RagService.QueryResult createMockQueryResult() {
        RagService.QueryResult result = new RagService.QueryResult();
        result.setAnswer("RAG是检索增强生成");
        result.setTotalDurationMs(1500L);
        result.setRetrievalDurationMs(300L);
        result.setLlmGenerationDurationMs(1200L);
        return result;
    }

    /**
     * 创建 Mock BatchIngestionResult
     */
    private IngestionService.BatchIngestionResult createMockBatchIngestionResult(boolean success) {
        IngestionService.BatchIngestionResult result = new IngestionService.BatchIngestionResult();
        result.setTotalFiles(1);

        if (success) {
            IngestionPipeline.IngestionResult ingestionResult = new IngestionPipeline.IngestionResult("/test/doc.pdf");
            ingestionResult.setChunksProcessed(42);
            ingestionResult.setSuccess(true);
            ingestionResult.setDurationMs(500L);
            result.addSuccess(ingestionResult);
        } else {
            result.addFailure("/test/doc.pdf", "File processing error");
        }

        return result;
    }

    /**
     * 创建 Mock DocumentInfo 列表
     */
    private List<DocumentService.DocumentInfo> createMockDocumentInfoList() {
        DocumentService.DocumentInfo doc1 = new DocumentService.DocumentInfo();
        doc1.setId("hash123");
        doc1.setFileHash("hash123");
        doc1.setSource("doc1.pdf");
        doc1.setFilePath("/path/to/doc1.pdf");
        doc1.setStatus("SUCCESS");
        doc1.setChunkCount(10);
        doc1.setProcessedAt(Instant.now());

        DocumentService.DocumentInfo doc2 = new DocumentService.DocumentInfo();
        doc2.setId("hash456");
        doc2.setFileHash("hash456");
        doc2.setSource("doc2.pdf");
        doc2.setFilePath("/path/to/doc2.pdf");
        doc2.setStatus("FAILED");
        doc2.setChunkCount(0);
        doc2.setProcessedAt(Instant.now());

        return List.of(doc1, doc2);
    }
}
