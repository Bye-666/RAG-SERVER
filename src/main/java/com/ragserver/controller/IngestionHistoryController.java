package com.ragserver.controller;

import com.ragserver.entity.IngestionHistory;
import com.ragserver.repository.IngestionHistoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 文档摄取历史控制器
 *
 * <p>提供文档上传历史的查询接口。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/ingestion-history")
@Tag(name = "上传历史", description = "文档摄取历史记录查询")
public class IngestionHistoryController {

    private final IngestionHistoryRepository ingestionHistoryRepository;

    public IngestionHistoryController(IngestionHistoryRepository ingestionHistoryRepository) {
        this.ingestionHistoryRepository = ingestionHistoryRepository;
    }

    /**
     * 获取文档上传历史
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页的文档上传历史
     */
    @GetMapping
    @Operation(
        summary = "获取文档上传历史",
        description = "分页查询文档摄取历史记录，按处理时间倒序排列"
    )
    @ApiResponse(responseCode = "200", description = "成功返回历史记录")
    public ResponseEntity<Map<String, Object>> getIngestionHistory(
            @Parameter(description = "页码（从0开始）", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        log.info("获取文档上传历史：page={}, size={}", page, size);

        try {
            // 按处理时间倒序排列
            Pageable pageable = PageRequest.of(page, size, Sort.by("processedAt").descending());
            log.debug("创建Pageable: {}", pageable);

            Page<IngestionHistory> historyPage = ingestionHistoryRepository.findAll(pageable);
            log.debug("查询结果：totalElements={}, totalPages={}", historyPage.getTotalElements(), historyPage.getTotalPages());

            Map<String, Object> response = new HashMap<>();
            response.put("content", historyPage.getContent());
            response.put("totalElements", historyPage.getTotalElements());
            response.put("totalPages", historyPage.getTotalPages());
            response.put("currentPage", historyPage.getNumber());
            response.put("pageSize", historyPage.getSize());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取文档上传历史失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取历史记录失败：" + e.getMessage());
            errorResponse.put("errorType", e.getClass().getName());
            errorResponse.put("page", page);
            errorResponse.put("size", size);
            return ResponseEntity.internalServerError()
                    .body(errorResponse);
        }
    }

    /**
     * 获取文档上传统计信息
     *
     * @return 文档上传统计信息
     */
    @GetMapping("/stats")
    @Operation(
        summary = "获取文档上传统计",
        description = "获取文档摄取的统计信息，包括总数、成功数、失败数等"
    )
    @ApiResponse(responseCode = "200", description = "成功返回统计信息")
    public ResponseEntity<Map<String, Object>> getIngestionStats() {
        log.info("获取文档上传统计");

        try {
            long total = ingestionHistoryRepository.count();
            long success = ingestionHistoryRepository.countByStatus(IngestionHistory.IngestionStatus.SUCCESS);
            long failed = ingestionHistoryRepository.countByStatus(IngestionHistory.IngestionStatus.FAILED);
            long processing = ingestionHistoryRepository.countByStatus(IngestionHistory.IngestionStatus.PROCESSING);

            Map<String, Object> stats = new HashMap<>();
            stats.put("total", total);
            stats.put("success", success);
            stats.put("failed", failed);
            stats.put("processing", processing);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("获取文档上传统计失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取统计信息失败：" + e.getMessage()));
        }
    }
}
