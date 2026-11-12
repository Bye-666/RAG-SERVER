package com.ragserver.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 查询历史记录实体
 *
 * <p>存储用户的RAG查询历史，用于：</p>
 * <ul>
 *   <li>查询日志：记录所有查询请求</li>
 *   <li>使用分析：统计高频问题</li>
 *   <li>质量监控：跟踪响应时间</li>
 *   <li>用户体验：提供历史查询记录</li>
 * </ul>
 *
 * <h3>数据库表结构</h3>
 * <pre>
 * CREATE TABLE query_history (
 *   query_id VARCHAR(64) PRIMARY KEY,
 *   query_text TEXT NOT NULL,
 *   question VARCHAR(1000) NOT NULL,
 *   answer TEXT,
 *   collection_name VARCHAR(128),
 *   top_k INT DEFAULT 10,
 *   enable_rerank BOOLEAN DEFAULT FALSE,
 *   result_count INT,
 *   retrieved_docs_count INT,
 *   response_time_ms BIGINT,
 *   user_id VARCHAR(128),
 *   session_id VARCHAR(128),
 *   query_type VARCHAR(50),
 *   status VARCHAR(50) NOT NULL,
 *   error_message TEXT,
 *   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *   INDEX idx_collection (collection_name),
 *   INDEX idx_user (user_id),
 *   INDEX idx_created_at (created_at),
 *   INDEX idx_status (status)
 * );
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "query_history", indexes = {
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_collection", columnList = "collection_name")
})
public class QueryHistory {

    /**
     * 主键ID（UUID）
     */
    @Id
    @Column(name = "query_id", length = 64)
    private String id;

    /**
     * 查询文本（数据库字段）
     */
    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    /**
     * 用户问题
     */
    @Column(nullable = false, length = 1000)
    private String question;

    /**
     * 生成的答案
     */
    @Column(columnDefinition = "TEXT")
    private String answer;

    /**
     * 检索文档数量
     */
    @Column(name = "top_k")
    private Integer topK;

    /**
     * 是否启用重排序
     */
    @Column(name = "enable_rerank")
    private Boolean enableRerank;

    /**
     * Collection名称
     */
    @Column(name = "collection_name")
    private String collectionName;

    /**
     * 响应时间（毫秒）
     */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    /**
     * 查询时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 查询状态
     *
     * <p>可能的值：</p>
     * <ul>
     *   <li>SUCCESS - 查询成功</li>
     *   <li>FAILED - 查询失败</li>
     *   <li>NO_RESULTS - 无检索结果</li>
     * </ul>
     */
    @Column(nullable = false, length = 50)
    private String status;

    /**
     * 错误消息（如果失败）
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 检索到的文档数量
     */
    @Column(name = "retrieved_docs_count")
    private Integer retrievedDocsCount;

    /**
     * 实际返回的结果数量
     */
    @Column(name = "result_count")
    private Integer resultCount;

    /**
     * 用户ID
     */
    @Column(name = "user_id", length = 128)
    private String userId;

    /**
     * 会话ID
     */
    @Column(name = "session_id", length = 128)
    private String sessionId;

    /**
     * 查询类型
     *
     * <p>可能的值：</p>
     * <ul>
     *   <li>STANDARD - 标准查询</li>
     *   <li>STREAMING - 流式查询</li>
     * </ul>
     */
    @Column(name = "query_type", length = 50)
    private String queryType;

    /**
     * 创建查询历史记录
     *
     * @param question 用户问题
     * @param answer 生成的答案
     * @param topK 检索文档数量
     * @param enableRerank 是否启用重排序
     * @param responseTimeMs 响应时间
     * @param status 查询状态
     * @return 查询历史实例
     */
    public static QueryHistory create(String question, String answer, Integer topK,
                                     Boolean enableRerank, Long responseTimeMs, String status) {
        QueryHistory history = new QueryHistory();
        history.id = java.util.UUID.randomUUID().toString();
        history.question = question;
        history.queryText = question; // 同时设置 query_text 字段
        history.answer = answer;
        history.topK = topK;
        history.enableRerank = enableRerank;
        history.responseTimeMs = responseTimeMs;
        history.status = status;
        history.createdAt = LocalDateTime.now();
        history.queryType = "STANDARD";
        return history;
    }

    /**
     * 创建失败的查询记录
     *
     * @param question 用户问题
     * @param errorMessage 错误消息
     * @return 查询历史实例
     */
    public static QueryHistory createFailed(String question, String errorMessage) {
        QueryHistory history = new QueryHistory();
        history.id = java.util.UUID.randomUUID().toString();
        history.question = question;
        history.queryText = question; // 同时设置 query_text 字段
        history.status = "FAILED";
        history.errorMessage = errorMessage;
        history.createdAt = LocalDateTime.now();
        history.queryType = "STANDARD";
        return history;
    }
}
