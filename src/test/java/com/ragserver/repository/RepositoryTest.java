package com.ragserver.repository;

import com.ragserver.entity.ImageIndex;
import com.ragserver.entity.IngestionHistory;
import com.ragserver.entity.IngestionHistory.IngestionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository测试类
 *
 * <p>使用@DataJpaTest测试Repository的CRUD操作。</p>
 *
 * <h3>测试内容</h3>
 * <ul>
 *   <li>IngestionHistory的增删改查</li>
 *   <li>ImageIndex的增删改查</li>
 *   <li>自定义查询方法</li>
 *   <li>统计方法</li>
 * </ul>
 *
 * <h3>@DataJpaTest特点</h3>
 * <ul>
 *   <li>自动配置H2内存数据库</li>
 *   <li>自动扫描@Entity和@Repository</li>
 *   <li>每个测试方法事务回滚（不影响其他测试）</li>
 *   <li>不加载完整的Spring容器（更快）</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Repository测试")
class RepositoryTest {

    @Autowired
    private IngestionHistoryRepository ingestionHistoryRepository;

    @Autowired
    private ImageIndexRepository imageIndexRepository;

    // ========================================
    // IngestionHistory 测试
    // ========================================

    @Test
    @DisplayName("IngestionHistory应能保存和查询")
    void testIngestionHistorySaveAndFind() {
        // 创建摄取记录
        IngestionHistory history = new IngestionHistory();
        history.setFileHash("abc123def456");
        history.setFilePath("test.pdf");
        history.setFileSize(1024000L);
        history.setStatus(IngestionStatus.SUCCESS);
        history.setProcessedAt(Instant.now());
        history.setChunkCount(50);

        // 保存
        IngestionHistory saved = ingestionHistoryRepository.save(history);
        assertNotNull(saved, "保存后的对象不应为null");
        assertEquals("abc123def456", saved.getFileHash(), "文件哈希应正确保存");

        // 查询
        Optional<IngestionHistory> found = ingestionHistoryRepository.findById("abc123def456");
        assertTrue(found.isPresent(), "应能查询到保存的记录");
        assertEquals("test.pdf", found.get().getFilePath(), "文件路径应正确");
        assertEquals(IngestionStatus.SUCCESS, found.get().getStatus(), "状态应正确");
    }

    @Test
    @DisplayName("应能根据状态查询记录")
    void testFindByStatus() {
        // 创建不同状态的记录
        IngestionHistory success1 = createHistory("hash1", "file1.pdf", IngestionStatus.SUCCESS);
        IngestionHistory success2 = createHistory("hash2", "file2.pdf", IngestionStatus.SUCCESS);
        IngestionHistory failed = createHistory("hash3", "file3.pdf", IngestionStatus.FAILED);
        IngestionHistory processing = createHistory("hash4", "file4.pdf", IngestionStatus.PROCESSING);

        ingestionHistoryRepository.saveAll(List.of(success1, success2, failed, processing));

        // 查询成功记录
        List<IngestionHistory> successList = ingestionHistoryRepository.findByStatusOrderByProcessedAtDesc(IngestionStatus.SUCCESS);
        assertEquals(2, successList.size(), "应有2条成功记录");

        // 查询失败记录
        List<IngestionHistory> failedList = ingestionHistoryRepository.findByStatusOrderByProcessedAtDesc(IngestionStatus.FAILED);
        assertEquals(1, failedList.size(), "应有1条失败记录");

        // 查询处理中记录
        List<IngestionHistory> processingList = ingestionHistoryRepository.findByStatusOrderByProcessedAtDesc(IngestionStatus.PROCESSING);
        assertEquals(1, processingList.size(), "应有1条处理中记录");
    }

