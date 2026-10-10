package com.ragserver.controller;

import com.ragserver.repository.IngestionHistoryRepository;
import com.ragserver.service.DocumentService;
import com.ragserver.service.ImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计信息API
 *
 * <p>提供系统统计信息的REST接口：</p>
 * <ul>
 *   <li>总览统计：文档数、向量数、图片数等</li>
 *   <li>Collection统计：各Collection的详细信息</li>
 * </ul>
 *
 * <h3>API端点</h3>
 * <pre>
 * GET /api/stats/overview    - 获取系统总览统计
 * GET /api/stats/collections - 获取Collection统计
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>
 * curl http://localhost:8080/api/stats/overview
 * {
 *   "totalDocuments": 100,
 *   "successDocuments": 95,
 *   "failedDocuments": 5,
 *   "vectorCount": 1500,
 *   "imageCount": 250
 * }
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final DocumentService documentService;
    private final ImageStorageService imageStorageService;
    private final IngestionHistoryRepository ingestionHistoryRepository;

    public StatsController(DocumentService documentService,
                          ImageStorageService imageStorageService,
                          IngestionHistoryRepository ingestionHistoryRepository) {
        this.documentService = documentService;
        this.imageStorageService = imageStorageService;
        this.ingestionHistoryRepository = ingestionHistoryRepository;
    }

    /**
     * 获取系统总览统计
     *
     * <p>返回系统级别的统计信息。</p>
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "totalDocuments": 100,
     *   "successDocuments": 95,
     *   "failedDocuments": 5,
     *   "vectorCount": 1500,
     *   "imageCount": 250
     * }
     * }</pre>
     *
     * @return 统计信息
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        log.info("获取系统总览统计");

        try {
            // 1. 获取Collection分组统计
            List<Object[]> collectionGroups = ingestionHistoryRepository.groupByCollection();

            // 计算总文档数和总chunk数
            long totalDocuments = 0;
            long totalChunks = 0;
            for (Object[] row : collectionGroups) {
                totalDocuments += (Long) row[1];
                totalChunks += (Long) row[2];
            }

            int totalCollections = collectionGroups.size();

            // 2. 获取成功/失败文档数
            DocumentService.CollectionStats collectionStats = documentService.getCollectionStats();

            // 3. 获取图片统计
            ImageStorageService.StorageStats storageStats = imageStorageService.getStats();

            // 4. 构建响应
            Map<String, Object> overview = new HashMap<>();
            overview.put("totalDocuments", totalDocuments);
            overview.put("successDocuments", collectionStats.getSuccessDocuments());
            overview.put("failedDocuments", collectionStats.getFailedDocuments());
            overview.put("vectorCount", totalChunks);  // 使用数据库统计的chunk数
            overview.put("totalChunks", totalChunks);  // 别名
            overview.put("totalCollections", totalCollections);
            overview.put("imageCount", storageStats.getTotalImages());

            log.info("系统总览统计：文档数={}, Chunk数={}, Collection数={}, 图片数={}",
                    totalDocuments,
                    totalChunks,
                    totalCollections,
                    storageStats.getTotalImages());

            return ResponseEntity.ok(overview);

        } catch (Exception e) {
            log.error("获取统计信息失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取统计信息失败：" + e.getMessage()));
        }
    }

    /**
     * 获取Collection统计
     *
     * <p>返回Collection级别的详细统计信息。</p>
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "collectionName": "rag_knowledge_hub_dev",
     *   "totalDocuments": 100,
     *   "successDocuments": 95,
     *   "failedDocuments": 5,
     *   "vectorCount": 1500
     * }
     * }</pre>
     *
     * @return Collection统计信息
     */
    @GetMapping("/collections")
    public ResponseEntity<List<Map<String, Object>>> getCollections() {
        log.info("获取Collection统计");

        try {
            // 获取按Collection分组的统计
            List<Object[]> collectionGroups = ingestionHistoryRepository.groupByCollection();

            List<Map<String, Object>> collections = new ArrayList<>();

            for (Object[] row : collectionGroups) {
                String collectionName = (String) row[0];
                Long documentCount = (Long) row[1];
                Long chunkCount = (Long) row[2];

                Map<String, Object> collection = new HashMap<>();
                collection.put("collectionName", collectionName != null ? collectionName : "default");
                collection.put("documentCount", documentCount);
                collection.put("chunkCount", chunkCount);

                collections.add(collection);
            }

            log.info("Collection统计：共{}个collection", collections.size());

            return ResponseEntity.ok(collections);

        } catch (Exception e) {
            log.error("获取Collection统计失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
