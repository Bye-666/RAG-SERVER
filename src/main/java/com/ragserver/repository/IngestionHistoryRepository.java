package com.ragserver.repository;

import com.ragserver.entity.IngestionHistory;
import com.ragserver.entity.IngestionHistory.IngestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文档摄取历史Repository
 *
 * <p>提供IngestionHistory实体的数据访问接口，继承Spring Data JPA的JpaRepository。</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>检查文档是否已摄取：避免重复处理</li>
 *   <li>查询摄取状态：追踪处理进度</li>
 *   <li>统计摄取数据：分析系统使用情况</li>
 *   <li>错误诊断：查询失败记录</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Service
 * public class IngestionService {
 *     @Autowired
 *     private IngestionHistoryRepository repository;
 *
 *     public void ingestDocument(File file) {
 *         String fileHash = calculateHash(file);
 *
 *         // 检查是否已摄取
 *         Optional<IngestionHistory> existing = repository.findById(fileHash);
 *         if (existing.isPresent() && existing.get().getStatus() == SUCCESS) {
 *             log.info("文档已摄取，跳过: {}", fileHash);
 *             return;
 *         }
 *
 *         // 创建PROCESSING记录
 *         IngestionHistory history = new IngestionHistory();
 *         history.setFileHash(fileHash);
 *         history.setFilePath(file.getName());
 *         history.setStatus(PROCESSING);
 *         repository.save(history);
 *
 *         try {
 *             // 执行摄取...
 *             int chunkCount = doIngest(file);
 *
 *             // 更新为SUCCESS
 *             history.setStatus(SUCCESS);
 *             history.setChunkCount(chunkCount);
 *             history.setProcessedAt(Instant.now());
 *             repository.save(history);
 *         } catch (Exception e) {
 *             // 更新为FAILED
 *             history.setStatus(FAILED);
 *             history.setErrorMsg(e.getMessage());
 *             history.setProcessedAt(Instant.now());
 *             repository.save(history);
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Repository
public interface IngestionHistoryRepository extends JpaRepository<IngestionHistory, String> {

    /**
     * 根据状态查询摄取记录
     *
     * <p>查询所有指定状态的摄取记录，按处理时间倒序排列。</p>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>查询所有成功摄取的文档</li>
     *   <li>查询所有失败的记录用于重试</li>
     *   <li>监控正在处理的文档数量</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 查询所有失败的记录
     * List<IngestionHistory> failed = repository.findByStatus(FAILED);
     * for (IngestionHistory h : failed) {
     *     log.error("摄取失败: {} - {}", h.getFilePath(), h.getErrorMsg());
     * }
     * }</pre>
     *
     * @param status 摄取状态（PROCESSING/SUCCESS/FAILED）
     * @return 匹配状态的摄取记录列表，按处理时间倒序
     */
    List<IngestionHistory> findByStatusOrderByProcessedAtDesc(IngestionStatus status);

    /**
     * 根据文件路径模糊查询
     *
     * <p>支持模糊匹配文件路径，用于搜索功能。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 查询所有PDF文件
     * List<IngestionHistory> pdfs = repository.findByFilePathContaining(".pdf");
     *
     * // 查询包含"产品"的文档
     * List<IngestionHistory> products = repository.findByFilePathContaining("产品");
     * }</pre>
     *
     * @param keyword 关键词（支持模糊匹配）
     * @return 匹配的摄取记录列表
     */
    List<IngestionHistory> findByFilePathContaining(String keyword);

    /**
     * 查询指定时间范围内处理的记录
     *
     * <p>用于统计和报表功能。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 查询今天处理的文档
     * Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
     * Instant now = Instant.now();
     * List<IngestionHistory> today = repository.findByProcessedAtBetween(startOfDay, now);
     * log.info("今天处理了{}个文档", today.size());
     * }</pre>
     *
     * @param start 开始时间（包含）
     * @param end 结束时间（包含）
     * @return 时间范围内的摄取记录列表
     */
    List<IngestionHistory> findByProcessedAtBetween(Instant start, Instant end);

    /**
     * 统计指定状态的记录数量
     *
     * <p>用于Dashboard和监控。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * long successCount = repository.countByStatus(SUCCESS);
     * long failedCount = repository.countByStatus(FAILED);
     * long processingCount = repository.countByStatus(PROCESSING);
     *
     * log.info("成功: {}, 失败: {}, 处理中: {}", successCount, failedCount, processingCount);
     * }</pre>
     *
     * @param status 摄取状态
     * @return 该状态的记录数量
     */
    long countByStatus(IngestionStatus status);

    /**
     * 查询所有成功摄取的记录
     *
     * <p>快捷方法，等同于findByStatus(SUCCESS)。</p>
     *
     * @return 所有成功摄取的记录列表
     */
    default List<IngestionHistory> findAllSuccessful() {
        return findByStatusOrderByProcessedAtDesc(IngestionStatus.SUCCESS);
    }

    /**
     * 查询所有失败的记录
     *
     * <p>快捷方法，等同于findByStatus(FAILED)。</p>
     *
     * @return 所有失败的记录列表
     */
    default List<IngestionHistory> findAllFailed() {
        return findByStatusOrderByProcessedAtDesc(IngestionStatus.FAILED);
    }

    /**
     * 统计总的chunk数量
     *
     * <p>统计所有成功摄取的文档生成的chunk总数。</p>
     *
     * <p>用于Dashboard显示系统规模。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * Long totalChunks = repository.sumChunkCountByStatus(SUCCESS);
     * log.info("系统中共有{}个chunk", totalChunks);
     * }</pre>
     *
     * @param status 摄取状态（通常为SUCCESS）
     * @return chunk总数（如果没有记录则返回0）
     */
    @Query("SELECT COALESCE(SUM(h.chunkCount), 0) FROM IngestionHistory h WHERE h.status = :status")
    Long sumChunkCountByStatus(IngestionStatus status);

    /**
     * 查询最近处理的N条记录
     *
     * <p>用于Dashboard显示最近活动。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 查询最近10条记录
     * List<IngestionHistory> recent = repository.findTop10ByOrderByProcessedAtDesc();
     * }</pre>
     *
     * @return 最近10条记录
     */
    List<IngestionHistory> findTop10ByOrderByProcessedAtDesc();

    /**
     * 删除指定状态的所有记录
     *
     * <p>用于数据清理。</p>
     *
     * <p>⚠️ 警告：此操作不可逆，请谨慎使用！</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 清理所有失败记录
     * long deleted = repository.deleteByStatus(FAILED);
     * log.info("已清理{}条失败记录", deleted);
     * }</pre>
     *
     * @param status 要删除的记录状态
     * @return 删除的记录数量
     */
    long deleteByStatus(IngestionStatus status);

    /**
     * 按Collection分组统计
     *
     * <p>返回每个Collection的文档数和chunk数统计。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 获取所有Collection的统计信息
     * List<Object[]> stats = repository.groupByCollection();
     * for (Object[] row : stats) {
     *     String collection = (String) row[0];
     *     Long docCount = (Long) row[1];
     *     Long chunkCount = (Long) row[2];
     *     log.info("Collection: {}, 文档数: {}, Chunk数: {}", collection, docCount, chunkCount);
     * }
     * }</pre>
     *
     * @return 统计结果列表，每行包含：[collectionName, documentCount, totalChunkCount]
     */
    @Query("SELECT COALESCE(h.collectionName, 'default'), COUNT(h), COALESCE(SUM(h.chunkCount), 0) " +
           "FROM IngestionHistory h " +
           "WHERE h.status = 'SUCCESS' " +
           "GROUP BY h.collectionName")
    List<Object[]> groupByCollection();
}
