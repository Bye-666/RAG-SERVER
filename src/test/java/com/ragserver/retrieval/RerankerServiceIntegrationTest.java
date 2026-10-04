package com.ragserver.retrieval;

import com.ragserver.ai.dashscope.DashScopeChatClient;
import com.ragserver.retrieval.model.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RerankerService 集成测试
 *
 * <p>测试LLM重排序服务的核心功能：</p>
 * <ul>
 *   <li>Prompt构建正确性</li>
 *   <li>分数解析准确性</li>
 *   <li>重排序效果验证</li>
 *   <li>超时回退机制</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@SpringBootTest
class RerankerServiceIntegrationTest {

    @Autowired
    private RerankerService rerankerService;

    @MockBean
    private DashScopeChatClient chatClient;

    /**
     * 测试：正常Rerank流程
     */
    @Test
    void testRerank() {
        // Given: 5个候选文档，LLM返回分数
        String query = "什么是RAG？";
        List<Document> candidates = createCandidates();

        String mockResponse = """
                文档1: 9.5
                文档2: 3.2
                文档3: 8.0
                文档4: 5.5
                文档5: 7.0
                """;

        when(chatClient.chat(anyString())).thenReturn(mockResponse);

        // When: Rerank
        List<Document> results = rerankerService.rerank(query, candidates, 3);

        // Then: 返回前3个，按分数降序
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getId()).isEqualTo("doc_1"); // 9.5分
        assertThat(results.get(1).getId()).isEqualTo("doc_3"); // 8.0分
        assertThat(results.get(2).getId()).isEqualTo("doc_5"); // 7.0分

