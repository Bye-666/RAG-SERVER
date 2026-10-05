package com.ragserver.service;

import com.ragserver.entity.ImageIndex;
import com.ragserver.repository.ImageIndexRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * ImageStorageService 集成测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
        "image.base-dir=./target/test-images"
})
class ImageStorageServiceTest {

    @Autowired
    private ImageStorageService imageStorageService;

    @Autowired
    private ImageIndexRepository imageIndexRepository;

    private static final String TEST_BASE_DIR = "./target/test-images";

    @BeforeEach
    void setUp() {
        // 清空数据库
        imageIndexRepository.deleteAll();
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清理测试目录
        Path testDir = Paths.get(TEST_BASE_DIR);
        if (Files.exists(testDir)) {
            Files.walk(testDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    /**
     * 测试：保存图片
     */
    @Test
    void testSave() {
        // Given: 图片数据和元数据
        byte[] imageData = createTestImageData();
        ImageStorageService.ImageMetadata metadata = ImageStorageService.ImageMetadata.builder()
                .collection("test_collection")
                .docHash("doc_hash_123")
                .pageNum(1)
                .build();

        // When: 保存图片
        String imageId = imageStorageService.save(imageData, metadata);

        // Then: 图片已保存
        assertThat(imageId).isNotNull();
        assertThat(imageId).hasSize(32); // UUID without dashes

        // 验证文件存在
        Optional<ImageIndex> indexOpt = imageIndexRepository.findById(imageId);
        assertThat(indexOpt).isPresent();

        ImageIndex index = indexOpt.get();
        assertThat(index.getDocHash()).isEqualTo("doc_hash_123");
        assertThat(index.getPageNum()).isEqualTo(1);

        Path filePath = Paths.get(index.getFilePath());
        assertThat(filePath).exists();
    }

    /**
     * 测试：查找图片
     */
    @Test
    void testFind() {
        // Given: 保存一个图片
        byte[] imageData = createTestImageData();
        ImageStorageService.ImageMetadata metadata = ImageStorageService.ImageMetadata.builder()
                .collection("test_collection")
                .docHash("doc_hash_456")
                .pageNum(2)
                .build();

        String imageId = imageStorageService.save(imageData, metadata);

        // When: 查找图片
        Optional<ImageStorageService.ImageInfo> infoOpt = imageStorageService.find(imageId);

        // Then: 找到图片
        assertThat(infoOpt).isPresent();

        ImageStorageService.ImageInfo info = infoOpt.get();
        assertThat(info.getImageId()).isEqualTo(imageId);
        assertThat(info.getDocHash()).isEqualTo("doc_hash_456");
        assertThat(info.getPageNum()).isEqualTo(2);
        assertThat(info.getCollection()).isEqualTo("test_collection");
    }

    /**
     * 测试：查找不存在的图片
     */
    @Test
    void testFindNonexistent() {
        // When: 查找不存在的图片
        Optional<ImageStorageService.ImageInfo> infoOpt = imageStorageService.find("nonexistent_id");

        // Then: 返回empty
        assertThat(infoOpt).isEmpty();
    }

    /**
     * 测试：删除文档的所有图片
     */
    @Test
    void testDeleteByDoc() {
        // Given: 保存多个图片
        String docHash = "doc_hash_789";

        for (int i = 1; i <= 3; i++) {
            byte[] imageData = createTestImageData();
            ImageStorageService.ImageMetadata metadata = ImageStorageService.ImageMetadata.builder()
                    .collection("test_collection")
                    .docHash(docHash)
                    .pageNum(i)
                    .build();
            imageStorageService.save(imageData, metadata);
        }

        // When: 删除文档图片
        int deleted = imageStorageService.deleteByDoc(docHash);

        // Then: 所有图片已删除
        assertThat(deleted).isEqualTo(3);

        // 验证数据库中已删除
        assertThat(imageIndexRepository.findByDocHash(docHash)).isEmpty();
    }

    /**
     * 测试：删除不存在的文档图片
     */
    @Test
    void testDeleteByDocNonexistent() {
        // When: 删除不存在的文档
        int deleted = imageStorageService.deleteByDoc("nonexistent_doc");

        // Then: 返回0
        assertThat(deleted).isEqualTo(0);
    }

    /**
     * 测试：按Collection删除
     */
    @Test
    void testDeleteByCollection() {
        // Given: 保存多个Collection的图片
        String collection1 = "collection_1";
        String collection2 = "collection_2";

        // Collection 1: 2张图片
        for (int i = 1; i <= 2; i++) {
            byte[] imageData = createTestImageData();
            ImageStorageService.ImageMetadata metadata = ImageStorageService.ImageMetadata.builder()
                    .collection(collection1)
                    .docHash("doc_" + i)
                    .pageNum(1)
                    .build();
            imageStorageService.save(imageData, metadata);
        }

        // Collection 2: 1张图片
        byte[] imageData = createTestImageData();
        ImageStorageService.ImageMetadata metadata = ImageStorageService.ImageMetadata.builder()
                .collection(collection2)
                .docHash("doc_3")
                .pageNum(1)
                .build();
        imageStorageService.save(imageData, metadata);

        // When: 删除Collection 1
        int deleted = imageStorageService.deleteByCollection(collection1);

        // Then: Collection 1的图片已删除
        assertThat(deleted).isEqualTo(2);
        assertThat(imageIndexRepository.findByCollection(collection1)).isEmpty();

        // Collection 2的图片仍存在
        assertThat(imageIndexRepository.findByCollection(collection2)).hasSize(1);
    }

    /**
     * 测试：获取存储统计
     */
    @Test
    void testGetStats() {
        // Given: 保存一些图片
        for (int i = 1; i <= 3; i++) {
            byte[] imageData = createTestImageData();
            ImageStorageService.ImageMetadata metadata = ImageStorageService.ImageMetadata.builder()
                    .collection("test_collection")
                    .docHash("doc_" + i)
                    .pageNum(1)
                    .build();
            imageStorageService.save(imageData, metadata);
        }

        // When: 获取统计
        ImageStorageService.StorageStats stats = imageStorageService.getStats();

        // Then: 统计正确
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalImages()).isEqualTo(3L);
        assertThat(stats.getBaseDir()).isEqualTo(TEST_BASE_DIR);
    }

    /**
     * 测试：目录自动创建
     */
    @Test
    void testDirectoryCreation() throws IOException {
        // Given: 删除测试目录
        Path testDir = Paths.get(TEST_BASE_DIR);
        if (Files.exists(testDir)) {
            Files.walk(testDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }

        // When: 保存图片（会自动创建目录）
        byte[] imageData = createTestImageData();
        ImageStorageService.ImageMetadata metadata = ImageStorageService.ImageMetadata.builder()
                .collection("test_collection")
                .docHash("doc_hash_new")
                .pageNum(1)
                .build();

        String imageId = imageStorageService.save(imageData, metadata);

        // Then: 目录已创建，图片已保存
        assertThat(imageId).isNotNull();
        Optional<ImageIndex> indexOpt = imageIndexRepository.findById(imageId);
        assertThat(indexOpt).isPresent();

        Path filePath = Paths.get(indexOpt.get().getFilePath());
        assertThat(filePath).exists();
    }

    /**
     * 创建测试图片数据
     */
    private byte[] createTestImageData() {
        // 创建一个简单的PNG头部（8字节）+ 一些随机数据
        byte[] data = new byte[100];
        // PNG magic number
        data[0] = (byte) 0x89;
        data[1] = 'P';
        data[2] = 'N';
        data[3] = 'G';
        data[4] = '\r';
        data[5] = '\n';
        data[6] = 0x1a;
        data[7] = '\n';
        // 其余为随机数据
        for (int i = 8; i < data.length; i++) {
            data[i] = (byte) (Math.random() * 256);
        }
        return data;
    }
}
