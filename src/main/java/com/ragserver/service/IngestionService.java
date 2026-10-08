package com.ragserver.service;

import com.ragserver.entity.IngestionHistory;
import com.ragserver.ingestion.IngestionPipeline;
import com.ragserver.repository.IngestionHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * 构造函数
     */
    public IngestionService(IngestionPipeline pipeline,
                           IngestionHistoryRepository historyRepository) {
        this.pipeline = pipeline;
        this.historyRepository = historyRepository;
        log.info("IngestionService初始化完成");
    }

    /**
     * 摄取单个文档
     *
     * <p>摄取完成后自动保存历史记录并清除统计数据缓存。</p>
     *
     * @param filePath 文件路径
     * @return 摄取结果
     */
    @CacheEvict(value = {"stats", "documents"}, allEntries = true)
    public IngestionPipeline.IngestionResult ingestDocument(Path filePath) {
        log.info("开始摄取文档: {}", filePath);

        try {
            IngestionPipeline.IngestionResult result = pipeline.ingest(filePath);

            // 保存摄取历史记录
            saveIngestionHistory(filePath, result);

            log.info("文档摄取成功: {}", filePath);
            return result;
        } catch (IngestionPipeline.IngestionException e) {
            // 保存失败记录
            saveFailedIngestionHistory(filePath, e);

            log.error("文档摄取失败: {}", filePath, e);
            throw new RuntimeException("摄取失败: " + filePath, e);
        }
    }

    /**
     * 批量摄取文档
     *
     * @param filePaths 文件路径列表
     * @return 批量摄取结果
     */
    public BatchIngestionResult ingestDocuments(List<Path> filePaths) {
        log.info("开始批量摄取: {}个文件", filePaths.size());

        BatchIngestionResult batchResult = new BatchIngestionResult();
        batchResult.setTotalFiles(filePaths.size());

        for (Path filePath : filePaths) {
            try {
                IngestionPipeline.IngestionResult result = ingestDocument(filePath);
                batchResult.addSuccess(result);
            } catch (Exception e) {
                batchResult.addFailure(filePath.toString(), e.getMessage());
            }
        }

        log.info("批量摄取完成: 成功{}/失败{}/总计{}",
            batchResult.getSuccessCount(), batchResult.getFailureCount(), filePaths.size());

        return batchResult;
    }

    /**
     * 保存成功的摄取历史记录
     */
    private void saveIngestionHistory(Path filePath, IngestionPipeline.IngestionResult result) {
        try {
            IngestionHistory history = new IngestionHistory();
            history.setFilePath(filePath.toString());

            // 从元数据获取file_hash并转换为String
            Object fileHashObj = result.getSourceDocument().getMetadata().get("file_hash");
            String fileHash = fileHashObj != null ? fileHashObj.toString() : "UNKNOWN_" + System.currentTimeMillis();
            history.setFileHash(fileHash);

            history.setFileSize(filePath.toFile().length());
            history.setChunkCount(result.getChunksProcessed());
            history.setStatus(IngestionHistory.IngestionStatus.SUCCESS);
            history.setProcessedAt(Instant.now());

            historyRepository.save(history);
            log.debug("已保存摄取历史记录: {}", history.getFileHash());
        } catch (Exception e) {
            log.error("保存摄取历史失败: {}", filePath, e);
        }
    }

    /**
     * 保存失败的摄取历史记录
     */
    private void saveFailedIngestionHistory(Path filePath, IngestionPipeline.IngestionException e) {
        try {
            IngestionHistory history = new IngestionHistory();
            history.setFilePath(filePath.toString());
            history.setFileHash("FAILED_" + System.currentTimeMillis());
            history.setFileSize(filePath.toFile().length());
            history.setChunkCount(0);
            history.setStatus(IngestionHistory.IngestionStatus.FAILED);
            history.setProcessedAt(Instant.now());
            history.setErrorMsg(e.getMessage());

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

        public FailureInfo(String filePath, String errorMessage) {
            this.filePath = filePath;
            this.errorMessage = errorMessage;
        }

        public String getFilePath() { return filePath; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return String.format("FailureInfo{file='%s', error='%s'}", filePath, errorMessage);
        }
    }
}