        // 验证分数已保存到metadata
        assertThat(results.get(0).getMetadata().get("rerank_score")).isEqualTo(9.5);
    }

    /**
     * 测试：候选文档少于topK
     */
    @Test
    void testRerankWithFewerCandidates() {
        // Given: 只有3个候选，要求返回5个
        List<Document> candidates = createCandidates().subList(0, 3);

        // When: Rerank
        List<Document> results = rerankerService.rerank("测试", candidates, 5);

        // Then: 直接返回原列表，不调用LLM
        assertThat(results).hasSize(3);
        assertThat(results).isEqualTo(candidates);
        verify(chatClient, never()).chat(anyString());
    }

    /**
     * 测试：空候选列表
     */
    @Test
    void testRerankWithEmptyCandidates() {
        // Given: 空列表
        List<Document> candidates = List.of();

        // When: Rerank
        List<Document> results = rerankerService.rerank("测试", candidates, 5);

        // Then: 返回空列表
        assertThat(results).isEmpty();
        verify(chatClient, never()).chat(anyString());
    }

    /**
     * 测试：LLM返回格式错误，使用默认分数
     */
    @Test
    void testRerankWithInvalidResponse() {
        // Given: LLM返回无效格式
        List<Document> candidates = createCandidates();
        String invalidResponse = "这是一段无法解析的文本";

        when(chatClient.chat(anyString())).thenReturn(invalidResponse);

        // When: Rerank
        List<Document> results = rerankerService.rerank("测试", candidates, 3);

        // Then: 使用默认分数（5.0），返回前3个
        assertThat(results).hasSize(3);
        // 所有文档分数相同，保持原顺序
        assertThat(results.get(0).getMetadata().get("rerank_score")).isEqualTo(5.0);
    }

    /**
     * 测试：LLM调用失败，回退到原排序
     */
    @Test
    void testRerankWithLLMFailure() {
        // Given: LLM抛出异常
        List<Document> candidates = createCandidates();
        when(chatClient.chat(anyString())).thenThrow(new RuntimeException("LLM调用失败"));

        // When: Rerank
        List<Document> results = rerankerService.rerank("测试", candidates, 3);

        // Then: 回退到原排序的前3个
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getId()).isEqualTo("doc_1");
        assertThat(results.get(1).getId()).isEqualTo("doc_2");
        assertThat(results.get(2).getId()).isEqualTo("doc_3");
    }

    /**
     * 测试：候选文档超过最大限制（20个）
     */
    @Test
    void testRerankWithTooManyCandidates() {
        // Given: 25个候选文档
        List<Document> candidates = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            candidates.add(Document.builder()
                    .id("doc_" + i)
                    .text("文档内容 " + i)
                    .metadata(new HashMap<>())
                    .build());
        }

        String mockResponse = buildMockResponse(20); // 只返回前20个的分数
        when(chatClient.chat(anyString())).thenReturn(mockResponse);

        // When: Rerank
        List<Document> results = rerankerService.rerank("测试", candidates, 10);

        // Then: 只处理前20个，返回前10个
        assertThat(results).hasSize(10);
    }

    /**
     * 测试：部分文档分数解析失败
     */
    @Test
    void testRerankWithPartialScores() {
        // Given: 10个候选，但只有部分有效分数（返回5个）
        List<Document> candidates = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            candidates.add(Document.builder()
                    .id("doc_" + i)
                    .text("文档内容 " + i)
                    .metadata(new HashMap<>())
                    .build());
        }

        String partialResponse = """
                文档1: 9.0
                文档2: 无效分数
                文档3: 8.0
                文档4:
                文档5: 7.0
                文档6: 6.0
                文档7: 5.0
                文档8: 无效
                文档9: 4.0
                文档10: 3.0
                """;

        when(chatClient.chat(anyString())).thenReturn(partialResponse);

        // When: Rerank（返回5个）
        List<Document> results = rerankerService.rerank("测试", candidates, 5);

        // Then: 返回前5个，都应该有分数
        assertThat(results).hasSize(5);

        // 验证所有返回的文档都有分数
        for (int i = 0; i < results.size(); i++) {
            Object scoreObj = results.get(i).getMetadata().get("rerank_score");
            assertThat(scoreObj)
                    .as("Document at index " + i + " should have rerank_score")
                    .isNotNull();
        }

        // 验证分数降序排列
        Double prevScore = null;
        for (Document doc : results) {
            Double currentScore = (Double) doc.getMetadata().get("rerank_score");
            if (prevScore != null) {
                assertThat(currentScore).isLessThanOrEqualTo(prevScore);
            }
            prevScore = currentScore;
        }
    }

    /**
     * 测试：分数超出范围自动修正
     */
    @Test
    void testRerankWithOutOfRangeScores() {
        // Given: 10个候选文档，分数超出0-10范围
        List<Document> candidates = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            candidates.add(Document.builder()
                    .id("doc_" + i)
                    .text("文档内容 " + i)
                    .metadata(new HashMap<>())
                    .build());
        }

        String outOfRangeResponse = """
                文档1: 15.0
                文档2: -5.0
                文档3: 8.0
                文档4: 100.0
                文档5: 7.0
                文档6: 6.0
                文档7: -10.0
                文档8: 20.0
                文档9: 5.0
                文档10: 4.0
                """;

        when(chatClient.chat(anyString())).thenReturn(outOfRangeResponse);

        // When: Rerank（返回5个）
        List<Document> results = rerankerService.rerank("测试", candidates, 5);

        // Then: 分数被修正到0-10范围
        assertThat(results).hasSize(5);

        for (Document doc : results) {
            Object scoreObj = doc.getMetadata().get("rerank_score");
            assertThat(scoreObj).as("Document %s should have rerank_score", doc.getId()).isNotNull();
            double score = (Double) scoreObj;
            assertThat(score).isBetween(0.0, 10.0);
        }
    }

    /**
     * 创建测试候选文档
     */
    private List<Document> createCandidates() {
        List<Document> candidates = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            candidates.add(Document.builder()
                    .id("doc_" + i)
                    .text("这是第" + i + "个文档的内容，包含一些测试文本用于Rerank测试。")
                    .metadata(new HashMap<>())
                    .build());
        }
        return candidates;
    }

    /**
     * 构建Mock响应
     */
    private String buildMockResponse(int count) {
        StringBuilder response = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            response.append(String.format("文档%d: %.1f\n", i, 10.0 - i * 0.3));
        }
        return response.toString();
    }
}
