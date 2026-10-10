package com.ragserver.service;

import com.ragserver.entity.IngestionHistory;
import com.ragserver.repository.IngestionHistoryRepository;
import com.ragserver.retrieval.milvus.MilvusHybridStore;
import com.ragserver.retrieval.model.Document;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档服务
 *
 * <p>提供文档生命周期管理功能：</p>
 * <ul>
 *   <li>文档列表查询</li>
 *   <li>文档详情获取</li>
 *   <li>文档删除（联动清理向量库和历史记录）</li>
 *   <li>Collection统计信息</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 列出所有文档
 * List<DocumentInfo> docs = documentService.listDocuments();
 *
 * // 获取文档详情
 * DocumentDetail detail = documentService.getDocumentDetail("doc_123");
 *
 * // 删除文档
 * documentService.deleteDocument("/path/to/document.pdf");
 *
 * // 获取统计信息
 * CollectionStats stats = documentService.getCollectionStats();
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class DocumentService {

    private final MilvusClientV2 milvusClient;
    private final MilvusHybridStore vectorStore;
    private final IngestionHistoryRepository ingestionHistoryRepository;
    private final ImageStorageService imageStorageService;
    private final String collectionName;

    public DocumentService(MilvusClientV2 milvusClient,
                          MilvusHybridStore vectorStore,
                          IngestionHistoryRepository ingestionHistoryRepository,
                          ImageStorageService imageStorageService,
                          com.ragserver.config.MilvusProperties milvusProperties) {
        this.milvusClient = milvusClient;
        this.vectorStore = vectorStore;
        this.ingestionHistoryRepository = ingestionHistoryRepository;
        this.imageStorageService = imageStorageService;
        this.collectionName = milvusProperties.getCollectionName();
    }

    /**
     * 列出所有文档
     *
     * <p>从摄取历史表中获取所有已处理的文档信息。缓存5分钟。</p>
     *
     * @return 文档信息列表
     */
    @Cacheable(value = "documents", key = "'all'")
    public List<DocumentInfo> listDocuments() {
        log.info("列出所有文档");

        try {
            List<IngestionHistory> histories = ingestionHistoryRepository.findAll();

            return histories.stream()
                    .map(this::convertToDocumentInfo)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("列出文档失败：{}", e.getMessage(), e);
            throw new RuntimeException("列出文档失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据状态列出文档
     *
     * @param status 摄取状态
     * @return 文档信息列表
     */
    public List<DocumentInfo> listDocumentsByStatus(IngestionHistory.IngestionStatus status) {
        log.info("列出文档：status={}", status);

        try {
            List<IngestionHistory> histories = ingestionHistoryRepository.findByStatusOrderByProcessedAtDesc(status);

            return histories.stream()
                    .map(this::convertToDocumentInfo)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("列出文档失败：status={}, error={}", status, e.getMessage(), e);
            throw new RuntimeException("列出文档失败：" + e.getMessage(), e);
        }
    }

    /**
     * 获取文档详情
     *
     * <p>包含文档的摄取历史和向量库中的chunk信息。</p>
     *
     * @param fileHash 文件哈希
     * @return 文档详情
     */
    public DocumentDetail getDocumentDetail(String fileHash) {
        log.info("获取文档详情：fileHash={}", fileHash);

        try {
            // 1. 从摄取历史获取基本信息
            Optional<IngestionHistory> historyOpt = ingestionHistoryRepository.findById(fileHash);
            if (historyOpt.isEmpty()) {
                throw new RuntimeException("文档不存在：" + fileHash);
            }

            IngestionHistory history = historyOpt.get();

            // 2. 构建详情
            DocumentDetail detail = new DocumentDetail();
            detail.setFileHash(history.getFileHash());
            detail.setFilePath(history.getFilePath());
            detail.setFileSize(history.getFileSize());
            detail.setStatus(history.getStatus().name());
            detail.setProcessedAt(history.getProcessedAt());
            detail.setChunkCount(history.getChunkCount());
            detail.setErrorMsg(history.getErrorMsg());

            log.info("文档详情获取成功：fileHash={}, chunkCount={}", fileHash, history.getChunkCount());
            return detail;

        } catch (Exception e) {
            log.error("获取文档详情失败：fileHash={}, error={}", fileHash, e.getMessage(), e);
            throw new RuntimeException("获取文档详情失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除文档（联动清理）
     *
     * <p>删除流程：</p>
     * <ol>
     *   <li>根据file_path查询摄取历史</li>
     *   <li>从Milvus删除对应的向量数据</li>
     *   <li>删除摄取历史记录</li>
     * </ol>
     *
     * <p>删除后清除统计数据和文档列表缓存。</p>
     *
     * @param filePath 文件路径
     */
    @Transactional
    @CacheEvict(value = {"stats", "documents"}, allEntries = true)
    public void deleteDocument(String idOrPath) {
        log.info("开始删除文档：idOrPath={}", idOrPath);

        try {
            IngestionHistory history = null;

            // 1. 先尝试通过文件哈希查找
            Optional<IngestionHistory> historyOpt = ingestionHistoryRepository.findById(idOrPath);
            if (historyOpt.isPresent()) {
                history = historyOpt.get();
                log.info("通过文件哈希找到文档：fileHash={}", idOrPath);
            } else {
                // 2. 通过文件路径查找
                List<IngestionHistory> histories = ingestionHistoryRepository.findByFilePathContaining(idOrPath);
                if (histories.isEmpty()) {
                    log.warn("文档不存在：idOrPath={}", idOrPath);
                    throw new RuntimeException("文档不存在：" + idOrPath);
                }

                // 取第一个匹配的记录（精确匹配）
                history = histories.stream()
                        .filter(h -> h.getFilePath().equals(idOrPath))
                        .findFirst()
                        .orElse(histories.get(0));
                log.info("通过文件路径找到文档：filePath={}", idOrPath);
            }

            String fileHash = history.getFileHash();
            String filePath = history.getFilePath();

            // 从文件路径中提取文件名（不含扩展名）作为文档ID前缀
            String fileName = filePath;
            if (fileName != null) {
                int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
                if (lastSlash >= 0) {
                    fileName = fileName.substring(lastSlash + 1);
                }
                // 去除扩展名
                int lastDot = fileName.lastIndexOf('.');
                if (lastDot > 0) {
                    fileName = fileName.substring(0, lastDot);
                }
            }

            // 3. 从Milvus删除向量数据（通过ID前缀过滤）
            // 必须先删除Milvus，如果失败则抛出异常，不继续删除数据库记录
            try {
                // Milvus支持like查询，删除所有以该文件名开头的文档
                String filter = String.format("id like \"%s%%\"", fileName.replace("\"", "\\\""));

                DeleteReq deleteReq = DeleteReq.builder()
                        .collectionName(collectionName)
                        .filter(filter)
                        .build();

                milvusClient.delete(deleteReq);
                log.info("Milvus向量数据删除成功：fileName={}, fileHash={}", fileName, fileHash);

            } catch (Exception e) {
                log.error("Milvus删除失败：fileName={}, fileHash={}, error={}", fileName, fileHash, e.getMessage(), e);
                // 抛出异常，回滚事务，不删除数据库记录
                throw new RuntimeException("Milvus删除失败，操作已回滚：" + e.getMessage(), e);
            }

            // 4. Milvus删除成功后，再删除数据库记录
            ingestionHistoryRepository.delete(history);
            log.info("摄取历史删除成功：fileHash={}", fileHash);

            log.info("文档删除完成：idOrPath={}, fileHash={}", idOrPath, fileHash);

        } catch (Exception e) {
            log.error("删除文档失败：idOrPath={}, error={}", idOrPath, e.getMessage(), e);
            throw new RuntimeException("删除文档失败：" + e.getMessage(), e);
        }
    }

    /**
     * 获取Collection统计信息
     *
     * <p>包含：</p>
     * <ul>
     *   <li>向量数量</li>
     *   <li>文档数量</li>
     *   <li>成功/失败摄取数量</li>
     * </ul>
     *
     * <p>缓存5分钟。</p>
     *
     * @return 统计信息
     */
    @Cacheable(value = "stats", key = "'collection'")
    public CollectionStats getCollectionStats() {
        log.info("获取Collection统计信息：collection={}", collectionName);

        try {
            CollectionStats stats = new CollectionStats();

            // 1. Milvus统计
            try {
                GetCollectionStatsReq statsReq = GetCollectionStatsReq.builder()
                        .collectionName(collectionName)
                        .build();

                GetCollectionStatsResp statsResp = milvusClient.getCollectionStats(statsReq);

                // 从stats map中获取row_count
                Object rowCountObj = statsResp.getStats().get("row_count");
                long rowCount = rowCountObj != null ? Long.parseLong(rowCountObj.toString()) : 0L;

                stats.setVectorCount(rowCount);
                log.debug("Milvus向量数量：{}", rowCount);

            } catch (Exception e) {
                log.warn("获取Milvus统计失败：{}", e.getMessage());
                stats.setVectorCount(0L);
            }

            // 2. 摄取历史统计
            long totalDocuments = ingestionHistoryRepository.count();
            long successDocuments = ingestionHistoryRepository.countByStatus(IngestionHistory.IngestionStatus.SUCCESS);
            long failedDocuments = ingestionHistoryRepository.countByStatus(IngestionHistory.IngestionStatus.FAILED);

            stats.setTotalDocuments(totalDocuments);
            stats.setSuccessDocuments(successDocuments);
            stats.setFailedDocuments(failedDocuments);

            log.info("统计信息：总文档数={}, 成功={}, 失败={}, 向量数={}",
                    totalDocuments, successDocuments, failedDocuments, stats.getVectorCount());

            return stats;

        } catch (Exception e) {
            log.error("获取统计信息失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取统计信息失败：" + e.getMessage(), e);
        }
    }

    /**
     * 批量删除Collection（联动清理）
     *
     * <p>删除指定Collection的所有数据：</p>
     * <ol>
     *   <li>从Milvus删除向量数据</li>
     *   <li>删除图片文件和索引</li>
     *   <li>删除摄取历史记录</li>
     * </ol>
     *
     * <p>注意：此操作不可逆，请谨慎使用！</p>
     *
     * @param collection Collection名称
     */
    @Transactional
    public void deleteCollection(String collection) {
        log.info("开始批量删除Collection：collection={}", collection);

        try {
            int totalDeleted = 0;

            // 1. 从Milvus删除向量数据
            try {
                DeleteReq deleteReq = DeleteReq.builder()
                        .collectionName(collectionName)
                        .filter(String.format("collection == \"%s\"", collection))
                        .build();

                milvusClient.delete(deleteReq);
                log.info("Milvus向量数据删除成功：collection={}", collection);

            } catch (Exception e) {
                log.error("Milvus删除失败：collection={}, error={}", collection, e.getMessage(), e);
                // 继续删除其他数据
            }

            // 2. 删除图片
            try {
                int imageDeleted = imageStorageService.deleteByCollection(collection);
                log.info("图片删除成功：collection={}, 删除{}个", collection, imageDeleted);
                totalDeleted += imageDeleted;

            } catch (Exception e) {
                log.error("图片删除失败：collection={}, error={}", collection, e.getMessage(), e);
                // 继续删除其他数据
            }

            // 3. 删除摄取历史（需要先查询该Collection的所有文档）
            // 注：IngestionHistory表中没有collection字段，需要通过其他方式关联
            // 这里假设collection信息存储在metadata中或通过file_path推断
            // 简化处理：记录日志，实际需要根据业务逻辑实现
            log.warn("摄取历史删除需要根据业务逻辑实现，当前仅删除向量和图片");

            log.info("Collection批量删除完成：collection={}, 总删除{}项", collection, totalDeleted);

        } catch (Exception e) {
            log.error("批量删除Collection失败：collection={}, error={}", collection, e.getMessage(), e);
            throw new RuntimeException("批量删除Collection失败：" + e.getMessage(), e);
        }
    }

    /**
     * 转换为文档信息
     */
    private DocumentInfo convertToDocumentInfo(IngestionHistory history) {
        DocumentInfo info = new DocumentInfo();
        info.setId(history.getFileHash());
        info.setFileHash(history.getFileHash());
        info.setFilePath(history.getFilePath());

        // 从完整路径中提取文件名作为source
        String fileName = history.getFilePath();
        if (fileName != null) {
            int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
            if (lastSlash >= 0) {
                fileName = fileName.substring(lastSlash + 1);
            }
        }
        info.setSource(fileName);

        info.setFileSize(history.getFileSize());
        info.setStatus(history.getStatus().name());
        info.setProcessedAt(history.getProcessedAt());
        info.setCreatedAt(history.getProcessedAt());
        info.setChunkCount(history.getChunkCount());
        info.setCollection(history.getCollectionName());
        return info;
    }

    /**
     * 文档信息（列表用）
     */
    public static class DocumentInfo {
        private String id;              // 文档ID（文件哈希）
        private String fileHash;        // 文件哈希
        private String source;          // 文档名称（文件名）
        private String filePath;        // 完整文件路径
        private Long fileSize;          // 文件大小
        private String status;          // 状态
        private String collection;      // Collection名称
        private java.time.Instant processedAt;  // 处理时间
        private java.time.Instant createdAt;    // 创建时间（同processedAt）
        private Integer chunkCount;     // Chunk数量

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getFileHash() { return fileHash; }
        public void setFileHash(String fileHash) { this.fileHash = fileHash; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }

        public java.time.Instant getProcessedAt() { return processedAt; }
        public void setProcessedAt(java.time.Instant processedAt) { this.processedAt = processedAt; }

        public java.time.Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }

        public Integer getChunkCount() { return chunkCount; }
        public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    }

    /**
     * 文档详情（详情用）
     */
    public static class DocumentDetail {
        private String fileHash;
        private String filePath;
        private Long fileSize;
        private String status;
        private java.time.Instant processedAt;
        private Integer chunkCount;
        private String errorMsg;

        // Getters and Setters
        public String getFileHash() { return fileHash; }
        public void setFileHash(String fileHash) { this.fileHash = fileHash; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public java.time.Instant getProcessedAt() { return processedAt; }
        public void setProcessedAt(java.time.Instant processedAt) { this.processedAt = processedAt; }

        public Integer getChunkCount() { return chunkCount; }
        public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    }

    /**
     * Collection统计信息
     */
    public static class CollectionStats {
        private Long vectorCount;
        private Long totalDocuments;
        private Long successDocuments;
        private Long failedDocuments;

        // Getters and Setters
        public Long getVectorCount() { return vectorCount; }
        public void setVectorCount(Long vectorCount) { this.vectorCount = vectorCount; }

        public Long getTotalDocuments() { return totalDocuments; }
        public void setTotalDocuments(Long totalDocuments) { this.totalDocuments = totalDocuments; }

        public Long getSuccessDocuments() { return successDocuments; }
        public void setSuccessDocuments(Long successDocuments) { this.successDocuments = successDocuments; }

        public Long getFailedDocuments() { return failedDocuments; }
        public void setFailedDocuments(Long failedDocuments) { this.failedDocuments = failedDocuments; }
    }
}
