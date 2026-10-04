package com.ragserver.service;

import com.ragserver.retrieval.model.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * PromptService 单元测试
 *
 * <p>测试Prompt模板管理的核心功能：</p>
 * <ul>
 *   <li>RAG Prompt构建</li>
 *   <li>Citation生成</li>
 *   <li>模板渲染</li>
 *   <li>变量替换</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
class PromptServiceTest {

    private PromptService promptService;

    @BeforeEach
    void setUp() {
        promptService = new PromptService();
    }

    /**
     * 测试：构建RAG Prompt
     */
    @Test
    void testBuildRagPrompt() {
        // Given: 查询和上下文文档
        String query = "什么是RAG？";
        List<Document> context = createTestDocuments();

        // When: 构建Prompt
        String prompt = promptService.buildRagPrompt(query, context);

        // Then: Prompt包含查询和上下文
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("什么是RAG？");
        assertThat(prompt).contains("RAG是检索增强生成");
        assertThat(prompt).contains("test1.pdf");
        assertThat(prompt).contains("test2.md");
    }

    /**
     * 测试：空上下文的Prompt构建
     */
    @Test
    void testBuildRagPromptWithEmptyContext() {
        // Given: 空上下文
        String query = "测试问题";
        List<Document> emptyContext = List.of();

        // When: 构建Prompt
        String prompt = promptService.buildRagPrompt(query, emptyContext);

        // Then: Prompt仍然包含查询
        assertThat(prompt).isNotNull();
        assertThat(prompt).contains("测试问题");
        assertThat(prompt).contains("上下文:");
    }

    /**
     * 测试：构建Citations
     */
    @Test
    void testBuildCitations() {
        // Given: 文档列表
        List<Document> documents = createTestDocuments();

        // When: 构建Citations
        String citations = promptService.buildCitations(documents);

        // Then: Citations格式正确
        assertThat(citations).isNotNull();
        assertThat(citations).contains("引用来源:");
        assertThat(citations).contains("[1]");
        assertThat(citations).contains("[2]");
        assertThat(citations).contains("test1.pdf");
        assertThat(citations).contains("第1页");
    }

    /**
     * 测试：空文档列表的Citations
     */
    @Test
    void testBuildCitationsWithEmptyList() {
        // Given: 空列表
        List<Document> emptyList = List.of();

        // When: 构建Citations
        String citations = promptService.buildCitations(emptyList);

        // Then: 返回空字符串
        assertThat(citations).isEmpty();
    }

    /**
     * 测试：null文档列表的Citations
     */
    @Test
    void testBuildCitationsWithNull() {
        // Given: null
        List<Document> nullList = null;

        // When: 构建Citations
        String citations = promptService.buildCitations(nullList);

        // Then: 返回空字符串
        assertThat(citations).isEmpty();
    }

    /**
     * 测试：带页码的Citation
     */
    @Test
    void testBuildCitationsWithPageNumber() {
        // Given: 带页码的文档
        Document doc = Document.builder()
                .id("doc_1")
                .text("测试内容")
                .metadata(new HashMap<>(Map.of(
                        "source_path", "/path/to/document.pdf",
                        "page", 5
                )))
                .build();

        // When: 构建Citations
        String citations = promptService.buildCitations(List.of(doc));

        // Then: 包含页码信息
        assertThat(citations).contains("第5页");
    }

    /**
     * 测试：带章节的Citation
     */
    @Test
    void testBuildCitationsWithSection() {
        // Given: 带章节的文档
        Document doc = Document.builder()
                .id("doc_1")
                .text("测试内容")
                .metadata(new HashMap<>(Map.of(
                        "source_path", "/path/to/guide.md",
                        "section", "3"
                )))
                .build();

        // When: 构建Citations
        String citations = promptService.buildCitations(List.of(doc));

        // Then: 包含章节信息
        assertThat(citations).contains("第3节");
    }

    /**
     * 测试：带页码和章节的Citation
     */
    @Test
    void testBuildCitationsWithPageAndSection() {
        // Given: 带页码和章节的文档
        Document doc = Document.builder()
                .id("doc_1")
                .text("测试内容")
                .metadata(new HashMap<>(Map.of(
                        "source_path", "/path/to/book.pdf",
                        "page", 10,
                        "section", "2"
                )))
                .build();

        // When: 构建Citations
        String citations = promptService.buildCitations(List.of(doc));

        // Then: 包含页码和章节信息
        assertThat(citations).contains("第10页");
        assertThat(citations).contains("第2节");
    }

