package com.ragserver.controller;

import com.ragserver.service.DocumentService;
import com.ragserver.service.IngestionService;
import com.ragserver.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * RAG管理API
 *
 * <p>提供文档摄取、查询、管理的REST接口：</p>
 * <ul>
 *   <li>文档摄取：上传PDF并处理</li>
 *   <li>文档管理：列表、详情、删除</li>
 *   <li>RAG查询：问答接口</li>
 *   <li>Collection管理：列表查询</li>
 * </ul>
 *
 * <h3>API端点</h3>
 * <pre>
 * POST   /api/ingest              - 摄取文档
 * GET    /api/documents           - 列出所有文档
 * GET    /api/documents/{id}      - 获取文档详情
 * DELETE /api/documents/{id}      - 删除文档
 * POST   /api/query               - RAG查询
 * GET    /api/collections         - 列出所有Collection
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>
 * # 摄取文档
 * curl -X POST -F "file=@document.pdf" http://localhost:8080/api/ingest
 *
 * # RAG查询
 * curl -X POST http://localhost:8080/api/query \
 *   -H "Content-Type: application/json" \
 *   -d '{"question": "什么是RAG？", "topK": 5}'
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class RagController {

    private final IngestionService ingestionService;
    private final DocumentService documentService;
    private final RagService ragService;

    public RagController(IngestionService ingestionService,
                        DocumentService documentService,
                        RagService ragService) {
        this.ingestionService = ingestionService;
        this.documentService = documentService;
        this.ragService = ragService;
    }

    /**
     * 摄取文档
     *
     * <p>上传PDF文件并进行摄取处理。</p>
     *
     * <h3>请求示例</h3>
     * <pre>
     * POST /api/ingest
     * Content-Type: multipart/form-data
     *
     * file: document.pdf
     * collection: rag_knowledge_hub (可选)
     * </pre>
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "fileHash": "abc123...",
     *   "chunkCount": 25,
     *   "status": "SUCCESS"
     * }
     * }</pre>
     *
     * @param file 上传的PDF文件
     * @param collection Collection名称（可选）
     * @return 摄取结果
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "collection", required = false) String collection) {

        log.info("接收文档摄取请求：filename={}, size={}", file.getOriginalFilename(), file.getSize());

        try {
            // 调用摄取服务（简化版，实际需要实现文件保存和处理）
            String message = String.format("文档摄取功能开发中。文件：%s，大小：%d字节",
                    file.getOriginalFilename(), file.getSize());

            return ResponseEntity.ok(Map.of(
                    "message", message,
                    "filename", file.getOriginalFilename(),
                    "size", file.getSize(),
                    "status", "PENDING"
            ));

        } catch (Exception e) {
            log.error("文档摄取失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "文档摄取失败：" + e.getMessage()));
        }
    }

    /**
     * 列出所有文档
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "documents": [
     *     {
     *       "fileHash": "abc123",
     *       "filePath": "/path/to/doc.pdf",
     *       "status": "SUCCESS",
     *       "chunkCount": 25
     *     }
     *   ]
     * }
     * }</pre>
     *
     * @return 文档列表
     */
    @GetMapping("/documents")
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
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "fileHash": "abc123",
     *   "filePath": "/path/to/doc.pdf",
     *   "fileSize": 1024000,
     *   "status": "SUCCESS",
     *   "chunkCount": 25,
     *   "processedAt": "2025-01-04T10:00:00Z"
     * }
     * }</pre>
     *
     * @param id 文档ID（文件哈希）
     * @return 文档详情
     */
    @GetMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> getDocument(@PathVariable String id) {
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
     * 删除文档
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "message": "文档已删除",
     *   "filePath": "/path/to/doc.pdf"
     * }
     * }</pre>
     *
     * @param id 文档ID（文件路径或哈希）
     * @return 删除结果
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String id) {
        log.info("删除文档：id={}", id);

        try {
            documentService.deleteDocument(id);

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
     * <h3>请求示例</h3>
     * <pre>{@code
     * {
     *   "question": "什么是RAG？",
     *   "topK": 5,
     *   "enableRerank": false
     * }
     * }</pre>
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "answer": "RAG是检索增强生成...",
     *   "question": "什么是RAG？"
     * }
     * }</pre>
     *
     * @param request 查询请求
     * @return 查询结果
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestBody Map<String, Object> request) {
        String question = (String) request.get("question");
        Integer topK = request.containsKey("topK") ? (Integer) request.get("topK") : 10;
        Boolean enableRerank = request.containsKey("enableRerank") ? (Boolean) request.get("enableRerank") : false;

        log.info("RAG查询：question={}, topK={}, enableRerank={}", question, topK, enableRerank);

        try {
            String answer = ragService.query(question, topK, enableRerank);

            return ResponseEntity.ok(Map.of(
                    "answer", answer,
                    "question", question
            ));

        } catch (Exception e) {
            log.error("RAG查询失败：question={}, error={}", question, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "查询失败：" + e.getMessage()));
        }
    }

    /**
     * 列出所有Collection
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "collections": [
     *     {
     *       "name": "rag_knowledge_hub_dev",
     *       "documentCount": 100,
     *       "vectorCount": 1500
     *     }
     *   ]
     * }
     * }</pre>
     *
     * @return Collection列表
     */
    @GetMapping("/collections")
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
}
