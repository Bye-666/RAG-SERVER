package com.ragserver.repository;

import com.ragserver.entity.QueryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询历史Repository
 *
 * <p>提供查询历史的数据访问接口：</p>
 * <ul>
 *   <li>基本CRUD操作</li>
 *   <li>时间范围查询</li>
 *   <li>状态过滤</li>
 *   <li>统计分析</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 查询最近的查询历史
 * List<QueryHistory> recent = repository.findTop10ByOrderByCreatedAtDesc();
 *
 * // 查询成功的记录
 * List<QueryHistory> success = repository.findByStatus("SUCCESS");
 *
 * // 统计总查询数
 * long total = repository.count();
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Repository
public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {

    /**
     * 查询最近的N条记录
     *
     * <p>按创建时间倒序排列，返回最新的查询历史。</p>
     *
     * @return 最近10条查询历史
     */
    List<QueryHistory> findTop10ByOrderByCreatedAtDesc();

    /**
     * 查询最近的N条记录（自定义数量）
     *
     * @return 最近N条查询历史
     */
    List<QueryHistory> findTop20ByOrderByCreatedAtDesc();

    /**
     * 按状态查询
     *
     * <p>支持的状态：SUCCESS、FAILED、NO_RESULTS。</p>
     *
     * @param status 查询状态
     * @return 指定状态的查询历史列表
     */
    List<QueryHistory> findByStatus(String status);

    /**
     * 按状态查询（带排序）
     *
     * @param status 查询状态
     * @return 指定状态的查询历史列表（按时间倒序）
     */
    List<QueryHistory> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * 时间范围查询
     *
     * <p>查询指定时间范围内的所有查询历史。</p>
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 时间范围内的查询历史列表
     */
    List<QueryHistory> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 时间范围查询（带排序）
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 时间范围内的查询历史列表（按时间倒序）
     */
    List<QueryHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按Collection查询
     *
     * @param collectionName Collection名称
     * @return 指定Collection的查询历史
     */
    List<QueryHistory> findByCollectionName(String collectionName);

    /**
     * 按查询类型查询
     *
     * @param queryType 查询类型（STANDARD或STREAMING）
     * @return 指定类型的查询历史
     */
    List<QueryHistory> findByQueryType(String queryType);

    /**
     * 统计指定状态的查询数量
     *
     * @param status 查询状态
     * @return 查询数量
     */
    long countByStatus(String status);

    /**
     * 统计时间范围内的查询数量
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 查询数量
     */
    long countByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 计算平均响应时间
     *
     * <p>只计算成功查询的平均响应时间。</p>
     *
     * @return 平均响应时间（毫秒）
     */
    @Query("SELECT AVG(q.responseTimeMs) FROM QueryHistory q WHERE q.status = 'SUCCESS'")
    Double getAverageResponseTime();

    /**
     * 获取最慢的查询
     *
     * @return 响应时间最长的10条查询
     */
    List<QueryHistory> findTop10ByOrderByResponseTimeMsDesc();

    /**
     * 查询高频问题
     *
     * <p>统计出现次数最多的问题。</p>
     *
     * @return 问题及其出现次数
     */
    @Query("SELECT q.question, COUNT(q) as cnt FROM QueryHistory q GROUP BY q.question ORDER BY cnt DESC")
    List<Object[]> findFrequentQuestions();

    /**
     * 删除指定时间之前的历史记录
     *
     * <p>用于定期清理旧数据。</p>
     *
     * @param before 截止时间
     */
    void deleteByCreatedAtBefore(LocalDateTime before);

    /**
     * 按问题模糊搜索
     *
     * @param keyword 关键词
     * @return 包含关键词的查询历史
     */
    List<QueryHistory> findByQuestionContainingOrderByCreatedAtDesc(String keyword);
}
