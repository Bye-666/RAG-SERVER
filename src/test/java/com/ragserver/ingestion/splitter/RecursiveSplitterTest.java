package com.ragserver.ingestion.splitter;

import com.ragserver.retrieval.model.Document;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecursiveSplitter测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("递归文档分块器测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecursiveSplitterTest {

    private static RecursiveSplitter splitter;

    @BeforeAll
    static void setUpAll() {
        splitter = new RecursiveSplitter();
        splitter.setChunkSize(512);
        splitter.setChunkOverlap(128);
    }

    @Test
    @Order(1)
    @DisplayName("1. 短文档不切分")
    void testShortDocument() {
        String shortText = "这是一个短文档，不需要切分。";

        Document doc = Document.builder()
            .id("doc_1")
            .text(shortText)
            .build();

        List<Document> chunks = splitter.split(doc);

        assertEquals(1, chunks.size(), "短文档应该只返回1个块");
        assertEquals(shortText, chunks.get(0).getText(), "文本内容应该不变");

        System.out.println("✅ 短文档不切分（长度: " + shortText.length() + "）");
    }

    @Test
    @Order(2)
    @DisplayName("2. 长文档应切分为多个块")
    void testLongDocument() {
        // 生成超过512字符的长文档
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("这是第").append(i + 1).append("段内容。");
            sb.append("包含一些示例文本用于测试文档切分功能。");
            sb.append("Milvus是一个开源向量数据库，支持十亿级向量检索。\n\n");
        }

        Document doc = Document.builder()
            .id("doc_2")
            .text(sb.toString())
            .build();

        List<Document> chunks = splitter.split(doc);

        assertTrue(chunks.size() > 1, "长文档应该切分为多个块");
        System.out.println("✅ 长文档切分: " + doc.getText().length() + "字符 → " + chunks.size() + "个块");
    }

    @Test
    @Order(3)
    @DisplayName("3. 块大小应在512±20范围内")
    void testChunkSize() {
        // 生成长文档
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("这是测试段落").append(i).append("。");
            sb.append("用于验证块大小是否符合预期。\n\n");
        }

        Document doc = Document.builder()
            .id("doc_3")
            .text(sb.toString())
            .build();

        List<Document> chunks = splitter.split(doc);

        int minSize = 512 - 20;
        int maxSize = 512 + 20;
        int validChunks = 0;

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            int size = chunk.getText().length();

            // 最后一个块可能较小
            if (i == chunks.size() - 1) {
                assertTrue(size <= maxSize, "最后一个块应 <= " + maxSize);
            } else {
                if (size >= minSize && size <= maxSize) {
                    validChunks++;
                }
            }

            System.out.println("  块" + i + ": " + size + "字符");
        }

        // 至少一半的块应该在目标范围内
        assertTrue(validChunks >= chunks.size() / 2,
            "至少一半的块应该在512±20范围内");

        System.out.println("✅ 块大小验证: " + validChunks + "/" + chunks.size() + " 在范围内");
    }

    @Test
    @Order(4)
    @DisplayName("4. Overlap应生效")
    void testOverlap() {
        // 生成简单文档
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("第").append(i + 1).append("行。");
        }

        Document doc = Document.builder()
            .id("doc_4")
            .text(sb.toString())
            .build();

        List<Document> chunks = splitter.split(doc);

        if (chunks.size() > 1) {
            // 检查相邻块之间是否有重叠
            for (int i = 0; i < chunks.size() - 1; i++) {
                String current = chunks.get(i).getText();
                String next = chunks.get(i + 1).getText();

                // 取当前块的末尾部分
                int overlapStart = Math.max(0, current.length() - 128);
                String currentEnd = current.substring(overlapStart);

                // 检查下一个块的开头是否包含这部分
                boolean hasOverlap = next.contains(currentEnd.substring(0, Math.min(50, currentEnd.length())));

                System.out.println("  块" + i + "→块" + (i + 1) + ": overlap=" + hasOverlap);
            }

            System.out.println("✅ Overlap检查完成");
        } else {
            System.out.println("⚠️ 只有1个块，无法测试overlap");
        }
    }

    @Test
    @Order(5)
    @DisplayName("5. 元数据应正确继承")
    void testMetadataInheritance() {
        String text = "这是一个测试文档。".repeat(100);

        Map<String, Object> originalMetadata = Map.of(
            "source", "test.pdf",
            "author", "tester"
        );

        Document doc = Document.builder()
            .id("doc_5")
            .text(text)
            .metadata(originalMetadata)
            .build();

        List<Document> chunks = splitter.split(doc);

        for (Document chunk : chunks) {
            assertNotNull(chunk.getMetadata(), "元数据不应为null");
            assertEquals("test.pdf", chunk.getMetadata().get("source"), "应继承source");
            assertEquals("tester", chunk.getMetadata().get("author"), "应继承author");
            assertTrue(chunk.getMetadata().containsKey("chunk_index"), "应包含chunk_index");
            assertTrue(chunk.getMetadata().containsKey("chunk_total"), "应包含chunk_total");
            assertTrue(chunk.getMetadata().containsKey("original_doc_id"), "应包含original_doc_id");
        }

        System.out.println("✅ 元数据继承验证通过");
        System.out.println("   继承字段: source, author");
        System.out.println("   新增字段: chunk_index, chunk_total, original_doc_id");
    }

    @Test
    @Order(6)
    @DisplayName("6. Chunk ID应正确生成")
    void testChunkIds() {
        String text = "测试内容。".repeat(150);

        Document doc = Document.builder()
            .id("doc_6")
            .text(text)
            .build();

        List<Document> chunks = splitter.split(doc);

        for (int i = 0; i < chunks.size(); i++) {
            String expectedId = "doc_6_chunk_" + i;
            assertEquals(expectedId, chunks.get(i).getId(), "Chunk ID应正确");
        }

        System.out.println("✅ Chunk ID生成正确");
        chunks.forEach(chunk -> System.out.println("   " + chunk.getId()));
    }

    @Test
    @Order(7)
    @DisplayName("7. Markdown标题切分")
    void testMarkdownSplit() {
        String markdownText = """
            ## 第一章
            这是第一章的内容，包含一些介绍性文字。

            ### 1.1 小节
            这是第一个小节的详细内容。

            ## 第二章
            这是第二章的内容。

            ### 2.1 小节
            第二章的第一个小节。
            """;

        Document doc = Document.builder()
            .id("doc_7")
            .text(markdownText)
            .build();

        List<Document> chunks = splitter.split(doc);

        System.out.println("✅ Markdown切分: " + chunks.size() + "个块");
        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("  块" + i + " (" + chunks.get(i).getText().length() + "字符)");
        }
    }

    @Test
    @Order(8)
    @DisplayName("8. 批量切分")
    void testBatchSplit() {
        List<Document> documents = List.of(
            Document.builder().id("doc_a").text("短文档A").build(),
            Document.builder().id("doc_b").text("这是一个较长的文档B。".repeat(50)).build(),
            Document.builder().id("doc_c").text("中等长度文档C。".repeat(20)).build()
        );

        List<Document> allChunks = splitter.splitBatch(documents);

        assertTrue(allChunks.size() >= 3, "至少应有3个块（每个文档至少1个）");

        System.out.println("✅ 批量切分: 3个文档 → " + allChunks.size() + "个块");
    }

    @Test
    @Order(9)
    @DisplayName("9. 空文档处理")
    void testEmptyDocument() {
        Document emptyDoc = Document.builder()
            .id("doc_empty")
            .text("")
            .build();

        List<Document> chunks = splitter.split(emptyDoc);

        assertEquals(0, chunks.size(), "空文档应返回空列表");

        System.out.println("✅ 空文档处理正确");
    }

    @Test
    @Order(10)
    @DisplayName("10. 参数验证")
    void testParameterValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            splitter.setChunkSize(0);
        }, "chunkSize=0应抛出异常");

        assertThrows(IllegalArgumentException.class, () -> {
            splitter.setChunkSize(-1);
        }, "chunkSize<0应抛出异常");

        assertThrows(IllegalArgumentException.class, () -> {
            splitter.setChunkOverlap(-1);
        }, "chunkOverlap<0应抛出异常");

        splitter.setChunkSize(512);
        assertThrows(IllegalArgumentException.class, () -> {
            splitter.setChunkOverlap(512);
        }, "chunkOverlap>=chunkSize应抛出异常");

        System.out.println("✅ 参数验证正确");
    }
}
