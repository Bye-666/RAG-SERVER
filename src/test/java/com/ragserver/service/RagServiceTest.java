package com.ragserver.service;

import com.ragserver.ai.dashscope.DashScopeChatClient;
import com.ragserver.repository.QueryHistoryRepository;
import com.ragserver.retrieval.HybridRetriever;
import com.ragserver.retrieval.model.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RagService 集成测试
 *
 * <p>测试RAG核心服务的完整流程：</p>
 * <ul>
 *   <li>检索→Prompt构建→LLM生成→Citation添加</li>
 *   <li>各种异常场景处理</li>
 *   <li>参数配置效果</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private HybridRetriever retriever;

    @Mock
    private DashScopeChatClient chatClient;

    @Mock
    private StreamingService streamingService;

    @Mock
    private QueryHistoryRepository queryHistoryRepository;

    private PromptService promptService;
    private RagService ragService;

    @BeforeEach
    void setUp() {
        promptService = new PromptService();
        ragService = new RagService(retriever, chatClient, promptService, streamingService, queryHistoryRepository);
    }

    /**
     * 测试：完整RAG流程
     */
    @Test
    void testQueryFullFlow() {
        // Given: Mock检索和LLM响应
        String question = "什么是RAG？";
        List<Document> mockDocs = createMockDocuments();
        String mockAnswer = "RAG（检索增强生成）是一种结合了检索和生成的AI技术。";

        when(retriever.retrieve(eq(question), eq(10), eq(false))).thenReturn(mockDocs);
        when(chatClient.chat(anyString())).thenReturn(mockAnswer);

        // When: 执行查询
        String result = ragService.query(question);

        // Then: 验证结果包含答案和引用
        assertThat(result).isNotNull();
        assertThat(result).contains(mockAnswer);
        assertThat(result).contains("引用来源:");
        assertThat(result).contains("[1]");
        assertThat(result).contains("test1.pdf");

        // 验证调用链
        verify(retriever).retrieve(eq(question), eq(10), eq(false));
        verify(chatClient).chat(anyString());
    }

    /**
     * 测试：启用Rerank的查询
     */
    @Test
    void testQueryWithRerank() {
        // Given: 启用Rerank
        String question = "测试问题";
        List<Document> mockDocs = createMockDocuments();
        String mockAnswer = "这是答案";

        when(retriever.retrieve(eq(question), eq(5), eq(true))).thenReturn(mockDocs);
        when(chatClient.chat(anyString())).thenReturn(mockAnswer);

        // When: 执行查询
        String result = ragService.query(question, 5, true);

        // Then: 验证Rerank被启用
        assertThat(result).isNotNull();
        verify(retriever).retrieve(eq(question), eq(5), eq(true));
    }

    /**
     * 测试：未检索到文档时的处理
     */
    @Test
    void testQueryWithNoResults() {
        // Given: 检索返回空列表
        String question = "不存在的内容";
        when(retriever.retrieve(eq(question), anyInt(), anyBoolean())).thenReturn(List.of());

        // When: 执行查询
        String result = ragService.query(question);

        // Then: 返回友好提示，不调用LLM
        assertThat(result).contains("未找到与您问题相关的信息");
        verify(chatClient, never()).chat(anyString());
    }

    /**
     * 测试：检索失败时的异常处理
     */
    @Test
    void testQueryWithRetrievalFailure() {
        // Given: 检索抛出异常
        String question = "测试问题";
        when(retriever.retrieve(anyString(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("Milvus连接失败"));

        // When & Then: 抛出异常
        assertThatThrownBy(() -> ragService.query(question))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("RAG查询失败");
    }

    /**
     * 测试：LLM生成失败时的异常处理
     */
    @Test
    void testQueryWithLLMFailure() {
        // Given: LLM调用失败
        String question = "测试问题";
        List<Document> mockDocs = createMockDocuments();

        when(retriever.retrieve(anyString(), anyInt(), anyBoolean())).thenReturn(mockDocs);
        when(chatClient.chat(anyString())).thenThrow(new RuntimeException("API限流"));

        // When & Then: 抛出异常
        assertThatThrownBy(() -> ragService.query(question))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("RAG查询失败");
    }

    /**
     * 测试：仅检索模式
     */
    @Test
    void testRetrieveOnly() {
        // Given: Mock检索
        String question = "测试问题";
        List<Document> mockDocs = createMockDocuments();

        when(retriever.retrieve(eq(question), eq(5), eq(false))).thenReturn(mockDocs);

        // When: 仅检索
        List<Document> results = ragService.retrieveOnly(question, 5, false);

        // Then: 返回文档，不调用LLM
        assertThat(results).isNotNull();
        assertThat(results).hasSize(2);
        verify(retriever).retrieve(eq(question), eq(5), eq(false));
        verify(chatClient, never()).chat(anyString());
    }

    /**
     * 测试：仅检索模式的异常处理
     */
    @Test
    void testRetrieveOnlyWithFailure() {
        // Given: 检索失败
        String question = "测试问题";
        when(retriever.retrieve(anyString(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("检索失败"));

        // When & Then: 抛出异常
        assertThatThrownBy(() -> ragService.retrieveOnly(question, 5, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("检索失败");
    }

    /**
     * 测试：仅构建Prompt模式
     */
    @Test
    void testBuildPromptOnly() {
        // Given: Mock检索
        String question = "什么是RAG？";
        List<Document> mockDocs = createMockDocuments();

        when(retriever.retrieve(eq(question), eq(10), eq(false))).thenReturn(mockDocs);

        // When: 仅构建Prompt
        String prompt = ragService.buildPromptOnly(question, 10, false);

        // Then: 返回Prompt，不调用LLM
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("什么是RAG？");
        assertThat(prompt).contains("test1.pdf");
        verify(retriever).retrieve(eq(question), eq(10), eq(false));
        verify(chatClient, never()).chat(anyString());
    }

    /**
     * 测试：仅构建Prompt模式的异常处理
     */
    @Test
    void testBuildPromptOnlyWithFailure() {
        // Given: 检索失败
        String question = "测试问题";
        when(retriever.retrieve(anyString(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("检索失败"));

        // When & Then: 抛出异常
        assertThatThrownBy(() -> ragService.buildPromptOnly(question, 10, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Prompt构建失败");
    }

    /**
     * 测试：默认参数查询
     */
    @Test
    void testQueryWithDefaultParams() {
        // Given: Mock
        String question = "测试问题";
        List<Document> mockDocs = createMockDocuments();
        String mockAnswer = "答案";

        when(retriever.retrieve(eq(question), eq(10), eq(false))).thenReturn(mockDocs);
        when(chatClient.chat(anyString())).thenReturn(mockAnswer);

        // When: 使用默认参数
        String result = ragService.query(question);

        // Then: 使用topK=10, enableRerank=false
        assertThat(result).isNotNull();
        verify(retriever).retrieve(eq(question), eq(10), eq(false));
    }

    /**
     * 测试：答案和引用正确组合
     */
    @Test
    void testAnswerAndCitationsComposition() {
        // Given: Mock
        String question = "测试问题";
        List<Document> mockDocs = createMockDocuments();
        String mockAnswer = "这是答案部分";

        when(retriever.retrieve(anyString(), anyInt(), anyBoolean())).thenReturn(mockDocs);
        when(chatClient.chat(anyString())).thenReturn(mockAnswer);

        // When: 执行查询
        String result = ragService.query(question);

        // Then: 答案和引用之间有空行分隔
        assertThat(result).contains("这是答案部分");
        assertThat(result).contains("\n\n引用来源:");

        // 验证答案在前，引用在后
        int answerIndex = result.indexOf("这是答案部分");
        int citationIndex = result.indexOf("引用来源:");
        assertThat(answerIndex).isLessThan(citationIndex);
    }

    /**
     * 测试：不同topK参数
     */
    @Test
    void testQueryWithDifferentTopK() {
        // Given: 不同的topK值
        String question = "测试问题";
        List<Document> mockDocs = createMockDocuments();
        String mockAnswer = "答案";

        when(retriever.retrieve(anyString(), anyInt(), anyBoolean())).thenReturn(mockDocs);
        when(chatClient.chat(anyString())).thenReturn(mockAnswer);

        // When: 使用topK=3
        ragService.query(question, 3, false);

        // Then: 验证参数传递
        verify(retriever).retrieve(eq(question), eq(3), eq(false));
    }

    /**
     * 创建Mock文档列表
     */
    private List<Document> createMockDocuments() {
        List<Document> documents = new ArrayList<>();

        Document doc1 = Document.builder()
                .id("doc_1")
                .text("RAG是检索增强生成的缩写。")
                .metadata(new HashMap<>(Map.of(
                        "source_path", "/documents/test1.pdf",
                        "page", 1
                )))
                .build();

        Document doc2 = Document.builder()
                .id("doc_2")
                .text("RAG结合了检索和生成两种技术。")
                .metadata(new HashMap<>(Map.of(
                        "source_path", "/documents/test2.md"
                )))
                .build();

        documents.add(doc1);
        documents.add(doc2);

        return documents;
    }
}