    @Test
    @DisplayName("应能根据文件路径模糊查询")
    void testFindByFilePathContaining() {
        // 创建不同文件名的记录
        ingestionHistoryRepository.save(createHistory("hash1", "产品手册.pdf", IngestionStatus.SUCCESS));
        ingestionHistoryRepository.save(createHistory("hash2", "技术文档.pdf", IngestionStatus.SUCCESS));
        ingestionHistoryRepository.save(createHistory("hash3", "产品说明书.pdf", IngestionStatus.SUCCESS));

        // 查询包含"产品"的文档
        List<IngestionHistory> results = ingestionHistoryRepository.findByFilePathContaining("产品");
        assertEquals(2, results.size(), "应找到2个包含'产品'的文档");

        // 查询包含".pdf"的文档
        List<IngestionHistory> pdfs = ingestionHistoryRepository.findByFilePathContaining(".pdf");
        assertEquals(3, pdfs.size(), "应找到3个PDF文档");
    }

    @Test
    @DisplayName("应能统计状态数量")
    void testCountByStatus() {
        // 创建多条记录
        ingestionHistoryRepository.save(createHistory("hash1", "file1.pdf", IngestionStatus.SUCCESS));
        ingestionHistoryRepository.save(createHistory("hash2", "file2.pdf", IngestionStatus.SUCCESS));
        ingestionHistoryRepository.save(createHistory("hash3", "file3.pdf", IngestionStatus.SUCCESS));
        ingestionHistoryRepository.save(createHistory("hash4", "file4.pdf", IngestionStatus.FAILED));

        // 统计
        long successCount = ingestionHistoryRepository.countByStatus(IngestionStatus.SUCCESS);
        long failedCount = ingestionHistoryRepository.countByStatus(IngestionStatus.FAILED);

        assertEquals(3, successCount, "应有3条成功记录");
        assertEquals(1, failedCount, "应有1条失败记录");
    }

    @Test
    @DisplayName("应能统计总chunk数量")
    void testSumChunkCount() {
        // 创建带chunk数量的记录
        IngestionHistory h1 = createHistory("hash1", "file1.pdf", IngestionStatus.SUCCESS);
        h1.setChunkCount(50);

        IngestionHistory h2 = createHistory("hash2", "file2.pdf", IngestionStatus.SUCCESS);
        h2.setChunkCount(100);

        IngestionHistory h3 = createHistory("hash3", "file3.pdf", IngestionStatus.FAILED);
        h3.setChunkCount(0);

        ingestionHistoryRepository.saveAll(List.of(h1, h2, h3));

        // 统计成功记录的chunk总数
        Long totalChunks = ingestionHistoryRepository.sumChunkCountByStatus(IngestionStatus.SUCCESS);
        assertEquals(150, totalChunks, "成功记录的chunk总数应为150");
    }

    @Test
    @DisplayName("应能查询时间范围内的记录")
    void testFindByProcessedAtBetween() {
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        // 创建不同时间的记录
        IngestionHistory h1 = createHistory("hash1", "file1.pdf", IngestionStatus.SUCCESS);
        h1.setProcessedAt(twoDaysAgo);

        IngestionHistory h2 = createHistory("hash2", "file2.pdf", IngestionStatus.SUCCESS);
        h2.setProcessedAt(yesterday);

        IngestionHistory h3 = createHistory("hash3", "file3.pdf", IngestionStatus.SUCCESS);
        h3.setProcessedAt(now);

        ingestionHistoryRepository.saveAll(List.of(h1, h2, h3));

        // 查询最近1天的记录
        List<IngestionHistory> recent = ingestionHistoryRepository.findByProcessedAtBetween(
            yesterday.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS)
        );

