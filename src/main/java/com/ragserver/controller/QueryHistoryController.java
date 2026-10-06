package com.ragserver.controller;

import com.ragserver.entity.QueryHistory;
import com.ragserver.repository.QueryHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询历史Controller
 *
 * <p>提供查询历史记录的REST接口：</p>
 * <ul>
 *   <li>查询历史列表</li>
 *   <li>统计分析</li>
 *   <li>搜索过滤</li>
 * </ul>
 *
 * <h3>API端点</h3>
 * <pre>
 * GET  /api/history              - 获取最近查询历史
 * GET  /api/history/{id}         - 获取单条历史详情
 * GET  /api/history/search       - 搜索查询历史
 * GET  /api/history/stats        - 获取统计信息
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/history")
public class QueryHistoryController {

    private final QueryHistoryRepository queryHistoryRepository;

    public QueryHistoryController(QueryHistoryRepository queryHistoryRepository) {
        this.queryHistoryRepository = queryHistoryRepository;
    }

    /**
     * 获取最近的查询历史
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "histories": [
     *     {
     *       "id": 1,
     *       "question": "什么是RAG？",
     *       "answer": "RAG是检索增强生成...",
     *       "topK": 10,
     *       "enableRerank": false,
     *       "responseTimeMs": 1500,
     *       "status": "SUCCESS",
     *       "createdAt": "2026-10-06T10:00:00"
     *     }
     *   ],
     *   "total": 1
     * }
     * }</pre>
     *
     * @param limit 返回数量限制（默认20）
     * @return 查询历史列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRecentHistory(
            @RequestParam(defaultValue = "20") int limit) {

        log.info("获取最近查询历史，limit={}", limit);

        try {
            List<QueryHistory> histories = limit <= 10
                ? queryHistoryRepository.findTop10ByOrderByCreatedAtDesc()
                : queryHistoryRepository.findTop20ByOrderByCreatedAtDesc();

            return ResponseEntity.ok(Map.of(
                "histories", histories,
                "total", histories.size()
            ));

        } catch (Exception e) {
            log.error("获取查询历史失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "获取查询历史失败：" + e.getMessage()));
        }
    }

    /**
     * 获取单条历史详情
     *
     * @param id 历史记录ID
     * @return 历史详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getHistoryById(@PathVariable Long id) {
        log.info("获取查询历史详情，id={}", id);

        try {
            return queryHistoryRepository.findById(id)
                .map(history -> ResponseEntity.ok(Map.of("history", (Object) history)))
                .orElse(ResponseEntity.notFound().build());

        } catch (Exception e) {
            log.error("获取历史详情失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "获取历史详情失败：" + e.getMessage()));
        }
    }

    /**
     * 搜索查询历史
     *
     * <p>支持多种搜索条件：</p>
     * <ul>
     *   <li>关键词搜索</li>
     *   <li>状态过滤</li>
     *   <li>时间范围</li>
     * </ul>
     *
     * <h3>请求示例</h3>
     * <pre>
     * GET /api/history/search?keyword=RAG&status=SUCCESS
     * GET /api/history/search?startTime=2026-10-01T00:00:00&endTime=2026-10-31T23:59:59
     * </pre>
     *
     * @param keyword 关键词（可选）
     * @param status 状态（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 搜索结果
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchHistory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        log.info("搜索查询历史：keyword={}, status={}, startTime={}, endTime={}",
            keyword, status, startTime, endTime);

        try {
            List<QueryHistory> results;

            if (keyword != null && !keyword.trim().isEmpty()) {
                // 关键词搜索
                results = queryHistoryRepository.findByQuestionContainingOrderByCreatedAtDesc(keyword);
            } else if (status != null && !status.trim().isEmpty()) {
                // 按状态过滤
                results = queryHistoryRepository.findByStatusOrderByCreatedAtDesc(status);
            } else if (startTime != null && endTime != null) {
                // 时间范围查询
                results = queryHistoryRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startTime, endTime);
            } else {
                // 默认返回最近记录
                results = queryHistoryRepository.findTop20ByOrderByCreatedAtDesc();
            }

            return ResponseEntity.ok(Map.of(
                "results", results,
                "total", results.size()
            ));

        } catch (Exception e) {
            log.error("搜索查询历史失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "搜索失败：" + e.getMessage()));
        }
    }

    /**
     * 获取统计信息
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "totalQueries": 100,
     *   "successQueries": 85,
     *   "failedQueries": 10,
     *   "noResultsQueries": 5,
     *   "averageResponseTime": 1500.5,
     *   "slowestQueries": [...]
     * }
     * }</pre>
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.info("获取查询历史统计信息");

        try {
            long totalQueries = queryHistoryRepository.count();
            long successQueries = queryHistoryRepository.countByStatus("SUCCESS");
            long failedQueries = queryHistoryRepository.countByStatus("FAILED");
            long noResultsQueries = queryHistoryRepository.countByStatus("NO_RESULTS");
            Double averageResponseTime = queryHistoryRepository.getAverageResponseTime();
            List<QueryHistory> slowestQueries = queryHistoryRepository.findTop10ByOrderByResponseTimeMsDesc();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalQueries", totalQueries);
            stats.put("successQueries", successQueries);
            stats.put("failedQueries", failedQueries);
            stats.put("noResultsQueries", noResultsQueries);
            stats.put("averageResponseTime", averageResponseTime != null ? averageResponseTime : 0);
            stats.put("slowestQueries", slowestQueries);
            stats.put("successRate", totalQueries > 0 ? (double) successQueries / totalQueries * 100 : 0);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "获取统计信息失败：" + e.getMessage()));
        }
    }

    /**
     * 获取高频问题
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "frequentQuestions": [
     *     {
     *       "question": "什么是RAG？",
     *       "count": 50
     *     }
     *   ]
     * }
     * }</pre>
     *
     * @return 高频问题列表
     */
    @GetMapping("/frequent")
    public ResponseEntity<Map<String, Object>> getFrequentQuestions() {
        log.info("获取高频问题");

        try {
            List<Object[]> frequentQuestions = queryHistoryRepository.findFrequentQuestions();

            // 转换为友好格式
            List<Map<String, Object>> formattedQuestions = frequentQuestions.stream()
                .limit(10) // 只返回前10个
                .map(row -> Map.of(
                    "question", (Object) row[0],
                    "count", row[1]
                ))
                .toList();

            return ResponseEntity.ok(Map.of("frequentQuestions", formattedQuestions));

        } catch (Exception e) {
            log.error("获取高频问题失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "获取高频问题失败：" + e.getMessage()));
        }
    }
}
