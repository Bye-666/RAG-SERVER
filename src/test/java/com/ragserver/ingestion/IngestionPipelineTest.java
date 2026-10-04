package com.ragserver.ingestion;

import com.ragserver.ingestion.loader.PdfLoader;
import com.ragserver.ingestion.splitter.RecursiveSplitter;
import com.ragserver.ingestion.transformer.ChunkRefiner;
import com.ragserver.ingestion.transformer.MetadataEnricher;
import com.ragserver.retrieval.model.Document;
import com.ragserver.retrieval.milvus.MilvusHybridStore;
import com.ragserver.service.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IngestionPipeline集成测试
 *
 * @author RAG-SERVER开发团队
 */
@DisplayName("IngestionPipeline - 摄取管道测试")
class IngestionPipelineTest {

    private IngestionPipeline pipeline;
    private PdfLoader mockPdfLoader;
    private RecursiveSplitter mockSplitter;
    private ChunkRefiner mockRefiner;
    private MetadataEnricher mockEnricher;
    private EmbeddingService mockEmbeddingService;
    private MilvusHybridStore mockVectorStore;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockPdfLoader = mock(PdfLoader.class);
        mockSplitter = mock(RecursiveSplitter.class);
        mockRefiner = mock(ChunkRefiner.class);
        mockEnricher = mock(MetadataEnricher.class);
        mockEmbeddingService = mock(EmbeddingService.class);
        mockVectorStore = mock(MilvusHybridStore.class);