        assertEquals(2, recent.size(), "最近1天应有2条记录");
    }

    // ========================================
    // ImageIndex 测试
    // ========================================

    @Test
    @DisplayName("ImageIndex应能保存和查询")
    void testImageIndexSaveAndFind() {
        // 创建图片索引
        ImageIndex image = new ImageIndex();
        image.setImageId("img123");
        image.setFilePath("./data/images/abc123/img123.png");
        image.setCollection("rag_knowledge_hub_test");
        image.setDocHash("abc123");
        image.setPageNum(5);

        // 保存
        ImageIndex saved = imageIndexRepository.save(image);
        assertNotNull(saved, "保存后的对象不应为null");
        assertNotNull(saved.getCreatedAt(), "创建时间应自动设置");

        // 查询
        Optional<ImageIndex> found = imageIndexRepository.findById("img123");
        assertTrue(found.isPresent(), "应能查询到保存的记录");
        assertEquals("./data/images/abc123/img123.png", found.get().getFilePath(), "文件路径应正确");
        assertEquals(5, found.get().getPageNum(), "页码应正确");
    }

    @Test
    @DisplayName("应能根据文档哈希查询图片")
    void testFindByDocHash() {
        // 创建同一文档的多张图片
        imageIndexRepository.save(createImage("img1", "doc123", 1));
        imageIndexRepository.save(createImage("img2", "doc123", 2));
        imageIndexRepository.save(createImage("img3", "doc456", 1));

        // 查询doc123的图片
        List<ImageIndex> images = imageIndexRepository.findByDocHash("doc123");
        assertEquals(2, images.size(), "doc123应有2张图片");
    }

    @Test
    @DisplayName("应能根据Collection查询图片")
    void testFindByCollection() {
        // 创建不同Collection的图片
        ImageIndex img1 = createImage("img1", "doc1", 1);
        img1.setCollection("collection_a");

        ImageIndex img2 = createImage("img2", "doc2", 1);
        img2.setCollection("collection_a");

        ImageIndex img3 = createImage("img3", "doc3", 1);
        img3.setCollection("collection_b");

        imageIndexRepository.saveAll(List.of(img1, img2, img3));

        // 查询collection_a的图片
        List<ImageIndex> results = imageIndexRepository.findByCollection("collection_a");
        assertEquals(2, results.size(), "collection_a应有2张图片");
    }

    @Test
    @DisplayName("应能统计文档的图片数量")
    void testCountByDocHash() {
        // 创建多张图片
        imageIndexRepository.save(createImage("img1", "doc123", 1));
        imageIndexRepository.save(createImage("img2", "doc123", 2));
        imageIndexRepository.save(createImage("img3", "doc123", 3));

        // 统计
        long count = imageIndexRepository.countByDocHash("doc123");
        assertEquals(3, count, "doc123应有3张图片");
    }

    @Test
    @DisplayName("应能删除文档的所有图片")
    void testDeleteByDocHash() {
        // 创建多张图片
        imageIndexRepository.save(createImage("img1", "doc123", 1));
        imageIndexRepository.save(createImage("img2", "doc123", 2));
        imageIndexRepository.save(createImage("img3", "doc456", 1));

        // 删除doc123的图片
        long deleted = imageIndexRepository.deleteByDocHash("doc123");
        assertEquals(2, deleted, "应删除2张图片");

        // 验证
        List<ImageIndex> remaining = imageIndexRepository.findByDocHash("doc123");
        assertEquals(0, remaining.size(), "doc123的图片应全部删除");

        List<ImageIndex> others = imageIndexRepository.findByDocHash("doc456");
        assertEquals(1, others.size(), "其他文档的图片应保留");
    }

    // ========================================
    // 辅助方法
    // ========================================

    /**
     * 创建测试用的IngestionHistory
     */
    private IngestionHistory createHistory(String fileHash, String filePath, IngestionStatus status) {
        IngestionHistory history = new IngestionHistory();
        history.setFileHash(fileHash);
        history.setFilePath(filePath);
        history.setFileSize(1024000L);
        history.setStatus(status);
        history.setProcessedAt(Instant.now());
        history.setChunkCount(10);
        return history;
    }

    /**
     * 创建测试用的ImageIndex
     */
    private ImageIndex createImage(String imageId, String docHash, int pageNum) {
        ImageIndex image = new ImageIndex();
        image.setImageId(imageId);
        image.setFilePath("./data/images/" + docHash + "/" + imageId + ".png");
        image.setCollection("rag_knowledge_hub_test");
        image.setDocHash(docHash);
        image.setPageNum(pageNum);
        return image;
    }
}