    /**
     * 测试：文档来源路径提取（完整路径）
     */
    @Test
    void testExtractSourcePathFromFullPath() {
        // Given: 完整路径
        Document doc = Document.builder()
                .id("doc_1")
                .text("测试内容")
                .metadata(new HashMap<>(Map.of(
                        "source_path", "/home/user/documents/report.pdf"
                )))
                .build();

        // When: 构建Citations
        String citations = promptService.buildCitations(List.of(doc));

        // Then: 只包含文件名，不包含完整路径
        assertThat(citations).contains("report.pdf");
        assertThat(citations).doesNotContain("/home/user/documents/");
    }

    /**
     * 测试：Windows路径提取
     */
    @Test
    void testExtractSourcePathFromWindowsPath() {
        // Given: Windows路径
        Document doc = Document.builder()
                .id("doc_1")
                .text("测试内容")
                .metadata(new HashMap<>(Map.of(
                        "source_path", "C:\\Users\\Admin\\Documents\\file.docx"
                )))
                .build();

        // When: 构建Citations
        String citations = promptService.buildCitations(List.of(doc));

        // Then: 只包含文件名
        assertThat(citations).contains("file.docx");
        assertThat(citations).doesNotContain("C:\\Users\\");
    }

    /**
     * 测试：使用file_path作为备用来源
     */
    @Test
    void testExtractSourcePathFromFilePath() {
        // Given: 只有file_path，没有source_path
        Document doc = Document.builder()
                .id("doc_1")
                .text("测试内容")
                .metadata(new HashMap<>(Map.of(
                        "file_path", "/data/uploads/document.pdf"
                )))
                .build();

        // When: 构建Citations
        String citations = promptService.buildCitations(List.of(doc));

        // Then: 使用file_path
        assertThat(citations).contains("document.pdf");
    }

    /**
     * 测试：使用文档ID作为最后备用
     */
    @Test
    void testExtractSourcePathFromDocId() {
        // Given: 没有source_path和file_path，只有ID
        Document doc = Document.builder()
                .id("doc_123")
                .text("测试内容")
                .metadata(new HashMap<>())
                .build();

        // When: 构建Citations
        String citations = promptService.buildCitations(List.of(doc));

        // Then: 使用文档ID
        assertThat(citations).contains("doc_123");
    }

    /**
     * 测试：多个文档的Citations序号
     */
    @Test
    void testBuildCitationsNumbering() {
        // Given: 3个文档
        List<Document> documents = List.of(
                createDocument("doc1.pdf", "内容1"),
                createDocument("doc2.pdf", "内容2"),
                createDocument("doc3.pdf", "内容3")
        );

        // When: 构建Citations
        String citations = promptService.buildCitations(documents);

        // Then: 序号正确
        assertThat(citations).contains("[1] doc1.pdf");
        assertThat(citations).contains("[2] doc2.pdf");
        assertThat(citations).contains("[3] doc3.pdf");
    }

    /**
     * 测试：模板缓存清除
     */
    @Test
    void testClearTemplateCache() {
        // Given: 已加载过模板
        String query = "测试";
        List<Document> context = createTestDocuments();
        promptService.buildRagPrompt(query, context);

        // When: 清除缓存
        promptService.clearTemplateCache();

        // Then: 再次构建仍然成功（会重新加载模板）
        String prompt = promptService.buildRagPrompt(query, context);
        assertThat(prompt).isNotNull();
    }

    /**
     * 创建测试文档列表
     */
    private List<Document> createTestDocuments() {
        List<Document> documents = new ArrayList<>();

        Document doc1 = Document.builder()
                .id("doc_1")
                .text("RAG是检索增强生成（Retrieval-Augmented Generation）的缩写。")
                .metadata(new HashMap<>(Map.of(
                        "source_path", "/documents/test1.pdf",
                        "page", 1
                )))
                .build();

        Document doc2 = Document.builder()
                .id("doc_2")
                .text("RAG结合了检索和生成两种技术，能够提供更准确的答案。")
                .metadata(new HashMap<>(Map.of(
                        "source_path", "/documents/test2.md",
                        "section", "2"
                )))
                .build();

        documents.add(doc1);
        documents.add(doc2);

        return documents;
    }

    /**
     * 创建单个测试文档
     */
    private Document createDocument(String sourcePath, String text) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .metadata(new HashMap<>(Map.of("source_path", sourcePath)))
                .build();
    }
}
