package com.ragserver.ingestion.loader;

import com.ragserver.retrieval.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * PDF文档加载器
 *
 * <p>使用Apache PDFBox加载PDF文档，提取文本内容。</p>
 *
 * <h3>功能特点</h3>
 * <ul>
 *   <li>按页提取：每页生成一个Document</li>
 *   <li>元数据记录：source_path、page、total_pages</li>
 *   <li>错误处理：跳过无法解析的页面</li>
 *   <li>资源管理：自动关闭PDF文档</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * PdfLoader loader = new PdfLoader();
 * Path pdfPath = Path.of("document.pdf");
 * List<Document> pages = loader.load(pdfPath);
 *
 * pages.forEach(doc -> {
 *     System.out.println("第" + doc.getMetadata().get("page") + "页");
 *     System.out.println(doc.getText());
 * });
 * }</pre>
 *
 * <h3>元数据结构</h3>
 * <pre>
 * {
 *   "source_path": "/path/to/document.pdf",
 *   "page": 1,
 *   "total_pages": 10,
 *   "file_name": "document.pdf"
 * }
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class PdfLoader {

    /**
     * 加载PDF文档
     *
     * <p>按页提取文本，每页生成一个Document对象。</p>
     *
     * @param pdfPath PDF文件路径
     * @return Document列表（每页一个Document）
     * @throws IOException PDF读取失败时抛出
     */
    public List<Document> load(Path pdfPath) throws IOException {
        if (pdfPath == null || !pdfPath.toFile().exists()) {
            throw new IllegalArgumentException("PDF文件不存在: " + pdfPath);
        }

        log.info("开始加载PDF: {}", pdfPath);
        long startTime = System.currentTimeMillis();

        List<Document> pages = new ArrayList<>();
        File pdfFile = pdfPath.toFile();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int totalPages = document.getNumberOfPages();
            log.debug("PDF总页数: {}", totalPages);

            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 0; i < totalPages; i++) {
                int pageNumber = i + 1;

                try {
                    // 提取单页文本
                    stripper.setStartPage(pageNumber);
                    stripper.setEndPage(pageNumber);
                    String text = stripper.getText(document);

                    // 清理文本（去除多余空白）
                    text = cleanText(text);

                    if (text.trim().isEmpty()) {
                        log.warn("第{}页文本为空，跳过", pageNumber);
                        continue;
                    }

                    // 构建元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source_path", pdfPath.toString());
                    metadata.put("page", pageNumber);
                    metadata.put("total_pages", totalPages);
                    metadata.put("file_name", pdfFile.getName());

                    // 创建Document
                    Document doc = Document.builder()
                        .id(generatePageId(pdfPath, pageNumber))
                        .text(text)
                        .metadata(metadata)
                        .build();

                    pages.add(doc);

                    log.trace("成功提取第{}页，文本长度: {}", pageNumber, text.length());

                } catch (Exception e) {
                    log.error("提取第{}页失败: {}", pageNumber, e.getMessage());
                }
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("PDF加载完成: {} → {}页，耗时{}ms", pdfFile.getName(), pages.size(), elapsedTime);

            return pages;

        } catch (IOException e) {
            log.error("加载PDF失败: {}", pdfPath, e);
            throw e;
        }
    }

    /**
     * 清理文本
     *
     * <p>去除多余空白、统一换行符。</p>
     *
     * @param text 原始文本
     * @return 清理后的文本
     */
    private String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 统一换行符
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        // 去除连续多个空行（保留最多2个换行）
        text = text.replaceAll("\n{3,}", "\n\n");

        // 去除行首行尾空白
        String[] lines = text.split("\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            cleaned.append(line.trim()).append("\n");
        }

        return cleaned.toString().trim();
    }

    /**
     * 生成页面ID
     *
     * <p>格式: 文件名（无扩展名）_page_页码</p>
     *
     * @param pdfPath PDF路径
     * @param pageNumber 页码
     * @return 页面ID
     */
    private String generatePageId(Path pdfPath, int pageNumber) {
        String fileName = pdfPath.getFileName().toString();
        String baseName = fileName.replaceFirst("[.][^.]+$", ""); // 去除扩展名
        return String.format("%s_page_%d", baseName, pageNumber);
    }

    /**
     * 加载PDF文档（简化版）
     *
     * <p>直接传入文件路径字符串。</p>
     *
     * @param pdfPath PDF文件路径字符串
     * @return Document列表
     * @throws IOException PDF读取失败时抛出
     */
    public List<Document> load(String pdfPath) throws IOException {
        return load(Path.of(pdfPath));
    }
}
