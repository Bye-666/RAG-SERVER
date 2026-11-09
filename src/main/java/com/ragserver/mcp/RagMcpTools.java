package com.ragserver.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.ragserver.service.DocumentService;
import com.ragserver.service.IngestionService;
import com.ragserver.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * RAG MCP Tools Configuration
 *
 * <p>定义 RAG 相关的 MCP Tools，使用 Spring AI Function Calling 机制。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class RagMcpTools {

    /**
     * RAG Query 请求参数
     */
    public record RagQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("User question to query the knowledge base")
            String question,

            @JsonPropertyDescription("Number of documents to retrieve (default: 10, range: 1-50)")
            Integer topK,

            @JsonPropertyDescription("Enable LLM reranking for better results (default: false)")
            Boolean enableRerank
    ) {}

    /**
     * RAG Ingest 请求参数
     */
    public record RagIngestRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("Path to the document file to ingest")
            String filePath,

            @JsonPropertyDescription("Name of the collection to store the document (default: rag_collection)")
            String collectionName,

            @JsonPropertyDescription("Custom metadata to attach to the document")
            Map<String, Object> metadata
    ) {}

    /**
     * RAG Ingest 响应结果
     */
    public record RagIngestResponse(
            boolean success,
            String message,
            String filePath,
            String fileName,
            Integer chunksProcessed,
            Long durationMs
    ) {}

    /**
     * RAG List Docs 请求参数
     */
    public record RagListDocsRequest(
            @JsonPropertyDescription("Filter by status: SUCCESS, FAILED, or null for all (default: all)")
            String status
    ) {}

    /**
     * 注册 rag_query Tool
     *
     * @param ragService RAG 核心服务
     * @return Function
     */
    @Bean
    @Description("Query the RAG knowledge base with hybrid retrieval (Dense + Sparse)")
    public Function<RagQueryRequest, String> ragQuery(RagService ragService) {
        log.info("注册 MCP Tool: ragQuery");

        return request -> {
            try {
                String question = request.question();
                int topK = (request.topK() != null) ? request.topK() : 10;
                boolean enableRerank = (request.enableRerank() != null) ? request.enableRerank() : false;

                log.info("MCP Tool 调用: ragQuery, question={}, topK={}, enableRerank={}",
                        question, topK, enableRerank);

                RagService.QueryResult result = ragService.query(question, topK, enableRerank);

                return result.getAnswer() + "\n\n--- Performance ---\n" +
                        "Total: " + result.getTotalDurationMs() + " ms\n" +
                        "Retrieval: " + result.getRetrievalDurationMs() + " ms\n" +
                        "LLM: " + result.getLlmGenerationDurationMs() + " ms";

            } catch (Exception e) {
                log.error("ragQuery failed", e);
                return "Error: " + e.getMessage();
            }
        };
    }

    /**
     * 注册 rag_ingest Tool
     *
     * @param ingestionService 文档摄取服务
     * @return Function
     */
    @Bean
    @Description("Ingest a document into the RAG knowledge base")
    public Function<RagIngestRequest, RagIngestResponse> ragIngest(IngestionService ingestionService) {
        log.info("注册 MCP Tool: ragIngest");

        return request -> {
            long startTime = System.currentTimeMillis();

            try {
                String filePath = request.filePath();
                String collectionName = (request.collectionName() != null)
                        ? request.collectionName()
                        : "rag_collection";
                Map<String, Object> metadata = request.metadata();

                log.info("MCP Tool 调用: ragIngest, filePath={}, collectionName={}, metadata={}",
                        filePath, collectionName, metadata);

                // 记录元数据（当前版本暂不支持自定义元数据）
                if (metadata != null && !metadata.isEmpty()) {
                    log.warn("自定义元数据功能暂未实现，将忽略: {}", metadata);
                }

                // 调用摄取服务
                Path documentPath = Path.of(filePath);
                IngestionService.BatchIngestionResult batchResult =
                        ingestionService.ingestDocuments(List.of(documentPath), collectionName);

                long durationMs = System.currentTimeMillis() - startTime;

                // 处理结果
                if (batchResult.getSuccessCount() > 0) {
                    var ingestionResult = batchResult.getSuccesses().get(0);
                    log.info("文档摄取成功: file={}, chunks={}, duration={}ms",
                            filePath, ingestionResult.getChunksProcessed(), durationMs);

                    return new RagIngestResponse(
                            true,
                            "Document ingested successfully",
                            filePath,
                            documentPath.getFileName().toString(),
                            ingestionResult.getChunksProcessed(),
                            durationMs
                    );
                } else {
                    String errorMessage = "Unknown error";
                    if (!batchResult.getFailures().isEmpty()) {
                        var failure = batchResult.getFailures().get(0);
                        errorMessage = failure.getErrorMessage();
                    }
                    log.error("文档摄取失败: file={}, error={}", filePath, errorMessage);

                    return new RagIngestResponse(
                            false,
                            "Ingestion failed: " + errorMessage,
                            filePath,
                            documentPath.getFileName().toString(),
                            0,
                            durationMs
                    );
                }

            } catch (Exception e) {
                long durationMs = System.currentTimeMillis() - startTime;
                log.error("ragIngest failed", e);

                return new RagIngestResponse(
                        false,
                        "Error: " + e.getMessage(),
                        request.filePath(),
                        null,
                        0,
                        durationMs
                );
            }
        };
    }

    /**
     * 注册 rag_list_docs Tool
     *
     * @param documentService 文档服务
     * @return Function
     */
    @Bean
    @Description("List all documents in the RAG knowledge base")
    public Function<RagListDocsRequest, List<DocumentService.DocumentInfo>> ragListDocs(DocumentService documentService) {
        log.info("注册 MCP Tool: ragListDocs");

        return request -> {
            try {
                String statusFilter = (request != null) ? request.status() : null;

                log.info("MCP Tool 调用: ragListDocs, statusFilter={}", statusFilter);

                // 根据状态过滤文档
                if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                    try {
                        var status = com.ragserver.entity.IngestionHistory.IngestionStatus.valueOf(statusFilter.toUpperCase());
                        List<DocumentService.DocumentInfo> docs = documentService.listDocumentsByStatus(status);
                        log.info("文档列表获取成功：status={}, count={}", statusFilter, docs.size());
                        return docs;
                    } catch (IllegalArgumentException e) {
                        log.warn("无效的状态过滤器：{}, 返回所有文档", statusFilter);
                        List<DocumentService.DocumentInfo> docs = documentService.listDocuments();
                        log.info("文档列表获取成功：count={}", docs.size());
                        return docs;
                    }
                } else {
                    // 获取所有文档
                    List<DocumentService.DocumentInfo> docs = documentService.listDocuments();
                    log.info("文档列表获取成功：count={}", docs.size());
                    return docs;
                }

            } catch (Exception e) {
                log.error("ragListDocs failed", e);
                return List.of();  // 返回空列表，不抛异常
            }
        };
    }
}

