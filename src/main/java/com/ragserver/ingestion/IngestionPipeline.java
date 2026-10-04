package com.ragserver.ingestion;

import com.ragserver.ingestion.loader.PdfLoader;
import com.ragserver.ingestion.splitter.RecursiveSplitter;
import com.ragserver.ingestion.transformer.ChunkRefiner;
import com.ragserver.ingestion.transformer.MetadataEnricher;
import com.ragserver.retrieval.model.Document;
import com.ragserver.retrieval.milvus.MilvusHybridStore;
import com.ragserver.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档摄取管道
 *
 * <p>串联所有摄取步骤，将PDF文档处理并存入向量数据库。</p>
 *
 * <h3>处理流程</h3>
 * <pre>
 * PDF文件
 *   ↓
 * 1. PdfLoader - 加载PDF，提取文本和元数据
 *   ↓
 * 2. RecursiveSplitter - 递归分块（512字符，128重叠）
 *   ↓
 * 3. ChunkRefiner - 清理噪声（空白、页眉页脚、特殊字符）
 *   ↓
 * 4. MetadataEnricher - 增强元数据（标题、关键词、标签）
 *   ↓
 * 5. EmbeddingService - 批量生成向量（2048维）
 *   ↓
 * 6. MilvusHybridStore - 存入向量数据库
 *   ↓
 * 摄取完成
 * </pre>
 *
 * <h3>异常处理</h3>
 * <ul>
 *   <li>每步失败抛出清晰的异常</li>
 *   <li>包含失败步骤、原因、建议</li>
 *   <li>支持部分失败继续（可配置）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private IngestionPipeline pipeline;
 *
 * // 摄取单个PDF
 * IngestionResult result = pipeline.ingest(Path.of("/docs/report.pdf"));
 * System.out.println("摄取完成：" + result.getChunksProcessed() + "个块");
 *
 * // 批量摄取
 * List<Path> files = List.of(
 *     Path.of("/docs/doc1.pdf"),
 *     Path.of("/docs/doc2.pdf")
 * );
 * List<IngestionResult> results = pipeline.ingestBatch(files);
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class IngestionPipeline {

    private final PdfLoader pdfLoader;
    private final RecursiveSplitter splitter;
    private final ChunkRefiner refiner;
    private final MetadataEnricher enricher;
    private final EmbeddingService embeddingService;
    private final MilvusHybridStore vectorStore;

    /**
     * 是否在块处理失败时继续处理剩余块
     */
    private boolean continueOnError = false;

    /**
     * 构造函数
     */
    public IngestionPipeline(
        PdfLoader pdfLoader,
        RecursiveSplitter splitter,
        ChunkRefiner refiner,
        MetadataEnricher enricher,
        EmbeddingService embeddingService,
        MilvusHybridStore vectorStore
    ) {
        this.pdfLoader = pdfLoader;
        this.splitter = splitter;
        this.refiner = refiner;
        this.enricher = enricher;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;

        log.info("IngestionPipeline初始化完成");
    }

    /**
     * 摄取单个PDF文件
     *
     * <p>执行完整的摄取流程，从PDF加载到向量存储。</p>
     *
     * @param pdfPath PDF文件路径
     * @return 摄取结果
     * @throws IngestionException 摄取过程中的任何步骤失败
     */
    public IngestionResult ingest(Path pdfPath) throws IngestionException {
        log.info("开始摄取文档: {}", pdfPath);
        long startTime = System.currentTimeMillis();

        IngestionResult result = new IngestionResult(pdfPath.toString());

        try {
            // 步骤1: 加载PDF
            log.debug("步骤1: 加载PDF文件");
            List<Document> rawDocuments = loadPdf(pdfPath);

            // 合并为单个文档（如果PDF加载返回多个页面）
            Document rawDocument = mergeDocuments(rawDocuments);
            result.setSourceDocument(rawDocument);

            // 步骤2: 分块
            log.debug("步骤2: 递归分块");
            List<Document> chunks = new ArrayList<>(splitDocument(rawDocument));
            result.setChunksCreated(chunks.size());

            // 步骤3: 清理
            log.debug("步骤3: 清理噪声");
            chunks = refineChunks(chunks);

            // 步骤4: 增强元数据
            log.debug("步骤4: 增强元数据");
            chunks = enrichMetadata(chunks);

            // 步骤5: 生成Embedding
            log.debug("步骤5: 批量生成Embedding");
            chunks = generateEmbeddings(chunks);
            result.setChunksProcessed(chunks.size());

            // 步骤6: 存入向量数据库
            log.debug("步骤6: 存入Milvus");
            storeToVectorDb(chunks);

            long duration = System.currentTimeMillis() - startTime;
            result.setSuccess(true);
            result.setDurationMs(duration);

            log.info("摄取完成: {} -> {}个块，耗时{}ms",
                pdfPath.getFileName(), chunks.size(), duration);

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            result.setSuccess(false);
            result.setDurationMs(duration);
            result.setErrorMessage(e.getMessage());

            log.error("摄取失败: {} - {}", pdfPath, e.getMessage(), e);
            throw new IngestionException("文档摄取失败: " + pdfPath, e, result);
        }
    }

    /**
     * 批量摄取PDF文件
     *
     * <p>逐个处理文件，失败的文件不影响后续文件（除非设置了快速失败）。</p>
     *
     * @param pdfPaths PDF文件路径列表
     * @return 每个文件的摄取结果
     */
    public List<IngestionResult> ingestBatch(List<Path> pdfPaths) {
        log.info("开始批量摄取: {}个文件", pdfPaths.size());
        long startTime = System.currentTimeMillis();

        List<IngestionResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (Path pdfPath : pdfPaths) {
            try {
                IngestionResult result = ingest(pdfPath);
                results.add(result);
                successCount++;
            } catch (IngestionException e) {
                results.add(e.getResult());
                failureCount++;

                if (!continueOnError) {
                    log.warn("摄取失败且未启用continueOnError，停止批量处理");
                    break;
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("批量摄取完成: 成功{}/失败{}/总计{}，耗时{}ms",
            successCount, failureCount, pdfPaths.size(), duration);

        return results;
    }

    // ==================== 内部步骤方法 ====================

    /**
     * 步骤1: 加载PDF
     */
    private List<Document> loadPdf(Path pdfPath) throws IngestionException {
        try {
            return pdfLoader.load(pdfPath);
        } catch (Exception e) {
            throw new IngestionException(
                "步骤1失败: PDF加载失败 - " + e.getMessage(),
                e,
                "检查文件路径是否正确，文件是否损坏"
            );
        }
    }

    /**
     * 合并多个文档为单个文档
     */
    private Document mergeDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            throw new IllegalStateException("PDF加载结果为空");
        }

        if (documents.size() == 1) {
            return documents.get(0);
        }

        // 合并多页PDF文档
        StringBuilder mergedText = new StringBuilder();
        Map<String, Object> mergedMetadata = new HashMap<>(documents.get(0).getMetadata());

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            if (i > 0) {
                mergedText.append("\n\n");
            }
            mergedText.append(doc.getText());
        }

        mergedMetadata.put("total_pages", documents.size());

        return Document.builder()
            .id(documents.get(0).getId())
            .text(mergedText.toString())
            .metadata(mergedMetadata)
            .build();
    }

    /**
     * 步骤2: 分块
     */
    private List<Document> splitDocument(Document document) throws IngestionException {
        try {
            List<Document> chunks = splitter.split(document);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("分块后结果为空");
            }
            return chunks;
        } catch (Exception e) {
            throw new IngestionException(
                "步骤2失败: 文档分块失败 - " + e.getMessage(),
                e,
                "检查文档内容是否为空或格式异常"
            );
        }
    }

    /**
     * 步骤3: 清理
     */
    private List<Document> refineChunks(List<Document> chunks) throws IngestionException {
        try {
            return refiner.refineBatch(chunks);
        } catch (Exception e) {
            throw new IngestionException(
                "步骤3失败: 噪声清理失败 - " + e.getMessage(),
                e,
                "内部错误，请检查ChunkRefiner实现"
            );
        }
    }

    /**
     * 步骤4: 增强元数据
     */
    private List<Document> enrichMetadata(List<Document> chunks) throws IngestionException {
        try {
            return enricher.enrichBatch(chunks);
        } catch (Exception e) {
            throw new IngestionException(
                "步骤4失败: 元数据增强失败 - " + e.getMessage(),
                e,
                "检查LLM服务是否可用（如果启用了LLM模式）"
            );
        }
    }

    /**
     * 步骤5: 生成Embedding
     */
    private List<Document> generateEmbeddings(List<Document> chunks) throws IngestionException {
        try {
            // 提取所有文本
            List<String> texts = chunks.stream()
                .map(Document::getText)
                .toList();

            // 批量生成Embedding
            List<List<Double>> embeddings = embeddingService.batchEmbed(texts);

            // 将Embedding添加到文档中
            List<Document> enrichedChunks = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                List<Double> embedding = embeddings.get(i);

                // 创建新文档，添加embedding
                // 注意：Document类使用Float列表，需要转换
                List<Float> floatEmbedding = embedding.stream()
                    .map(Double::floatValue)
                    .toList();

                enrichedChunks.add(Document.builder()
                    .id(chunk.getId())
                    .text(chunk.getText())
                    .metadata(chunk.getMetadata())
                    .denseVector(floatEmbedding)
                    .build());
            }

            return enrichedChunks;

        } catch (Exception e) {
            throw new IngestionException(
                "步骤5失败: Embedding生成失败 - " + e.getMessage(),
                e,
                "检查DashScope API密钥和网络连接"
            );
        }
    }

    /**
     * 步骤6: 存入向量数据库
     */
    private void storeToVectorDb(List<Document> chunks) throws IngestionException {
        try {
            vectorStore.batchUpsert(chunks);
        } catch (Exception e) {
            throw new IngestionException(
                "步骤6失败: 向量存储失败 - " + e.getMessage(),
                e,
                "检查Milvus服务是否运行，连接配置是否正确"
            );
        }
    }

    // ==================== Getters & Setters ====================

    public void setContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
    }

    // ==================== 内部类 ====================

    /**
     * 摄取结果
     */
    public static class IngestionResult {
        private final String sourcePath;
        private Document sourceDocument;
        private int chunksCreated;
        private int chunksProcessed;
        private boolean success;
        private long durationMs;
        private String errorMessage;

        public IngestionResult(String sourcePath) {
            this.sourcePath = sourcePath;
        }

        // Getters and Setters
        public String getSourcePath() { return sourcePath; }
        public Document getSourceDocument() { return sourceDocument; }
        public void setSourceDocument(Document sourceDocument) { this.sourceDocument = sourceDocument; }
        public int getChunksCreated() { return chunksCreated; }
        public void setChunksCreated(int chunksCreated) { this.chunksCreated = chunksCreated; }
        public int getChunksProcessed() { return chunksProcessed; }
        public void setChunksProcessed(int chunksProcessed) { this.chunksProcessed = chunksProcessed; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        @Override
        public String toString() {
            if (success) {
                return String.format("IngestionResult{source='%s', chunks=%d, duration=%dms}",
                    sourcePath, chunksProcessed, durationMs);
            } else {
                return String.format("IngestionResult{source='%s', failed='%s'}",
                    sourcePath, errorMessage);
            }
        }
    }

    /**
     * 摄取异常
     */
    public static class IngestionException extends Exception {
        private final IngestionResult result;
        private final String suggestion;

        public IngestionException(String message, Throwable cause, IngestionResult result) {
            super(message, cause);
            this.result = result;
            this.suggestion = null;
        }

        public IngestionException(String message, Throwable cause, String suggestion) {
            super(message, cause);
            this.result = null;
            this.suggestion = suggestion;
        }

        public IngestionResult getResult() { return result; }
        public String getSuggestion() { return suggestion; }

        @Override
        public String getMessage() {
            String msg = super.getMessage();
            if (suggestion != null) {
                msg += " | 建议: " + suggestion;
            }
            return msg;
        }
    }
}
