package com.ragserver.ingestion.transformer;

import com.ragserver.ai.dashscope.DashScopeChatClient;
import com.ragserver.retrieval.model.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MetadataEnricher单元测试
 *
 * @author RAG-SERVER开发团队
 */
@DisplayName("MetadataEnricher - 元数据增强器测试")
class MetadataEnricherTest {

    private MetadataEnricher enricher;
    private DashScopeChatClient mockChatClient;

    @BeforeEach
    void setUp() {
        mockChatClient = mock(DashScopeChatClient.class);
        enricher = new MetadataEnricher(mockChatClient);
    }

    @Test
    @DisplayName("测试1: 从Markdown标题提取title")
    void testExtractTitleFromMarkdown() {
        Document chunk = Document.builder()
            .id("chunk_1")
            .text("## 向量数据库介绍\n\nMilvus是一个开源的向量数据库...")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);

        assertEquals("向量数据库介绍", enriched.getMetadata().get("title"));
        assertEquals(0, enriched.getMetadata().get("chunk_index"));
    }

    @Test
    @DisplayName("测试2: 从第一行提取title（无Markdown标题）")
    void testExtractTitleFromFirstLine() {
        Document chunk = Document.builder()
            .id("chunk_2")
            .text("这是一段关于Spring Boot的介绍文本。\n内容很多...")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 1);

        assertEquals("这是一段关于Spring Boot的介绍文本。", enriched.getMetadata().get("title"));
        assertEquals(1, enriched.getMetadata().get("chunk_index"));
    }

    @Test
    @DisplayName("测试3: 长标题自动截断")
    void testLongTitleTruncation() {
        // 构造一个超过50字符的长标题（每个汉字算1个字符）
        String longTitle = "A".repeat(60); // 60个字符，肯定超过50
        Document chunk = Document.builder()
            .id("chunk_3")
            .text("# " + longTitle)
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);
        String title = (String) enriched.getMetadata().get("title");

        assertTrue(title.length() <= 53); // 50 + "..."
        assertTrue(title.endsWith("..."));
    }

    @Test
    @DisplayName("测试4: 提取关键词（英文文本）")
    void testExtractKeywordsEnglish() {
        Document chunk = Document.builder()
            .id("chunk_4")
            .text("Vector database is a database optimized for vector similarity search. " +
                  "Milvus is a vector database. Database performance is important.")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) enriched.getMetadata().get("keywords");

        assertNotNull(keywords);
        assertTrue(keywords.contains("vector") || keywords.contains("database") || keywords.contains("milvus"));
        assertFalse(keywords.contains("is")); // 停用词应被过滤
        assertFalse(keywords.contains("a"));  // 停用词应被过滤
    }

    @Test
    @DisplayName("测试5: 提取关键词（中文文本）")
    void testExtractKeywordsChinese() {
        Document chunk = Document.builder()
            .id("chunk_5")
            .text("向量数据库是一种专门为向量相似度搜索优化的数据库。" +
                  "Milvus是一个开源的向量数据库系统。")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) enriched.getMetadata().get("keywords");

        assertNotNull(keywords);
        assertFalse(keywords.isEmpty());
        // 中文分词简化，至少应该提取到"milvus"这样的英文词
        assertTrue(keywords.stream().anyMatch(k -> k.length() > 1));
    }

    @Test
    @DisplayName("测试6: 规则模式生成标签 - 向量数据库")
    void testExtractTagsVectorDatabase() {
        Document chunk = Document.builder()
            .id("chunk_6")
            .text("Milvus是一个向量数据库，支持高性能的向量检索。")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) enriched.getMetadata().get("tags");

        assertNotNull(tags);
        assertTrue(tags.contains("向量数据库"));
    }

    @Test
    @DisplayName("测试7: 规则模式生成标签 - 后端开发")
    void testExtractTagsBackend() {
        Document chunk = Document.builder()
            .id("chunk_7")
            .text("Spring Boot是一个Java框架，用于构建REST API。")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) enriched.getMetadata().get("tags");

        assertNotNull(tags);
        assertTrue(tags.contains("后端开发"));
    }

    @Test
    @DisplayName("测试8: 规则模式生成标签 - 多个标签")
    void testExtractMultipleTags() {
        Document chunk = Document.builder()
            .id("chunk_8")
            .text("使用Spring Boot构建REST API，集成Milvus向量数据库进行文档检索。" +
                  "通过PDF文档摄取，使用Embedding模型进行向量化。")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) enriched.getMetadata().get("tags");

        assertNotNull(tags);
        assertTrue(tags.size() >= 2);
        assertTrue(tags.contains("向量数据库"));
        assertTrue(tags.contains("后端开发"));
        assertTrue(tags.contains("文档处理") || tags.contains("AI模型"));
    }

    @Test
    @DisplayName("测试9: 无明显特征的文本，生成默认标签")
    void testDefaultTag() {
        Document chunk = Document.builder()
            .id("chunk_9")
            .text("这是一段普通的文本，没有特殊的技术关键词。")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) enriched.getMetadata().get("tags");

        assertNotNull(tags);
        assertTrue(tags.contains("其他"));
    }

    @Test
    @DisplayName("测试10: 保留原有元数据")
    void testPreserveExistingMetadata() {
        Map<String, Object> originalMetadata = new HashMap<>();
        originalMetadata.put("source_path", "/docs/test.pdf");
        originalMetadata.put("page_number", 5);

        Document chunk = Document.builder()
            .id("chunk_10")
            .text("## 测试文档\n内容...")
            .metadata(originalMetadata)
            .build();

        Document enriched = enricher.enrich(chunk, 0);

        // 检查原有元数据是否保留
        assertEquals("/docs/test.pdf", enriched.getMetadata().get("source_path"));
        assertEquals(5, enriched.getMetadata().get("page_number"));

        // 检查新增元数据
        assertNotNull(enriched.getMetadata().get("title"));
        assertNotNull(enriched.getMetadata().get("keywords"));
        assertNotNull(enriched.getMetadata().get("tags"));
    }

    @Test
    @DisplayName("测试11: 添加统计信息")
    void testStatistics() {
        String text = "这是一段测试文本，用于验证统计信息。";
        Document chunk = Document.builder()
            .id("chunk_11")
            .text(text)
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 3);

        assertEquals(3, enriched.getMetadata().get("chunk_index"));
        assertEquals(text.length(), enriched.getMetadata().get("char_count"));
    }

    @Test
    @DisplayName("测试12: 批量增强")
    void testEnrichBatch() {
        List<Document> chunks = Arrays.asList(
            Document.builder()
                .id("chunk_1")
                .text("## 第一块\n内容1...")
                .metadata(new HashMap<>())
                .build(),
            Document.builder()
                .id("chunk_2")
                .text("## 第二块\n内容2...")
                .metadata(new HashMap<>())
                .build(),
            Document.builder()
                .id("chunk_3")
                .text("## 第三块\n内容3...")
                .metadata(new HashMap<>())
                .build()
        );

        List<Document> enriched = enricher.enrichBatch(chunks);

        assertEquals(3, enriched.size());
        for (int i = 0; i < enriched.size(); i++) {
            Document doc = enriched.get(i);
            assertEquals(i, doc.getMetadata().get("chunk_index"));
            assertNotNull(doc.getMetadata().get("title"));
            assertNotNull(doc.getMetadata().get("keywords"));
            assertNotNull(doc.getMetadata().get("tags"));
        }
    }

    @Test
    @DisplayName("测试13: LLM模式成功生成元数据")
    void testLlmModeSuccess() {
        // Mock LLM响应
        when(mockChatClient.chat(anyString()))
            .thenReturn("""
                {
                  "tags": ["技术文档", "数据库", "开发指南"],
                  "summary": "这是一篇关于向量数据库的技术文档"
                }
                """);

        enricher.setUseLlm(true);

        Document chunk = Document.builder()
            .id("chunk_13")
            .text("Milvus向量数据库开发指南...")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) enriched.getMetadata().get("tags");
        String summary = (String) enriched.getMetadata().get("summary");

        assertNotNull(tags);
        assertTrue(tags.contains("技术文档"));
        assertTrue(tags.contains("数据库"));
        assertEquals("这是一篇关于向量数据库的技术文档", summary);

        verify(mockChatClient, times(1)).chat(anyString());
    }

    @Test
    @DisplayName("测试14: LLM模式失败，降级到规则模式")
    void testLlmModeFallbackToRules() {
        // Mock LLM抛出异常
        when(mockChatClient.chat(anyString()))
            .thenThrow(new RuntimeException("API调用失败"));

        enricher.setUseLlm(true);

        Document chunk = Document.builder()
            .id("chunk_14")
            .text("Spring Boot REST API开发...")
            .metadata(new HashMap<>())
            .build();

        // 应该不抛出异常，而是降级到规则模式
        Document enriched = enricher.enrich(chunk, 0);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) enriched.getMetadata().get("tags");

        assertNotNull(tags);
        assertFalse(tags.isEmpty());
        // 应该使用规则模式生成的标签
        assertTrue(tags.contains("后端开发"));

        verify(mockChatClient, times(1)).chat(anyString());
    }

    @Test
    @DisplayName("测试15: 空文本处理")
    void testEmptyText() {
        Document chunk = Document.builder()
            .id("chunk_15")
            .text("")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);

        assertEquals("无标题", enriched.getMetadata().get("title"));
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) enriched.getMetadata().get("keywords");
        assertTrue(keywords.isEmpty());
        assertEquals(0, enriched.getMetadata().get("char_count"));
    }

    @Test
    @DisplayName("测试16: 配置最大关键词数量")
    void testMaxKeywordsConfiguration() {
        enricher.setMaxKeywords(3);

        Document chunk = Document.builder()
            .id("chunk_16")
            .text("database vector search milvus performance optimization query embedding similarity")
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) enriched.getMetadata().get("keywords");

        assertNotNull(keywords);
        assertTrue(keywords.size() <= 3);
    }

    @Test
    @DisplayName("测试17: 复杂Markdown文档")
    void testComplexMarkdownDocument() {
        String complexText = """
            # 主标题

            ## 子标题1

            这是第一段内容，包含向量数据库的介绍。

            ### 三级标题

            更多详细内容...
            """;

        Document chunk = Document.builder()
            .id("chunk_17")
            .text(complexText)
            .metadata(new HashMap<>())
            .build();

        Document enriched = enricher.enrich(chunk, 0);
        String title = (String) enriched.getMetadata().get("title");

        // 应该提取第一个标题
        assertTrue(title.equals("主标题") || title.contains("主标题"));
    }
}