        pipeline = new IngestionPipeline(
            mockPdfLoader,
            mockSplitter,
            mockRefiner,
            mockEnricher,
            mockEmbeddingService,
            mockVectorStore
        );
    }

    @Test
    @DisplayName("测试1: 完整摄取流程成功")
    void testCompleteIngestionSuccess() throws Exception {
        // 创建测试PDF文件
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "PDF content");

        // Mock每个步骤
        Document rawDoc = createMockDocument("raw", "原始文档内容");
        when(mockPdfLoader.load(any(Path.class))).thenReturn(List.of(rawDoc));

        List<Document> chunks = Arrays.asList(
            createMockDocument("chunk1", "块1"),
            createMockDocument("chunk2", "块2"),
            createMockDocument("chunk3", "块3")
        );
        when(mockSplitter.split(any())).thenReturn(chunks);

        when(mockRefiner.refineBatch(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mockEnricher.enrichBatch(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<List<Double>> embeddings = Arrays.asList(
            generateMockEmbedding(2048),
            generateMockEmbedding(2048),
            generateMockEmbedding(2048)
        );
        when(mockEmbeddingService.batchEmbed(anyList())).thenReturn(embeddings);

        doNothing().when(mockVectorStore).batchUpsert(anyList());

        // 执行摄取
        IngestionPipeline.IngestionResult result = pipeline.ingest(testPdf);

        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals(3, result.getChunksCreated());
        assertEquals(3, result.getChunksProcessed());
        assertNotNull(result.getSourceDocument());
        assertTrue(result.getDurationMs() >= 0);

        // 验证每个步骤都被调用
        verify(mockPdfLoader, times(1)).load(testPdf);
        verify(mockSplitter, times(1)).split(rawDoc);
        verify(mockRefiner, times(1)).refineBatch(anyList());
        verify(mockEnricher, times(1)).enrichBatch(anyList());
        verify(mockEmbeddingService, times(1)).batchEmbed(anyList());
        verify(mockVectorStore, times(1)).batchUpsert(anyList());
    }

    @Test
    @DisplayName("测试2: PDF加载失败")
    void testPdfLoadFailure() throws Exception {
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "PDF content");

        when(mockPdfLoader.load(any(Path.class)))
            .thenThrow(new RuntimeException("文件损坏"));

        IngestionPipeline.IngestionException exception = assertThrows(
            IngestionPipeline.IngestionException.class,
            () -> pipeline.ingest(testPdf)
        );

        assertTrue(exception.getMessage().contains("步骤1失败"));
        assertTrue(exception.getMessage().contains("PDF加载失败"));
        assertNotNull(exception.getSuggestion());
        assertTrue(exception.getSuggestion().contains("文件路径"));
    }

    @Test
    @DisplayName("测试3: 分块失败")
    void testSplitFailure() throws Exception {
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "PDF content");

        Document rawDoc = createMockDocument("raw", "原始文档");
        when(mockPdfLoader.load(any(Path.class))).thenReturn(List.of(rawDoc));

        when(mockSplitter.split(any()))
            .thenThrow(new RuntimeException("分块算法错误"));

        IngestionPipeline.IngestionException exception = assertThrows(
            IngestionPipeline.IngestionException.class,
            () -> pipeline.ingest(testPdf)
        );

        assertTrue(exception.getMessage().contains("步骤2失败"));
        assertTrue(exception.getMessage().contains("文档分块失败"));
    }

    @Test
    @DisplayName("测试4: 分块后结果为空")
    void testEmptyChunksAfterSplit() throws Exception {
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "PDF content");

        Document rawDoc = createMockDocument("raw", "原始文档");
        when(mockPdfLoader.load(any(Path.class))).thenReturn(List.of(rawDoc));

        when(mockSplitter.split(any())).thenReturn(Collections.emptyList());

        IngestionPipeline.IngestionException exception = assertThrows(
            IngestionPipeline.IngestionException.class,
            () -> pipeline.ingest(testPdf)
        );

        assertTrue(exception.getMessage().contains("步骤2失败"));
        assertTrue(exception.getMessage().contains("分块后结果为空"));
    }

    @Test
    @DisplayName("测试5: Embedding生成失败")
    void testEmbeddingFailure() throws Exception {
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "PDF content");

        setupSuccessfulStepsUntilEmbedding();

        when(mockEmbeddingService.batchEmbed(anyList()))
            .thenThrow(new RuntimeException("API调用失败"));

        IngestionPipeline.IngestionException exception = assertThrows(
            IngestionPipeline.IngestionException.class,
            () -> pipeline.ingest(testPdf)
        );

        assertTrue(exception.getMessage().contains("步骤5失败"));
        assertTrue(exception.getMessage().contains("Embedding生成失败"));
        assertTrue(exception.getSuggestion().contains("DashScope API"));
    }

    @Test
    @DisplayName("测试6: 向量存储失败")
    void testVectorStoreFailure() throws Exception {
        Path testPdf = tempDir.resolve("test.pdf");
        Files.writeString(testPdf, "PDF content");

        setupSuccessfulStepsIncludingEmbedding();

        doThrow(new RuntimeException("Milvus连接失败"))
            .when(mockVectorStore).batchUpsert(anyList());

        IngestionPipeline.IngestionException exception = assertThrows(
            IngestionPipeline.IngestionException.class,
            () -> pipeline.ingest(testPdf)
        );

        assertTrue(exception.getMessage().contains("步骤6失败"));
        assertTrue(exception.getMessage().contains("向量存储失败"));
        assertTrue(exception.getSuggestion().contains("Milvus"));
    }

    @Test
    @DisplayName("测试7: 批量摄取 - 全部成功")
    void testBatchIngestionAllSuccess() throws Exception {
        setupSuccessfulStepsIncludingEmbedding();
        doNothing().when(mockVectorStore).batchUpsert(anyList());

        List<Path> files = Arrays.asList(
            createTestPdf("doc1.pdf"),
            createTestPdf("doc2.pdf"),
            createTestPdf("doc3.pdf")
        );

        List<IngestionPipeline.IngestionResult> results = pipeline.ingestBatch(files);

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(IngestionPipeline.IngestionResult::isSuccess));
    }

    @Test
    @DisplayName("测试8: 批量摄取 - 部分失败（continueOnError=true）")
    void testBatchIngestionPartialFailure() throws Exception {
        pipeline.setContinueOnError(true);

        setupSuccessfulStepsIncludingEmbedding();
        doNothing().when(mockVectorStore).batchUpsert(anyList());

        // 第二个文件会失败
        when(mockPdfLoader.load(any(Path.class)))
            .thenReturn(List.of(createMockDocument("raw", "原始文档")))
            .thenThrow(new RuntimeException("文件2加载失败"))
            .thenReturn(List.of(createMockDocument("raw", "原始文档")));

        List<Path> files = Arrays.asList(
            createTestPdf("doc1.pdf"),
            createTestPdf("doc2.pdf"),
            createTestPdf("doc3.pdf")
        );

        List<IngestionPipeline.IngestionResult> results = pipeline.ingestBatch(files);

        assertEquals(3, results.size());
        assertTrue(results.get(0).isSuccess());
        assertFalse(results.get(1).isSuccess());
        assertTrue(results.get(2).isSuccess());
    }

    @Test
    @DisplayName("测试9: 批量摄取 - 失败后停止（continueOnError=false）")
    void testBatchIngestionStopOnFailure() throws Exception {
        pipeline.setContinueOnError(false);

        setupSuccessfulStepsIncludingEmbedding();
        doNothing().when(mockVectorStore).batchUpsert(anyList());

        // 第二个文件会失败
        when(mockPdfLoader.load(any(Path.class)))
            .thenReturn(List.of(createMockDocument("raw", "原始文档")))
            .thenThrow(new RuntimeException("文件2加载失败"));

        List<Path> files = Arrays.asList(
            createTestPdf("doc1.pdf"),
            createTestPdf("doc2.pdf"),
            createTestPdf("doc3.pdf")
        );

        List<IngestionPipeline.IngestionResult> results = pipeline.ingestBatch(files);

        // 只处理了2个文件就停止了
        assertEquals(2, results.size());
        assertTrue(results.get(0).isSuccess());
        assertFalse(results.get(1).isSuccess());
    }

    @Test
    @DisplayName("测试10: 摄取结果toString")
    void testIngestionResultToString() {
        IngestionPipeline.IngestionResult successResult = new IngestionPipeline.IngestionResult("/test.pdf");
        successResult.setSuccess(true);
        successResult.setChunksProcessed(10);
        successResult.setDurationMs(1000);

        String str = successResult.toString();
        assertTrue(str.contains("/test.pdf"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("1000"));

        IngestionPipeline.IngestionResult failureResult = new IngestionPipeline.IngestionResult("/test.pdf");
        failureResult.setSuccess(false);
        failureResult.setErrorMessage("加载失败");

        String failStr = failureResult.toString();
        assertTrue(failStr.contains("failed"));
        assertTrue(failStr.contains("加载失败"));
    }

    // ==================== 辅助方法 ====================

    private Document createMockDocument(String id, String text) {
        return Document.builder()
            .id(id)
            .text(text)
            .metadata(new HashMap<>())
            .build();
    }

    private List<Double> generateMockEmbedding(int dimension) {
        List<Double> embedding = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < dimension; i++) {
            embedding.add(random.nextDouble());
        }
        return embedding;
    }

    private Path createTestPdf(String filename) throws Exception {
        Path pdfPath = tempDir.resolve(filename);
        Files.writeString(pdfPath, "PDF content");
        return pdfPath;
    }

    private void setupSuccessfulStepsUntilEmbedding() throws Exception {
        Document rawDoc = createMockDocument("raw", "原始文档");
        when(mockPdfLoader.load(any(Path.class))).thenReturn(List.of(rawDoc));

        List<Document> chunks = Arrays.asList(
            createMockDocument("chunk1", "块1"),
            createMockDocument("chunk2", "块2")
        );
        when(mockSplitter.split(any())).thenReturn(chunks);
        when(mockRefiner.refineBatch(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mockEnricher.enrichBatch(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void setupSuccessfulStepsIncludingEmbedding() throws Exception {
        setupSuccessfulStepsUntilEmbedding();

        List<List<Double>> embeddings = Arrays.asList(
            generateMockEmbedding(2048),
            generateMockEmbedding(2048)
        );
        when(mockEmbeddingService.batchEmbed(anyList())).thenReturn(embeddings);
    }
}
