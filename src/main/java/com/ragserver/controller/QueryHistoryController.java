package com.ragserver.controller;

import com.ragserver.entity.QueryHistory;
import com.ragserver.repository.QueryHistoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * <p>提供查询历史记录的REST接口。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/history")
@Tag(name = "查询历史", description = "RAG 查询历史记录管理")
public class QueryHistoryController {

    private final QueryHistoryRepository queryHistoryRepository;

    public QueryHistoryController(QueryHistoryRepository queryHistoryRepository) {
        this.queryHistoryRepository = queryHistoryRepository;
    }

    /**
     * 获取最近的查询历史
     *
     * @param limit 返回数量限制（默认20）
     * @return 查询历史列表
     */
    @GetMapping
    @Operation(
        summary = "获取最近查询历史",
        description = "获取最近的 RAG 查询历史记录，按时间倒序排列"
    )
    @ApiResponse(responseCode = "200", description = "成功返回历史记录")
    public ResponseEntity<Map<String, Object>> getRecentHistory(
            @Parameter(description = "返回数量限制", example = "20")
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
     * 分页获取查询历史
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页的查询历史
     */
    @GetMapping("/page")
    @Operation(
        summary = "分页获取查询历史",
        description = "分页查询 RAG 查询历史记录，支持自定义页码和每页大小"
    )
    @ApiResponse(responseCode = "200", description = "成功返回分页数据")
    public ResponseEntity<Map<String, Object>> getHistoryPage(
            @Parameter(description = "页码（从0开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        log.info("分页获取查询历史：page={}, size={}", page, size);

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<QueryHistory> historyPage = queryHistoryRepository.findAll(pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("content", historyPage.getContent());
            response.put("totalElements", historyPage.getTotalElements());
            response.put("totalPages", historyPage.getTotalPages());
            response.put("currentPage", historyPage.getNumber());
            response.put("pageSize", historyPage.getSize());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("分页获取查询历史失败", e);
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
    @Operation(
        summary = "获取查询历史详情",
        description = "根据 ID 获取单条查询历史的详细信息"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回历史详情"),
        @ApiResponse(responseCode = "404", description = "历史记录不存在")
    })
    public ResponseEntity<Map<String, Object>> getHistoryById(
            @Parameter(description = "历史记录ID", required = true)
            @PathVariable String id) {
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
     * @param keyword 关键词（可选）
     * @param status 状态（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 搜索结果
     */
    @GetMapping("/search")
    @Operation(
        summary = "搜索查询历史",
        description = "支持按关键词、状态、时间范围搜索查询历史记录"
    )
    @ApiResponse(responseCode = "200", description = "成功返回搜索结果")
    public ResponseEntity<Map<String, Object>> searchHistory(
            @Parameter(description = "搜索关键词", example = "RAG")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "查询状态", example = "SUCCESS")
            @RequestParam(required = false) String status,
            @Parameter(description = "开始时间", example = "2026-10-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间", example = "2026-10-31T23:59:59")
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
     * @return 统计信息
     */
    @GetMapping("/stats")
    @Operation(
        summary = "获取查询历史统计",
        description = "获取查询历史的统计信息，包括总数、成功率、平均响应时间等"
    )
    @ApiResponse(responseCode = "200", description = "成功返回统计信息")
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
     * @return 高频问题列表
     */
    @GetMapping("/frequent")
    @Operation(
        summary = "获取高频问题",
        description = "获取最常被问到的问题列表，按查询次数排序"
    )
    @ApiResponse(responseCode = "200", description = "成功返回高频问题")
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
