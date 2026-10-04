package com.ragserver.ingestion.loader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 测试PDF生成工具
 *
 * <p>用于生成测试用的PDF文档。</p>
 */
public class TestPdfGenerator {

    public static void main(String[] args) throws IOException {
        // 创建测试目录
        Path testDir = Path.of("src/test/resources/fixtures");
        Files.createDirectories(testDir);

        Path pdfPath = testDir.resolve("sample.pdf");

        // 生成测试PDF
        generateTestPdf(pdfPath);

        System.out.println("✅ 测试PDF生成成功: " + pdfPath.toAbsolutePath());
    }

    /**
     * 生成测试PDF
     *
     * @param pdfPath 输出路径
     * @throws IOException 写入失败时抛出
     */
    public static void generateTestPdf(Path pdfPath) throws IOException {
        try (PDDocument document = new PDDocument()) {

            // 第一页
            PDPage page1 = new PDPage();
            document.addPage(page1);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page1)) {
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("RAG-SERVER Test Document");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Page 1 of 3");
                contentStream.newLineAtOffset(0, -40);
                contentStream.showText("This is a sample PDF document for testing the PDF loader.");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("It contains multiple pages with different content.");
                contentStream.newLineAtOffset(0, -40);
                contentStream.showText("Milvus is an open-source vector database built for AI applications.");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("It supports billion-scale vector search with high performance.");
                contentStream.endText();
            }

            // 第二页
            PDPage page2 = new PDPage();
            document.addPage(page2);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page2)) {
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Page 2 of 3");
                contentStream.newLineAtOffset(0, -40);
                contentStream.showText("Dense vectors represent semantic meaning of text.");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Sparse vectors like BM25 are good for keyword matching.");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Hybrid search combines both for better results.");
                contentStream.newLineAtOffset(0, -40);
                contentStream.showText("RRF (Reciprocal Rank Fusion) is an algorithm for merging results.");
                contentStream.endText();
            }

            // 第三页
            PDPage page3 = new PDPage();
            document.addPage(page3);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page3)) {
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Page 3 of 3");
                contentStream.newLineAtOffset(0, -40);
                contentStream.showText("Document ingestion pipeline includes:");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("1. PDF loading");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("2. Text splitting");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("3. Embedding generation");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("4. Vector storage");
                contentStream.newLineAtOffset(0, -40);
                contentStream.showText("End of test document.");
                contentStream.endText();
            }

            // 保存文档
            document.save(pdfPath.toFile());
        }
    }
}
