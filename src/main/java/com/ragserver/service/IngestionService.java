package com.ragserver.service;

import com.ragserver.entity.IngestionHistory;
import com.ragserver.ingestion.IngestionPipeline;
import com.ragserver.ingestion.loader.FileIntegrityService;
import com.ragserver.repository.IngestionHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文档摄取服务
 *
 * <p>提供文档摄取的业务逻辑，包括：</p>
 * <ul>
 *   <li>单文件和批量摄取</li>
 *   <li>进度跟踪</li>
 *   <li>错误处理</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private IngestionService ingestionService;
 *
 * // 摄取单个文件
 * IngestionResult result = ingestionService.ingestDocument(
 *     Path.of("/docs/report.pdf")
 * );
 *
 * // 批量摄取
 * List<Path> files = List.of(
 *     Path.of("/docs/doc1.pdf"),
 *     Path.of("/docs/doc2.pdf")
 * );
 * BatchIngestionResult batchResult = ingestionService.ingestDocuments(files);
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class IngestionService {

    private final IngestionPipeline pipeline;
    private final IngestionHistoryRepository historyRepository;
    private final FileIntegrityService fileIntegrityService;

    /**
     * 构造函数
     */
    public IngestionService(IngestionPipeline pipeline,
                           IngestionHistoryRepository historyRepository,
                           FileIntegrityService fileIntegrityService) {
        this.pipeline = pipeline;
        this.historyRepository = historyRepository;
        this.fileIntegrityService = fileIntegrityService;
        log.info("IngestionService初始化完成");
    }

    /**
     * 批量摄取文档
     *
     * @param filePaths 文件路径列表
     * @return 批量摄取结果
     */
    public BatchIngestionResult ingestDocuments(List<Path> filePaths) {
        return ingestDocuments(filePaths, null);
    }

    /**
     * 批量摄取文档（支持指定Collection）
     *
     * @param filePaths 文件路径列表
     * @param collectionName Collection名称（可选）
     * @return 批量摄取结果
     */
    public BatchIngestionResult ingestDocuments(List<Path> filePaths, String collectionName) {
        log.info("开始批量摄取: {}个文件, collection={}", filePaths.size(), collectionName);

        BatchIngestionResult batchResult = new BatchIngestionResult();
        batchResult.setTotalFiles(filePaths.size());

        for (Path filePath : filePaths) {
            try {
                IngestionPipeline.IngestionResult result = ingestDocumentInternal(filePath, collectionName);
                batchResult.addSuccess(result);
            } catch (IngestionPipeline.IngestionException e) {
                // 保留异常对象，以便后续获取 ErrorType
                batchResult.addFailure(filePath.toString(), e.getMessage(), e);
            } catch (Exception e) {
                batchResult.addFailure(filePath.toString(), e.getMessage(), null);
            }
        }

        log.info("批量摄取完成: 成功{}/失败{}/总计{}",
            batchResult.getSuccessCount(), batchResult.getFailureCount(), filePaths.size());

        return batchResult;
    }

    /**
     * 摄取单个文档（内部方法，支持指定Collection）
     */
    private IngestionPipeline.IngestionResult ingestDocumentInternal(Path filePath, String collectionName)
            throws IngestionPipeline.IngestionException {
        try {
            // 1. 计算文件哈希
            String fileHash = fileIntegrityService.computeFileHash(filePath);

            // 2. 检查文件是否已存在（去重）
            Optional<IngestionHistory> existing = historyRepository.findByFileHash(fileHash);
            if (existing.isPresent() && existing.get().getStatus() == IngestionHistory.IngestionStatus.SUCCESS) {
                String existingFile = existing.get().getFileName();
                log.info("文件已存在，跳过摄取: {} (与已存在文件 {} 内容相同, hash: {})",
                    filePath.getFileName(), existingFile, fileHash);
                throw new IngestionPipeline.IngestionException(
                    String.format("文件已存在：与 %s 内容相同，无需重复上传", existingFile),
                    IngestionPipeline.IngestionException.ErrorType.DUPLICATE_FILE
                );
            }

            // 3. 执行摄取
            IngestionPipeline.IngestionResult result = pipeline.ingest(filePath);

            // 4. 保存成功的摄取历史（传入已计算的哈希）
            saveIngestionHistory(filePath, result, collectionName, fileHash);

            return result;
        } catch (IngestionPipeline.IngestionException e) {
            saveFailedIngestionHistory(filePath, e, collectionName);
            // 直接抛出原始异常，保留 ErrorType 信息
            throw e;
        } catch (Exception e) {
            log.error("摄取文档时发生错误: {}", filePath, e);
            throw new RuntimeException("摄取失败: " + filePath, e);
        }
    }

    /**
     * 保存成功的摄取历史记录
     */
    private void saveIngestionHistory(Path filePath, IngestionPipeline.IngestionResult result, String collectionName, String fileHash) {
        try {
            IngestionHistory history = new IngestionHistory();
            history.setFilePath(filePath.toString());
            history.setFileName(filePath.getFileName().toString());
            history.setFileHash(fileHash);
            history.setFileSize(filePath.toFile().length());
            history.setChunkCount(result.getChunksProcessed());
            history.setStatus(IngestionHistory.IngestionStatus.SUCCESS);
            history.setProcessedAt(Instant.now());
            history.setCollectionName(collectionName);

            historyRepository.save(history);
            log.debug("已保存摄取历史记录: {}", history.getFileHash());
        } catch (Exception e) {
            log.error("保存摄取历史失败: {}", filePath, e);
        }
    }

    /**
     * 保存失败的摄取历史记录
     */
    private void saveFailedIngestionHistory(Path filePath, IngestionPipeline.IngestionException e, String collectionName) {
        try {
            IngestionHistory history = new IngestionHistory();
            history.setFilePath(filePath.toString());
            history.setFileName(filePath.getFileName().toString());
            history.setFileHash("FAILED_" + System.currentTimeMillis());
            history.setFileSize(filePath.toFile().length());
            history.setChunkCount(0);
            history.setStatus(IngestionHistory.IngestionStatus.FAILED);
            history.setProcessedAt(Instant.now());
            history.setErrorMsg(e.getMessage());
            history.setCollectionName(collectionName);

            historyRepository.save(history);
            log.debug("已保存失败的摄取历史记录: {}", filePath);
        } catch (Exception ex) {
            log.error("保存失败历史记录失败: {}", filePath, ex);
        }
    }

    /**
     * 批量摄取结果
     */
    public static class BatchIngestionResult {
        private int totalFiles;
        private final List<IngestionPipeline.IngestionResult> successes = new ArrayList<>();
        private final List<FailureInfo> failures = new ArrayList<>();

        public void setTotalFiles(int totalFiles) {
            this.totalFiles = totalFiles;
        }

        public void addSuccess(IngestionPipeline.IngestionResult result) {
            successes.add(result);
        }

        public void addFailure(String filePath, String errorMessage) {
            failures.add(new FailureInfo(filePath, errorMessage));
        }

        public void addFailure(String filePath, String errorMessage, IngestionPipeline.IngestionException exception) {
            failures.add(new FailureInfo(filePath, errorMessage, exception));
        }

        public int getTotalFiles() { return totalFiles; }
        public int getSuccessCount() { return successes.size(); }
        public int getFailureCount() { return failures.size(); }
        public List<IngestionPipeline.IngestionResult> getSuccesses() { return successes; }
        public List<FailureInfo> getFailures() { return failures; }

        @Override
        public String toString() {
            return String.format("BatchIngestionResult{total=%d, success=%d, failure=%d}",
                totalFiles, getSuccessCount(), getFailureCount());
        }
    }

    /**
     * 失败信息
     */
    public static class FailureInfo {
        private final String filePath;
        private final String errorMessage;
        private final IngestionPipeline.IngestionException exception;

        public FailureInfo(String filePath, String errorMessage) {
            this(filePath, errorMessage, null);
        }

        public FailureInfo(String filePath, String errorMessage, IngestionPipeline.IngestionException exception) {
            this.filePath = filePath;
            this.errorMessage = errorMessage;
            this.exception = exception;
        }

        public String getFilePath() { return filePath; }
        public String getErrorMessage() { return errorMessage; }
        public IngestionPipeline.IngestionException getException() { return exception; }

        @Override
        public String toString() {
            return String.format("FailureInfo{file='%s', error='%s'}", filePath, errorMessage);
        }
    }
}
