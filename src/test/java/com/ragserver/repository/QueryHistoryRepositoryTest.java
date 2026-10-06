package com.ragserver.repository;

import com.ragserver.entity.QueryHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * QueryHistoryRepository 单元测试
 *
 * <p>测试查询历史Repository的数据访问功能：</p>
 * <ul>
 *   <li>基本CRUD操作</li>
 *   <li>查询过滤</li>
 *   <li>统计功能</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DataJpaTest
@ActiveProfiles("test")
class QueryHistoryRepositoryTest {

    @Autowired
    private QueryHistoryRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    /**
     * 测试：保存查询历史
     */
    @Test
    void testSaveQueryHistory() {
        // Given
        QueryHistory history = QueryHistory.create(
            "什么是RAG？",
            "RAG是检索增强生成技术...",
            10,
            false,
            1500L,
            "SUCCESS"
        );

        // When
        QueryHistory saved = repository.save(history);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getQuestion()).isEqualTo("什么是RAG？");
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
    }

    /**
     * 测试：查询最近记录
     */
    @Test
    void testFindTop10ByOrderByCreatedAtDesc() {
        // Given: 创建多条记录
        for (int i = 0; i < 15; i++) {
            QueryHistory history = QueryHistory.create(
                "问题" + i,
                "答案" + i,
                10,
                false,
                1000L,
                "SUCCESS"
            );
            repository.save(history);
        }

        // When
        List<QueryHistory> recent = repository.findTop10ByOrderByCreatedAtDesc();

        // Then
        assertThat(recent).hasSize(10);
    }

    /**
     * 测试：按状态查询
     */
    @Test
    void testFindByStatus() {
        // Given
        repository.save(QueryHistory.create("问题1", "答案1", 10, false, 1000L, "SUCCESS"));
        repository.save(QueryHistory.create("问题2", "答案2", 10, false, 1000L, "SUCCESS"));
        repository.save(QueryHistory.createFailed("问题3", "错误"));

        // When
        List<QueryHistory> successList = repository.findByStatus("SUCCESS");
        List<QueryHistory> failedList = repository.findByStatus("FAILED");

        // Then
        assertThat(successList).hasSize(2);
        assertThat(failedList).hasSize(1);
    }

    /**
     * 测试：时间范围查询
     */
    @Test
    void testFindByCreatedAtBetween() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        QueryHistory history1 = QueryHistory.create("问题1", "答案1", 10, false, 1000L, "SUCCESS");
        QueryHistory history2 = QueryHistory.create("问题2", "答案2", 10, false, 1000L, "SUCCESS");

        repository.save(history1);
        repository.save(history2);

        // When
        List<QueryHistory> results = repository.findByCreatedAtBetween(
            now.minusHours(1),
            now.plusHours(1)
        );

        // Then
        assertThat(results).hasSize(2);
    }

    /**
     * 测试：统计查询数量
     */
    @Test
    void testCountByStatus() {
        // Given
        repository.save(QueryHistory.create("问题1", "答案1", 10, false, 1000L, "SUCCESS"));
        repository.save(QueryHistory.create("问题2", "答案2", 10, false, 1000L, "SUCCESS"));
        repository.save(QueryHistory.createFailed("问题3", "错误"));

        // When
        long successCount = repository.countByStatus("SUCCESS");
        long failedCount = repository.countByStatus("FAILED");

        // Then
        assertThat(successCount).isEqualTo(2);
        assertThat(failedCount).isEqualTo(1);
    }

    /**
     * 测试：计算平均响应时间
     */
    @Test
    void testGetAverageResponseTime() {
        // Given
        repository.save(QueryHistory.create("问题1", "答案1", 10, false, 1000L, "SUCCESS"));
        repository.save(QueryHistory.create("问题2", "答案2", 10, false, 2000L, "SUCCESS"));
        repository.save(QueryHistory.create("问题3", "答案3", 10, false, 3000L, "SUCCESS"));

        // When
        Double avgTime = repository.getAverageResponseTime();

        // Then
        assertThat(avgTime).isEqualTo(2000.0);
    }

    /**
     * 测试：查询最慢的记录
     */
    @Test
    void testFindTop10ByOrderByResponseTimeMsDesc() {
        // Given
        repository.save(QueryHistory.create("快速查询", "答案1", 10, false, 500L, "SUCCESS"));
        repository.save(QueryHistory.create("慢速查询", "答案2", 10, false, 5000L, "SUCCESS"));
        repository.save(QueryHistory.create("中速查询", "答案3", 10, false, 2000L, "SUCCESS"));

        // When
        List<QueryHistory> slowest = repository.findTop10ByOrderByResponseTimeMsDesc();

        // Then
        assertThat(slowest).hasSize(3);
        assertThat(slowest.get(0).getQuestion()).isEqualTo("慢速查询");
        assertThat(slowest.get(0).getResponseTimeMs()).isEqualTo(5000L);
    }

    /**
     * 测试：关键词搜索
     */
    @Test
    void testFindByQuestionContaining() {
        // Given
        repository.save(QueryHistory.create("什么是RAG？", "答案1", 10, false, 1000L, "SUCCESS"));
        repository.save(QueryHistory.create("RAG的优势是什么？", "答案2", 10, false, 1000L, "SUCCESS"));
        repository.save(QueryHistory.create("如何部署系统？", "答案3", 10, false, 1000L, "SUCCESS"));

        // When
        List<QueryHistory> results = repository.findByQuestionContainingOrderByCreatedAtDesc("RAG");

        // Then
        assertThat(results).hasSize(2);
    }

    /**
     * 测试：查询类型过滤
     */
    @Test
    void testFindByQueryType() {
        // Given
        QueryHistory standard = QueryHistory.create("问题1", "答案1", 10, false, 1000L, "SUCCESS");
        standard.setQueryType("STANDARD");
        repository.save(standard);

        QueryHistory streaming = QueryHistory.create("问题2", "答案2", 10, false, 1000L, "SUCCESS");
        streaming.setQueryType("STREAMING");
        repository.save(streaming);

        // When
        List<QueryHistory> standardList = repository.findByQueryType("STANDARD");
        List<QueryHistory> streamingList = repository.findByQueryType("STREAMING");

        // Then
        assertThat(standardList).hasSize(1);
        assertThat(streamingList).hasSize(1);
    }

    /**
     * 测试：无结果查询
     */
    @Test
    void testNoResultsQuery() {
        // Given
        QueryHistory history = QueryHistory.create(
            "找不到答案的问题",
            "抱歉，未找到相关信息",
            10,
            false,
            500L,
            "NO_RESULTS"
        );
        history.setRetrievedDocsCount(0);
        repository.save(history);

        // When
        List<QueryHistory> noResults = repository.findByStatus("NO_RESULTS");

        // Then
        assertThat(noResults).hasSize(1);
        assertThat(noResults.get(0).getRetrievedDocsCount()).isEqualTo(0);
    }
}
