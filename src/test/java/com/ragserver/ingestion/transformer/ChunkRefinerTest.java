package com.ragserver.ingestion.transformer;

import com.ragserver.retrieval.model.Document;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChunkRefiner测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("文档块精炼器测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChunkRefinerTest {

    private static ChunkRefiner refiner;

    @BeforeAll
    static void setUpAll() {
        refiner = new ChunkRefiner();
    }

    @Test
    @Order(1)
    @DisplayName("1. 应能去除多余空白")
    void testRemoveExtraWhitespace() {
        String noisyText = """
            这是一段   有多余空格   的文本。



            连续多个空行。

            还有\t制表符\t。
            """;

        Document doc = Document.builder()
            .id("doc_1")
            .text(noisyText)
            .build();

        Document refined = refiner.refine(doc);

        // 验证多余空格被清理
        assertFalse(refined.getText().contains("   "), "不应包含连续3个空格");

        // 验证连续空行被合并
        assertFalse(refined.getText().contains("\n\n\n"), "不应包含3个连续换行");

        // 验证制表符被清理
        assertFalse(refined.getText().contains("\t"), "不应包含制表符");

        System.out.println("✅ 多余空白清理成功");
        System.out.println("原始: " + noisyText.length() + "字符");
        System.out.println("清理: " + refined.getText().length() + "字符");
    }

    @Test
    @Order(2)
    @DisplayName("2. 应能清理页眉页脚")
    void testRemoveHeadersFooters() {
        String textWithHeaders = """
            第 1 页
            这是正文内容第一段。

            Page 2
            这是正文内容第二段。

            1/10
            继续正文内容。

            Copyright © 2024
            更多正文。
            """;

        Document doc = Document.builder()
            .id("doc_2")
            .text(textWithHeaders)
            .build();

        Document refined = refiner.refine(doc);

        // 验证页眉页脚被移除
        assertFalse(refined.getText().contains("第 1 页"), "不应包含'第 1 页'");
        assertFalse(refined.getText().contains("Page 2"), "不应包含'Page 2'");
        assertFalse(refined.getText().contains("1/10"), "不应包含'1/10'");
        assertFalse(refined.getText().contains("Copyright"), "不应包含'Copyright'");

        // 验证正文保留
        assertTrue(refined.getText().contains("正文内容"), "应保留正文");

        System.out.println("✅ 页眉页脚清理成功");
        System.out.println("清理后: " + refined.getText());
    }

    @Test
    @Order(3)
    @DisplayName("3. 应能保护代码块格式")
    void testPreserveCodeBlocks() {
        String textWithCode = """
            这是一段说明文字。

            ```python
            def hello():
                print("Hello World")
                return   True
            ```

            继续说明文字。

            行内代码：`int x =   10;` 也应保留。
            """;

        Document doc = Document.builder()
            .id("doc_3")
            .text(textWithCode)
            .build();

        Document refined = refiner.refine(doc);

        // 验证代码块被保留
        assertTrue(refined.getText().contains("```python"), "应保留代码块标记");
        assertTrue(refined.getText().contains("print(\"Hello World\")"), "应保留代码内容");
        assertTrue(refined.getText().contains("`int x =   10;`"), "应保留行内代码");

        // 验证代码块内的空格被保留
        assertTrue(refined.getText().contains("return   True"), "应保留代码块内的多余空格");

        System.out.println("✅ 代码块格式保护成功");
    }

    @Test
    @Order(4)
    @DisplayName("4. 应能规范化特殊字符")
    void testNormalizeSpecialCharacters() {
        String textWithSpecialChars = """
            这是"中文引号"的示例。
            这是'单引号'的示例。
            破折号—测试。
            零宽字符​测试。
            """;

        Document doc = Document.builder()
            .id("doc_4")
            .text(textWithSpecialChars)
            .build();

        Document refined = refiner.refine(doc);

        // 验证引号被规范化
        assertTrue(refined.getText().contains("\"中文引号\""), "应规范化为普通引号");

        // 验证破折号被规范化
        assertTrue(refined.getText().contains("破折号-测试"), "应规范化为普通横线");

        System.out.println("✅ 特殊字符规范化成功");
        System.out.println("清理后: " + refined.getText());
    }

    @Test
    @Order(5)
    @DisplayName("5. 清理后可读性应提升")
    void testReadabilityImprovement() {
        String messyText = """
            第1页

            这是   一段   很   乱的   文本。



            多余的空行。

            Page 2

            继续   内容。

            Copyright © 2024
            """;

        Document doc = Document.builder()
            .id("doc_5")
            .text(messyText)
            .build();

        Document refined = refiner.refine(doc);

        // 验证文本长度减少（噪声被清理）
        assertTrue(refined.getText().length() < messyText.length(),
            "清理后文本应更短");

        // 验证不包含页眉页脚
        assertFalse(refined.getText().contains("第1页"), "不应包含页眉");
        assertFalse(refined.getText().contains("Copyright"), "不应包含页脚");

        // 验证核心内容保留
        assertTrue(refined.getText().contains("这是"), "应保留核心内容");
        assertTrue(refined.getText().contains("继续"), "应保留核心内容");

        System.out.println("✅ 可读性提升验证通过");
        System.out.println("原始: " + messyText.length() + "字符");
        System.out.println("清理: " + refined.getText().length() + "字符");
        System.out.println("减少: " + (messyText.length() - refined.getText().length()) + "字符");
    }

    @Test
    @Order(6)
    @DisplayName("6. 元数据应保留")
    void testMetadataPreservation() {
        Map<String, Object> metadata = Map.of(
            "source", "test.pdf",
            "page", 1
        );

        Document doc = Document.builder()
            .id("doc_6")
            .text("  带有元数据的文档  ")
            .metadata(metadata)
            .build();

        Document refined = refiner.refine(doc);

        assertNotNull(refined.getMetadata(), "元数据不应为null");
        assertEquals("test.pdf", refined.getMetadata().get("source"), "应保留source");
        assertEquals(1, refined.getMetadata().get("page"), "应保留page");

        System.out.println("✅ 元数据保留验证通过");
    }

    @Test
    @Order(7)
    @DisplayName("7. 批量精炼")
    void testBatchRefine() {
        List<Document> documents = List.of(
            Document.builder().id("doc_a").text("  文档A  ").build(),
            Document.builder().id("doc_b").text("  文档B   有多余空格  ").build(),
            Document.builder().id("doc_c").text("第1页\n文档C").build()
        );

        List<Document> refined = refiner.refineBatch(documents);

        assertEquals(3, refined.size(), "应返回3个文档");

        // 验证每个文档都被清理
        for (Document doc : refined) {
            assertFalse(doc.getText().startsWith(" "), "文档开头不应有空格");
            assertFalse(doc.getText().endsWith(" "), "文档结尾不应有空格");
        }

        System.out.println("✅ 批量精炼成功: 3个文档");
    }

    @Test
    @Order(8)
    @DisplayName("8. 空文档处理")
    void testEmptyDocument() {
        Document emptyDoc = Document.builder()
            .id("doc_empty")
            .text("")
            .build();

        Document refined = refiner.refine(emptyDoc);

        assertEquals("", refined.getText(), "空文档应返回空文本");

        System.out.println("✅ 空文档处理正确");
    }

    @Test
    @Order(9)
    @DisplayName("9. 清理统计信息")
    void testCleaningStats() {
        String original = """
            第1页
            这是   一段   文本。



            多余空行。
            """;

        String refined = refiner.refine(Document.builder().text(original).build()).getText();

        Map<String, Object> stats = refiner.getCleaningStats(original, refined);

        assertNotNull(stats, "统计信息不应为null");
        assertTrue((int) stats.get("original_length") > (int) stats.get("refined_length"),
            "原始长度应大于清理后长度");

        System.out.println("✅ 清理统计:");
        System.out.println("  原始长度: " + stats.get("original_length"));
        System.out.println("  清理后长度: " + stats.get("refined_length"));
        System.out.println("  移除字符: " + stats.get("removed_chars"));
        System.out.println("  压缩率: " + stats.get("reduction_rate"));
        System.out.println("  原始行数: " + stats.get("original_lines"));
        System.out.println("  清理后行数: " + stats.get("refined_lines"));
    }

    @Test
    @Order(10)
    @DisplayName("10. 复杂场景综合测试")
    void testComplexScenario() {
        String complexText = """
            第 1 页

            # Milvus向量数据库

            这是   一段   有多余空格   的介绍。



            ## 代码示例

            ```python
            def   search(query):
                # 这里有多余空格但应保留
                return   milvus.search(query)
            ```

            Page 2

            行内代码：`vector =   [1, 2, 3]` 也应保留格式。

            这是"中文引号"和'单引号'。

            Copyright © 2024
            """;

        Document doc = Document.builder()
            .id("doc_complex")
            .text(complexText)
            .build();

        Document refined = refiner.refine(doc);

        // 验证页眉页脚被移除
        assertFalse(refined.getText().contains("第 1 页"), "不应包含页眉");
        assertFalse(refined.getText().contains("Page 2"), "不应包含页码");
        assertFalse(refined.getText().contains("Copyright"), "不应包含版权");

        // 验证代码块被保护
        assertTrue(refined.getText().contains("```python"), "应保留代码块");
        assertTrue(refined.getText().contains("return   milvus"), "应保留代码内空格");
        assertTrue(refined.getText().contains("`vector =   [1, 2, 3]`"), "应保留行内代码");

        // 验证正文空白被清理
        assertFalse(refined.getText().contains("有多余空格   的"), "正文多余空格应被清理");

        // 验证标题保留
        assertTrue(refined.getText().contains("# Milvus"), "应保留标题");

        System.out.println("✅ 复杂场景综合测试通过");
        System.out.println("原始: " + complexText.length() + "字符");
        System.out.println("清理: " + refined.getText().length() + "字符");
        System.out.println("\n清理后文本预览:");
        System.out.println(refined.getText().substring(0, Math.min(200, refined.getText().length())));
    }
}
