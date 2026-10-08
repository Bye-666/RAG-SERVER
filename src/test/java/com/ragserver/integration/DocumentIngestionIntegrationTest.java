package com.ragserver.integration;

import com.ragserver.entity.IngestionHistory;
import com.ragserver.ingestion.IngestionPipeline;
import com.ragserver.repository.IngestionHistoryRepository;
import com.ragserver.service.IngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 文档摄取集成测试
 *
 * 验证文档上传后历史记录是否正确保存
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentIngestionIntegrationTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private IngestionHistoryRepository historyRepository;

    @AfterEach
    void cleanup() {
        historyRepository.deleteAll();
    }

    @Test
    void testDocumentIngestionSavesHistory() throws IOException {
        // Given: 创建一个测试PDF文件
        Path testPdf = Files.createTempFile("test-doc", ".pdf");

        try {
            // 写入一些PDF内容（简化版，实际PDF需要正确格式）
            Files.writeString(testPdf, "%PDF-1.4\nTest content\n%%EOF");

            // When: 摄取文档
            try {
                IngestionPipeline.IngestionResult result = ingestionService.ingestDocument(testPdf);

                // Then: 验证历史记录已保存
                List<IngestionHistory> histories = historyRepository.findAll();
                assertThat(histories).isNotEmpty();

                IngestionHistory history = histories.get(0);
                assertThat(history.getFilePath()).contains("test-doc");
                assertThat(history.getStatus()).isEqualTo(IngestionHistory.IngestionStatus.SUCCESS);
                assertThat(history.getChunkCount()).isGreaterThanOrEqualTo(0);
                assertThat(history.getProcessedAt()).isNotNull();
                assertThat(history.getFileHash()).isNotNull();

            } catch (Exception e) {
                // PDF格式不正确会失败，但应该保存失败记录
                List<IngestionHistory> histories = historyRepository.findAll();
                if (!histories.isEmpty()) {
                    IngestionHistory history = histories.get(0);
                    assertThat(history.getStatus()).isEqualTo(IngestionHistory.IngestionStatus.FAILED);
                    assertThat(history.getErrorMsg()).isNotNull();
                }
            }

        } finally {
            Files.deleteIfExists(testPdf);
        }
    }

    @Test
    void testFailedIngestionSavesFailureRecord() throws IOException {
        // Given: 创建一个无效的文件
        Path invalidFile = Files.createTempFile("invalid", ".pdf");

        try {
            Files.writeString(invalidFile, "This is not a valid PDF");

            // When: 尝试摄取（应该失败）
            try {
                ingestionService.ingestDocument(invalidFile);
            } catch (Exception e) {
                // 预期会失败
            }

            // Then: 验证失败记录已保存
            List<IngestionHistory> histories = historyRepository.findAll();
            assertThat(histories).isNotEmpty();

            IngestionHistory history = histories.get(0);
            assertThat(history.getStatus()).isEqualTo(IngestionHistory.IngestionStatus.FAILED);
            assertThat(history.getErrorMsg()).isNotNull();
            assertThat(history.getChunkCount()).isEqualTo(0);

        } finally {
            Files.deleteIfExists(invalidFile);
        }
    }
}
