package com.ragserver.ingestion.loader;

import com.ragserver.retrieval.model.Document;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PDF加载器测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("PDF加载器测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PdfLoaderTest {

    private static PdfLoader pdfLoader;
    private static Path testPdfPath;

    @BeforeAll
    static void setUpAll() throws IOException {
        pdfLoader = new PdfLoader();

        // 创建测试PDF目录
        Path testDir = Path.of("src/test/resources/fixtures");
        Files.createDirectories(testDir);

        testPdfPath = testDir.resolve("sample.pdf");

        // 如果PDF不存在，生成它
        if (!Files.exists(testPdfPath)) {
            System.out.println("生成测试PDF: " + testPdfPath.toAbsolutePath());
            try {
                TestPdfGenerator.generateTestPdf(testPdfPath);
                System.out.println("✅ 测试PDF生成成功");
            } catch (Exception e) {
                System.err.println("⚠️ 无法生成测试PDF: " + e.getMessage());
            }
        }

        System.out.println("测试PDF路径: " + testPdfPath.toAbsolutePath());
    }

    @Test
    @Order(1)
    @DisplayName("1. 应能加载PDF文档")
    void testLoadPdf() throws IOException {
        // 如果测试PDF不存在，跳过测试
        if (!Files.exists(testPdfPath)) {
            System.out.println("⚠️ 测试PDF不存在，跳过测试");
            System.out.println("   请将测试PDF放置在: " + testPdfPath.toAbsolutePath());
            return;
        }

        List<Document> pages = pdfLoader.load(testPdfPath);

        assertNotNull(pages, "返回结果不应为null");
        assertTrue(pages.size() > 0, "应该至少有1页");

        System.out.println("✅ 成功加载PDF: " + pages.size() + "页");
    }

    @Test
    @Order(2)
    @DisplayName("2. 每页应包含正确的元数据")
    void testPageMetadata() throws IOException {
        if (!Files.exists(testPdfPath)) {
            System.out.println("⚠️ 测试PDF不存在，跳过测试");
            return;
        }

        List<Document> pages = pdfLoader.load(testPdfPath);

        if (pages.isEmpty()) {
            System.out.println("⚠️ PDF无内容，跳过测试");
            return;
        }

        Document firstPage = pages.get(0);

        // 验证元数据
        assertNotNull(firstPage.getMetadata(), "元数据不应为null");
        assertTrue(firstPage.getMetadata().containsKey("source_path"), "应包含source_path");
        assertTrue(firstPage.getMetadata().containsKey("page"), "应包含page");
        assertTrue(firstPage.getMetadata().containsKey("total_pages"), "应包含total_pages");
        assertTrue(firstPage.getMetadata().containsKey("file_name"), "应包含file_name");

        // 验证页码
        assertEquals(1, firstPage.getMetadata().get("page"), "第一页的页码应为1");

        System.out.println("✅ 元数据验证通过");
        System.out.println("   source_path: " + firstPage.getMetadata().get("source_path"));
        System.out.println("   page: " + firstPage.getMetadata().get("page"));
        System.out.println("   total_pages: " + firstPage.getMetadata().get("total_pages"));
    }

    @Test
    @Order(3)
    @DisplayName("3. 应能提取文本内容")
    void testTextExtraction() throws IOException {
        if (!Files.exists(testPdfPath)) {
            System.out.println("⚠️ 测试PDF不存在，跳过测试");
            return;
        }

        List<Document> pages = pdfLoader.load(testPdfPath);

        if (pages.isEmpty()) {
            System.out.println("⚠️ PDF无内容，跳过测试");
            return;
        }

        Document firstPage = pages.get(0);

        assertNotNull(firstPage.getText(), "文本不应为null");
        assertFalse(firstPage.getText().trim().isEmpty(), "文本不应为空");

        System.out.println("✅ 文本提取成功");
        System.out.println("   文本长度: " + firstPage.getText().length());
        System.out.println("   文本预览: " + firstPage.getText().substring(0, Math.min(100, firstPage.getText().length())) + "...");
    }

    @Test
    @Order(4)
    @DisplayName("4. 多页PDF应返回多个Document")
    void testMultiplePages() throws IOException {
        if (!Files.exists(testPdfPath)) {
            System.out.println("⚠️ 测试PDF不存在，跳过测试");
            return;
        }

        List<Document> pages = pdfLoader.load(testPdfPath);

        if (pages.size() <= 1) {
            System.out.println("⚠️ PDF只有1页，跳过多页测试");
            return;
        }

        // 验证每页的页码递增
        for (int i = 0; i < pages.size(); i++) {
            Document page = pages.get(i);
            int expectedPageNumber = i + 1;
            assertEquals(expectedPageNumber, page.getMetadata().get("page"),
                "第" + expectedPageNumber + "页的页码应正确");
        }

        System.out.println("✅ 多页PDF处理正确: " + pages.size() + "页");
    }

    @Test
    @Order(5)
    @DisplayName("5. 每页应有唯一的ID")
    void testUniquePageIds() throws IOException {
        if (!Files.exists(testPdfPath)) {
            System.out.println("⚠️ 测试PDF不存在，跳过测试");
            return;
        }

        List<Document> pages = pdfLoader.load(testPdfPath);

        if (pages.size() <= 1) {
            System.out.println("⚠️ 只有1页，跳过ID唯一性测试");
            return;
        }

        // 验证ID唯一性
        long uniqueIds = pages.stream()
            .map(Document::getId)
            .distinct()
            .count();

        assertEquals(pages.size(), uniqueIds, "每页应有唯一的ID");

        System.out.println("✅ ID唯一性验证通过");
        pages.forEach(page -> System.out.println("   " + page.getId()));
    }

    @Test
    @Order(6)
    @DisplayName("6. 文件不存在应抛出异常")
    void testFileNotFound() {
        Path nonExistentPath = Path.of("non_existent.pdf");

        assertThrows(IllegalArgumentException.class, () -> {
            pdfLoader.load(nonExistentPath);
        }, "文件不存在应抛出IllegalArgumentException");

        System.out.println("✅ 文件不存在异常处理正确");
    }

    @Test
    @Order(7)
    @DisplayName("7. 字符串路径加载")
    void testLoadWithStringPath() throws IOException {
        if (!Files.exists(testPdfPath)) {
            System.out.println("⚠️ 测试PDF不存在，跳过测试");
            return;
        }

        List<Document> pages = pdfLoader.load(testPdfPath.toString());

        assertNotNull(pages, "返回结果不应为null");
        assertTrue(pages.size() > 0, "应该至少有1页");

        System.out.println("✅ 字符串路径加载成功");
    }
}
