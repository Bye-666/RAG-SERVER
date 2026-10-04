package com.ragserver.ingestion.loader;

import com.ragserver.entity.IngestionHistory;
import com.ragserver.repository.IngestionHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FileIntegrityService单元测试
 *
 * @author RAG-SERVER开发团队
 */
@DisplayName("FileIntegrityService - 文件完整性服务测试")
class FileIntegrityServiceTest {

    private FileIntegrityService integrityService;
    private IngestionHistoryRepository mockRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockRepository = mock(IngestionHistoryRepository.class);
        integrityService = new FileIntegrityService(mockRepository);
    }

    @Test
    @DisplayName("测试1: 计算文件SHA-256哈希")
    void testComputeFileHash() throws IOException {
        // 创建测试文件
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World");

        String hash = integrityService.computeFileHash(testFile);

        assertNotNull(hash);
        assertEquals(64, hash.length()); // SHA-256输出64个十六进制字符
        assertTrue(hash.matches("[0-9a-f]{64}")); // 只包含十六进制字符
    }

    @Test
    @DisplayName("测试2: 相同内容产生相同哈希")
    void testSameContentSameHash() throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");

        String content = "测试内容";
        Files.writeString(file1, content);
        Files.writeString(file2, content);

        String hash1 = integrityService.computeFileHash(file1);
        String hash2 = integrityService.computeFileHash(file2);

        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("测试3: 不同内容产生不同哈希")
    void testDifferentContentDifferentHash() throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");

        Files.writeString(file1, "内容1");
        Files.writeString(file2, "内容2");

        String hash1 = integrityService.computeFileHash(file1);
        String hash2 = integrityService.computeFileHash(file2);

        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("测试4: 文件未摄取过，不应跳过")
    void testShouldNotSkipNewFile() throws IOException {
        Path testFile = tempDir.resolve("new.txt");
        Files.writeString(testFile, "新文件");

        when(mockRepository.findById(any())).thenReturn(Optional.empty());

        boolean shouldSkip = integrityService.shouldSkip(testFile);

        assertFalse(shouldSkip);
        verify(mockRepository, times(1)).findById(any());
    }

    @Test
    @DisplayName("测试5: 文件已成功摄取，应跳过")
    void testShouldSkipSuccessfullyIngestedFile() throws IOException {
        Path testFile = tempDir.resolve("ingested.txt");
        Files.writeString(testFile, "已摄取文件");

        IngestionHistory history = new IngestionHistory();
        history.setStatus(IngestionHistory.IngestionStatus.SUCCESS);

        when(mockRepository.findById(any())).thenReturn(Optional.of(history));

        boolean shouldSkip = integrityService.shouldSkip(testFile);

        assertTrue(shouldSkip);
    }

    @Test
    @DisplayName("测试6: 文件摄取失败，不应跳过（应重新摄取）")
    void testShouldNotSkipFailedFile() throws IOException {
        Path testFile = tempDir.resolve("failed.txt");
        Files.writeString(testFile, "失败文件");

        IngestionHistory history = new IngestionHistory();
        history.setStatus(IngestionHistory.IngestionStatus.FAILED);

        when(mockRepository.findById(any())).thenReturn(Optional.of(history));

        boolean shouldSkip = integrityService.shouldSkip(testFile);

        assertFalse(shouldSkip);
    }

    @Test
    @DisplayName("测试7: 文件正在处理中，不应跳过")
    void testShouldNotSkipProcessingFile() throws IOException {
        Path testFile = tempDir.resolve("processing.txt");
        Files.writeString(testFile, "处理中文件");

        IngestionHistory history = new IngestionHistory();
        history.setStatus(IngestionHistory.IngestionStatus.PROCESSING);

        when(mockRepository.findById(any())).thenReturn(Optional.of(history));

        boolean shouldSkip = integrityService.shouldSkip(testFile);

        assertFalse(shouldSkip);
    }

    @Test
    @DisplayName("测试8: 验证文件完整性 - 成功")
    void testVerifyFileIntegritySuccess() throws IOException {
        Path testFile = tempDir.resolve("verify.txt");
        Files.writeString(testFile, "验证内容");

        String expectedHash = integrityService.computeFileHash(testFile);

        boolean isValid = integrityService.verifyFileIntegrity(testFile, expectedHash);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("测试9: 验证文件完整性 - 失败")
    void testVerifyFileIntegrityFailure() throws IOException {
        Path testFile = tempDir.resolve("verify.txt");
        Files.writeString(testFile, "验证内容");

        String wrongHash = "0".repeat(64); // 错误的哈希

        boolean isValid = integrityService.verifyFileIntegrity(testFile, wrongHash);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("测试10: 检查文件是否被修改 - 新文件")
    void testIsFileModifiedNewFile() throws IOException {
        Path testFile = tempDir.resolve("new.txt");
        Files.writeString(testFile, "新文件");

        when(mockRepository.findById(any())).thenReturn(Optional.empty());
        when(mockRepository.findByFilePathContaining(any())).thenReturn(List.of());

        boolean isModified = integrityService.isFileModified(testFile);

        assertFalse(isModified);
    }

    @Test
    @DisplayName("测试11: 检查文件是否被修改 - 已修改")
    void testIsFileModifiedChanged() throws IOException {
        Path testFile = tempDir.resolve("changed.txt");
        Files.writeString(testFile, "修改后的内容");

        when(mockRepository.findById(any())).thenReturn(Optional.empty());

        IngestionHistory oldHistory = new IngestionHistory();
        oldHistory.setFileHash("old_hash");
        when(mockRepository.findByFilePathContaining(any())).thenReturn(List.of(oldHistory));

        boolean isModified = integrityService.isFileModified(testFile);

        assertTrue(isModified);
    }

    @Test
    @DisplayName("测试12: 检查文件是否被修改 - 未修改")
    void testIsFileModifiedUnchanged() throws IOException {
        Path testFile = tempDir.resolve("unchanged.txt");
        Files.writeString(testFile, "未修改内容");

        String hash = integrityService.computeFileHash(testFile);

        IngestionHistory history = new IngestionHistory();
        history.setFileHash(hash);
        when(mockRepository.findById(hash)).thenReturn(Optional.of(history));

        boolean isModified = integrityService.isFileModified(testFile);

        assertFalse(isModified);
    }

    @Test
    @DisplayName("测试13: 获取文件摄取历史")
    void testGetIngestionHistory() throws IOException {
        Path testFile = tempDir.resolve("history.txt");
        Files.writeString(testFile, "历史记录");

        String hash = integrityService.computeFileHash(testFile);

        IngestionHistory history = new IngestionHistory();
        history.setFileHash(hash);
        when(mockRepository.findById(hash)).thenReturn(Optional.of(history));

        Optional<IngestionHistory> result = integrityService.getIngestionHistory(testFile);

        assertTrue(result.isPresent());
        assertEquals(hash, result.get().getFileHash());
    }

    @Test
    @DisplayName("测试14: 空文件哈希")
    void testEmptyFileHash() throws IOException {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "");

        String hash = integrityService.computeFileHash(emptyFile);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        // 空文件的SHA-256哈希是固定的
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    @DisplayName("测试15: 大文件哈希（性能测试）")
    void testLargeFileHash() throws IOException {
        Path largeFile = tempDir.resolve("large.txt");

        // 创建1MB的文件
        byte[] data = new byte[1024 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        Files.write(largeFile, data);

        long startTime = System.currentTimeMillis();
        String hash = integrityService.computeFileHash(largeFile);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(hash);
        assertEquals(64, hash.length());
        System.out.println("1MB文件哈希计算耗时: " + duration + "ms");
        assertTrue(duration < 500, "1MB文件哈希应在500ms内完成");
    }

    @Test
    @DisplayName("测试16: 哈希值大小写不敏感验证")
    void testHashCaseInsensitiveVerification() throws IOException {
        Path testFile = tempDir.resolve("case.txt");
        Files.writeString(testFile, "测试内容");

        String hash = integrityService.computeFileHash(testFile);
        String upperHash = hash.toUpperCase();

        boolean isValid = integrityService.verifyFileIntegrity(testFile, upperHash);

        assertTrue(isValid);
    }
}
