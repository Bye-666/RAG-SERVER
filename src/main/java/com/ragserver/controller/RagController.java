package com.ragserver.controller;

import com.ragserver.ingestion.IngestionPipeline;
import com.ragserver.retrieval.model.Document;
import com.ragserver.service.DocumentService;
import com.ragserver.service.IngestionService;
import com.ragserver.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG管理API
 *
 * <p>提供文档摄取、查询、管理的REST接口。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "RAG核心接口", description = "文档摄取、RAG查询、文档管理")
public class RagController {

    private final IngestionService ingestionService;
    private final DocumentService documentService;
    private final RagService ragService;
    private final CacheManager cacheManager;

    public RagController(IngestionService ingestionService,
                        DocumentService documentService,
                        RagService ragService,
                        CacheManager cacheManager) {
        this.ingestionService = ingestionService;
        this.documentService = documentService;
        this.ragService = ragService;
        this.cacheManager = cacheManager;
    }

    /**
     * 摄取文档
     *
     * <p>上传PDF文件并进行摄取处理。</p>
     *
     * @param file 上传的PDF文件
     * @param collection Collection名称（可选）
     * @return 摄取结果
     */
    @PostMapping("/ingest")
    @Operation(
        summary = "摄取文档",
        description = "上传 PDF 文档，自动进行解析、切分、向量化并存储到 Milvus"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "摄取成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<Map<String, Object>> ingest(
            @Parameter(description = "PDF 文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Collection 名称（可选）")
            @RequestParam(value = "collection", required = false) String collection) {

        log.info("接收文档摄取请求：filename={}, size={}, collection={}",
                file.getOriginalFilename(), file.getSize(), collection);

        try {
            // 保存文件到临时目录
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("upload_", ".pdf");
            file.transferTo(tempFile.toFile());

            // 调用摄取服务，传递collection参数
            IngestionService.BatchIngestionResult result = ingestionService.ingestDocuments(
                    List.of(tempFile), collection);

            // 清理临时文件
            java.nio.file.Files.deleteIfExists(tempFile);

            if (result.getSuccessCount() > 0) {
                IngestionPipeline.IngestionResult ingestionResult = result.getSuccesses().get(0);

                // 清除统计数据和文档列表缓存
                clearStatsCache();

                // 构建详细的耗时统计信息
                Map<String, Object> timingDetails = new HashMap<>();
                timingDetails.put("loading", ingestionResult.getLoadingDurationMs());
                timingDetails.put("splitting", ingestionResult.getSplittingDurationMs());
                timingDetails.put("refining", ingestionResult.getRefiningDurationMs());
                timingDetails.put("enriching", ingestionResult.getEnrichingDurationMs());
                timingDetails.put("embedding", ingestionResult.getEmbeddingDurationMs());
                timingDetails.put("storage", ingestionResult.getStorageDurationMs());
                timingDetails.put("total", ingestionResult.getDurationMs());

                Map<String, Object> response = new HashMap<>();
                response.put("message", "文档摄取成功");
                response.put("filename", file.getOriginalFilename());
                response.put("chunkCount", ingestionResult.getChunksProcessed());
                response.put("collection", collection != null ? collection : "default");
                response.put("status", "SUCCESS");
                response.put("timing", timingDetails);

                return ResponseEntity.ok(response);
            } else {
                IngestionService.FailureInfo failure = result.getFailures().get(0);
                String errorMsg = failure.getErrorMessage();

                // 优先使用异常的 ErrorType
                String errorCode = "UNKNOWN";
                if (failure.getException() != null &&
                    failure.getException().getErrorType() == IngestionPipeline.IngestionException.ErrorType.DUPLICATE_FILE) {
                    errorCode = "DUPLICATE_FILE";
                } else if (errorMsg != null && errorMsg.contains("文件已存在")) {
                    errorCode = "DUPLICATE_FILE";  // 备用方案
                }

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("message", "文档摄取失败: " + errorMsg);
                errorResponse.put("filename", file.getOriginalFilename());
                errorResponse.put("status", "FAILED");
                errorResponse.put("errorCode", errorCode);

                return ResponseEntity.ok(errorResponse);
            }

        } catch (Exception e) {
            log.error("文档摄取失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "文档摄取失败：" + e.getMessage()));
        }
    }

    /**
     * 列出所有文档
     *
     * @return 文档列表
     */
    @GetMapping("/documents")
    @Operation(
        summary = "列出所有文档",
        description = "获取系统中所有已摄取的文档列表，包括文档状态、Chunk 数量等信息"
    )
    @ApiResponse(responseCode = "200", description = "成功返回文档列表")
    public ResponseEntity<Map<String, Object>> listDocuments() {
        log.info("列出所有文档");

        try {
            List<DocumentService.DocumentInfo> documents = documentService.listDocuments();

            return ResponseEntity.ok(Map.of(
                    "documents", documents,
                    "total", documents.size()
            ));

        } catch (Exception e) {
            log.error("列出文档失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "列出文档失败：" + e.getMessage()));
        }
    }

    /**
     * 获取文档详情
     *
     * @param id 文档ID（文件哈希）
     * @return 文档详情
     */
    @GetMapping("/documents/{id}")
    @Operation(
        summary = "获取文档详情",
        description = "根据文档 ID 获取文档的详细信息，包括文件路径、大小、处理状态等"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回文档详情"),
        @ApiResponse(responseCode = "404", description = "文档不存在")
    })
    public ResponseEntity<Map<String, Object>> getDocument(
            @Parameter(description = "文档ID（文件哈希）", required = true)
            @PathVariable String id) {
        log.info("获取文档详情：id={}", id);

        try {
            DocumentService.DocumentDetail detail = documentService.getDocumentDetail(id);

            return ResponseEntity.ok(Map.of(
                    "fileHash", detail.getFileHash() != null ? detail.getFileHash() : "",
                    "filePath", detail.getFilePath() != null ? detail.getFilePath() : "",
                    "fileSize", detail.getFileSize() != null ? detail.getFileSize() : 0L,
                    "status", detail.getStatus() != null ? detail.getStatus() : "",
                    "chunkCount", detail.getChunkCount() != null ? detail.getChunkCount() : 0,
                    "processedAt", detail.getProcessedAt() != null ? detail.getProcessedAt().toString() : "",
                    "errorMsg", detail.getErrorMsg() != null ? detail.getErrorMsg() : ""
            ));

        } catch (Exception e) {
            log.error("获取文档详情失败：id={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取文档的所有chunks
     *
     * @param id 文档ID（文件哈希或文件路径）
     * @return chunks列表
     */
    @GetMapping("/documents/{id}/chunks")
    @Operation(
        summary = "获取文档的所有 Chunks",
        description = "获取指定文档切分后的所有文本块（Chunks），用于查看文档如何被切分和存储"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回 Chunks 列表"),
        @ApiResponse(responseCode = "500", description = "服务器错误")
    })
    public ResponseEntity<Map<String, Object>> getDocumentChunks(
            @Parameter(description = "文档ID（文件哈希或文件路径）", required = true)
            @PathVariable String id) {
        log.info("获取文档chunks：id={}", id);

        try {
            List<DocumentService.ChunkInfo> chunks = documentService.getDocumentChunks(id);

            return ResponseEntity.ok(Map.of(
                    "chunks", chunks,
                    "total", chunks.size()
            ));

        } catch (Exception e) {
            log.error("获取文档chunks失败：id={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取文档chunks失败：" + e.getMessage()));
        }
    }

    /**
     * 删除文档
     *
     * @param id 文档ID（文件路径或哈希）
     * @return 删除结果
     */
    @DeleteMapping("/documents/{id}")
    @Operation(
        summary = "删除文档",
        description = "删除指定文档及其所有 Chunks，同时清理向量数据库和摄取历史记录"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "500", description = "删除失败")
    })
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @Parameter(description = "文档ID（文件路径或哈希）", required = true)
            @PathVariable String id) {
        log.info("删除文档：id={}", id);

        try {
            documentService.deleteDocument(id);

            // 清除统计数据和文档列表缓存
            clearStatsCache();

            return ResponseEntity.ok(Map.of(
                    "message", "文档已删除",
                    "documentId", id
            ));

        } catch (Exception e) {
            log.error("删除文档失败：id={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "删除文档失败：" + e.getMessage()));
        }
    }

    /**
     * RAG查询
     *
     * @param request 查询请求
     * @return 查询结果
     */
    @PostMapping("/query")
    @Operation(
        summary = "RAG 查询（非流式）",
        description = "基于知识库回答问题，返回完整答案（非流式）。适用于不需要实时显示的场景"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    public ResponseEntity<Map<String, Object>> query(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "查询请求参数",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
                        {
                          "question": "什么是 RAG？",
                          "topK": 5,
                          "enableRerank": false
                        }
                        """)
                )
            )
            @RequestBody Map<String, Object> request) {
        String question = (String) request.get("question");
        Integer topK = request.containsKey("topK") ? (Integer) request.get("topK") : 10;
        Boolean enableRerank = request.containsKey("enableRerank") ? (Boolean) request.get("enableRerank") : false;

        log.info("RAG查询：question={}, topK={}, enableRerank={}", question, topK, enableRerank);

        // 输入校验
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "查询问题不能为空"));
        }

        if (topK < 1 || topK > 20) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "topK必须在1-20之间"));
        }

        try {
            RagService.QueryResult queryResult = ragService.query(question, topK, enableRerank);

            // 构建详细的检索耗时统计
            Map<String, Object> retrievalTiming = new HashMap<>();
            retrievalTiming.put("denseSearch", queryResult.getDenseSearchDurationMs());
            retrievalTiming.put("sparseSearch", queryResult.getSparseSearchDurationMs());
            retrievalTiming.put("rrfFusion", queryResult.getRrfFusionDurationMs());
            retrievalTiming.put("reranker", queryResult.getRerankerDurationMs());
            retrievalTiming.put("total", queryResult.getRetrievalDurationMs());

            // 构建总体耗时统计
            Map<String, Object> timing = new HashMap<>();
            timing.put("retrieval", retrievalTiming);
            timing.put("promptBuild", queryResult.getPromptBuildDurationMs());
            timing.put("llmGeneration", queryResult.getLlmGenerationDurationMs());
            timing.put("citationBuild", queryResult.getCitationBuildDurationMs());
            timing.put("total", queryResult.getTotalDurationMs());

            // 构建检索文档信息
            List<Map<String, Object>> sources = new ArrayList<>();
            if (queryResult.getRetrievedDocuments() != null) {
                for (Document doc : queryResult.getRetrievedDocuments()) {
                    Map<String, Object> source = new HashMap<>();
                    source.put("id", doc.getId());
                    source.put("text", doc.getText());
                    source.put("metadata", doc.getMetadata());
                    sources.add(source);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("answer", queryResult.getAnswer());
            result.put("timing", timing);
            result.put("sources", sources);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("RAG查询失败：question={}, error={}", question, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "查询失败：" + e.getMessage()));
        }
    }

    /**
     * 流式RAG查询
     *
     * @param question 用户问题
     * @param topK 检索文档数量（可选，默认10）
     * @param enableRerank 是否启用重排序（可选，默认false）
     * @return 流式答案文本
     */
    @GetMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "RAG 流式查询",
        description = "基于知识库回答问题，答案以 SSE (Server-Sent Events) 流式返回，支持实时显示。适用于需要逐字展示的聊天场景"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功，返回流式内容"),
        @ApiResponse(responseCode = "400", description = "参数错误")
    })
    public Flux<String> queryStream(
            @Parameter(description = "用户问题", required = true, example = "什么是 RAG？")
            @RequestParam String question,
            @Parameter(description = "检索文档数量", example = "10")
            @RequestParam(defaultValue = "10") int topK,
            @Parameter(description = "是否启用重排序", example = "false")
            @RequestParam(defaultValue = "false") boolean enableRerank) {

        log.info("流式RAG查询：question={}, topK={}, enableRerank={}", question, topK, enableRerank);

        // 输入校验
        if (question == null || question.trim().isEmpty()) {
            return Flux.just("错误：查询问题不能为空");
        }

        if (topK < 1 || topK > 20) {
            return Flux.just("错误：topK必须在1-20之间");
        }

        return ragService.queryStream(question, topK, enableRerank);
    }

    /**
     * 列出所有Collection
     *
     * @return Collection列表
     */
    @GetMapping("/collections")
    @Operation(
        summary = "列出所有 Collection",
        description = "获取向量数据库中所有 Collection 的列表及其统计信息"
    )
    @ApiResponse(responseCode = "200", description = "成功返回 Collection 列表")
    public ResponseEntity<Map<String, Object>> listCollections() {
        log.info("列出所有Collection");

        try {
            // 简化版：返回当前Collection的统计
            DocumentService.CollectionStats stats = documentService.getCollectionStats();

            return ResponseEntity.ok(Map.of(
                    "collections", List.of(Map.of(
                            "name", "rag_knowledge_hub_dev",
                            "documentCount", stats.getTotalDocuments(),
                            "vectorCount", stats.getVectorCount()
                    ))
            ));

        } catch (Exception e) {
            log.error("列出Collection失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "列出Collection失败：" + e.getMessage()));
        }
    }

    /**
     * 清除统计数据缓存
     *
     * <p>在文档摄取或删除后调用，确保Dashboard显示最新数据。</p>
     */
    private void clearStatsCache() {
        try {
            if (cacheManager.getCache("stats") != null) {
                cacheManager.getCache("stats").clear();
                log.debug("已清除stats缓存");
            }
            if (cacheManager.getCache("documents") != null) {
                cacheManager.getCache("documents").clear();
                log.debug("已清除documents缓存");
            }
        } catch (Exception e) {
            log.warn("清除缓存失败：{}", e.getMessage());
        }
    }
}
