package com.ragserver.service;

import com.ragserver.entity.ImageIndex;
import com.ragserver.repository.ImageIndexRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 图片存储服务
 *
 * <p>负责管理从PDF中提取的图片文件：</p>
 * <ul>
 *   <li>图片文件保存：将图片数据写入本地文件系统</li>
 *   <li>图片索引管理：在数据库中记录图片元数据</li>
 *   <li>图片查询：根据ID查找图片</li>
 *   <li>图片删除：清理文档关联的所有图片</li>
 * </ul>
 *
 * <h3>存储结构</h3>
 * <pre>
 * {image.base-dir}/
 * ├── {collection}/
 * │   ├── {doc_hash}/
 * │   │   ├── {image_id}.png
 * │   │   └── {image_id}.png
 * │   └── ...
 * └── ...
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 保存图片
 * byte[] imageData = extractImageFromPdf(page);
 * ImageMetadata metadata = ImageMetadata.builder()
 *     .collection("rag_knowledge_hub")
 *     .docHash("abc123")
 *     .pageNum(1)
 *     .build();
 * String imageId = imageStorageService.save(imageData, metadata);
 *
 * // 查找图片
 * Optional<ImageInfo> image = imageStorageService.find(imageId);
 *
 * // 删除文档的所有图片
 * imageStorageService.deleteByDoc("abc123");
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class ImageStorageService {

    private final ImageIndexRepository imageIndexRepository;
    private final String baseDir;

    /**
     * 默认图片存储目录
     */
    private static final String DEFAULT_IMAGE_DIR = "./data/images";

    public ImageStorageService(ImageIndexRepository imageIndexRepository,
                              @Value("${image.base-dir:./data/images}") String baseDir) {
        this.imageIndexRepository = imageIndexRepository;
        this.baseDir = baseDir;
        initializeStorage();
    }

    /**
     * 初始化存储目录
     */
    private void initializeStorage() {
        try {
            Path basePath = Paths.get(baseDir);
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
                log.info("图片存储目录已创建：{}", baseDir);
            }
        } catch (IOException e) {
            log.error("创建图片存储目录失败：{}", e.getMessage(), e);
            throw new RuntimeException("初始化图片存储失败：" + e.getMessage(), e);
        }
    }

    /**
     * 保存图片
     *
     * <p>将图片数据写入文件系统，并在数据库中创建索引记录。</p>
     *
     * @param imageData 图片二进制数据
     * @param metadata 图片元数据
     * @return 图片ID
     */
    @Transactional
    public String save(byte[] imageData, ImageMetadata metadata) {
        log.debug("保存图片：docHash={}, pageNum={}", metadata.getDocHash(), metadata.getPageNum());

        try {
            // 1. 生成图片ID
            String imageId = generateImageId();

            // 2. 构建文件路径
            String relativePath = buildRelativePath(metadata.getCollection(), metadata.getDocHash(), imageId);
            Path filePath = Paths.get(baseDir, relativePath);

            // 3. 创建目录
            Files.createDirectories(filePath.getParent());

            // 4. 写入文件
            Files.write(filePath, imageData);

            // 5. 创建索引记录
            ImageIndex index = new ImageIndex();
            index.setImageId(imageId);
            index.setFilePath(filePath.toString());
            index.setCollection(metadata.getCollection());
            index.setDocHash(metadata.getDocHash());
            index.setPageNum(metadata.getPageNum());
            index.setCreatedAt(Instant.now());

            imageIndexRepository.save(index);

            log.info("图片保存成功：imageId={}, path={}", imageId, relativePath);
            return imageId;

        } catch (Exception e) {
            log.error("保存图片失败：docHash={}, error={}", metadata.getDocHash(), e.getMessage(), e);
            throw new RuntimeException("保存图片失败：" + e.getMessage(), e);
        }
    }

    /**
     * 查找图片
     *
     * <p>根据图片ID查询图片信息（包含文件路径）。</p>
     *
     * @param imageId 图片ID
     * @return 图片信息，不存在返回empty
     */
    public Optional<ImageInfo> find(String imageId) {
        log.debug("查找图片：imageId={}", imageId);

        try {
            Optional<ImageIndex> indexOpt = imageIndexRepository.findById(imageId);

            if (indexOpt.isEmpty()) {
                log.debug("图片不存在：imageId={}", imageId);
                return Optional.empty();
            }

            ImageIndex index = indexOpt.get();

            // 检查文件是否存在
            Path filePath = Paths.get(index.getFilePath());
            if (!Files.exists(filePath)) {
                log.warn("图片文件不存在：imageId={}, path={}", imageId, index.getFilePath());
                return Optional.empty();
            }

            ImageInfo info = new ImageInfo();
            info.setImageId(index.getImageId());
            info.setFilePath(index.getFilePath());
            info.setCollection(index.getCollection());
            info.setDocHash(index.getDocHash());
            info.setPageNum(index.getPageNum());
            info.setCreatedAt(index.getCreatedAt());

            return Optional.of(info);

        } catch (Exception e) {
            log.error("查找图片失败：imageId={}, error={}", imageId, e.getMessage(), e);
            throw new RuntimeException("查找图片失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除文档的所有图片
     *
     * <p>删除指定文档关联的所有图片文件和索引记录。</p>
     *
     * @param docHash 文档哈希
     * @return 删除的图片数量
     */
    @Transactional
    public int deleteByDoc(String docHash) {
        log.info("删除文档图片：docHash={}", docHash);

        try {
            // 1. 查询所有图片
            List<ImageIndex> images = imageIndexRepository.findByDocHash(docHash);

            if (images.isEmpty()) {
                log.debug("文档无图片：docHash={}", docHash);
                return 0;
            }

            // 2. 删除文件
            int deletedFiles = 0;
            for (ImageIndex image : images) {
                try {
                    Path filePath = Paths.get(image.getFilePath());
                    if (Files.deleteIfExists(filePath)) {
                        deletedFiles++;
                        log.debug("图片文件已删除：{}", image.getFilePath());
                    }
                } catch (IOException e) {
                    log.warn("删除图片文件失败：imageId={}, path={}, error={}",
                            image.getImageId(), image.getFilePath(), e.getMessage());
                }
            }

            // 3. 删除索引记录
            imageIndexRepository.deleteByDocHash(docHash);

            log.info("文档图片删除完成：docHash={}, 删除{}个文件", docHash, deletedFiles);
            return deletedFiles;

        } catch (Exception e) {
            log.error("删除文档图片失败：docHash={}, error={}", docHash, e.getMessage(), e);
            throw new RuntimeException("删除文档图片失败：" + e.getMessage(), e);
        }
    }

    /**
     * 按Collection删除所有图片
     *
     * <p>用于批量清理功能。</p>
     *
     * @param collection Collection名称
     * @return 删除的图片数量
     */
    @Transactional
    public int deleteByCollection(String collection) {
        log.info("删除Collection图片：collection={}", collection);

        try {
            // 1. 查询所有图片
            List<ImageIndex> images = imageIndexRepository.findByCollection(collection);

            if (images.isEmpty()) {
                log.debug("Collection无图片：collection={}", collection);
                return 0;
            }

            // 2. 删除文件
            int deletedFiles = 0;
            for (ImageIndex image : images) {
                try {
                    Path filePath = Paths.get(image.getFilePath());
                    if (Files.deleteIfExists(filePath)) {
                        deletedFiles++;
                    }
                } catch (IOException e) {
                    log.warn("删除图片文件失败：{}", e.getMessage());
                }
            }

            // 3. 删除索引记录
            imageIndexRepository.deleteByCollection(collection);

            log.info("Collection图片删除完成：collection={}, 删除{}个文件", collection, deletedFiles);
            return deletedFiles;

        } catch (Exception e) {
            log.error("删除Collection图片失败：collection={}, error={}", collection, e.getMessage(), e);
            throw new RuntimeException("删除Collection图片失败：" + e.getMessage(), e);
        }
    }

    /**
     * 获取存储统计信息
     *
     * @return 统计信息
     */
    public StorageStats getStats() {
        log.debug("获取图片存储统计");

        try {
            long totalImages = imageIndexRepository.count();

            StorageStats stats = new StorageStats();
            stats.setTotalImages(totalImages);
            stats.setBaseDir(baseDir);

            return stats;

        } catch (Exception e) {
            log.error("获取存储统计失败：{}", e.getMessage(), e);
            throw new RuntimeException("获取存储统计失败：" + e.getMessage(), e);
        }
    }

    /**
     * 生成图片ID
     */
    private String generateImageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构建相对路径
     */
    private String buildRelativePath(String collection, String docHash, String imageId) {
        return String.format("%s/%s/%s.png", collection, docHash, imageId);
    }

    /**
     * 图片元数据（输入）
     */
    public static class ImageMetadata {
        private String collection;
        private String docHash;
        private Integer pageNum;

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final ImageMetadata metadata = new ImageMetadata();

            public Builder collection(String collection) {
                metadata.collection = collection;
                return this;
            }

            public Builder docHash(String docHash) {
                metadata.docHash = docHash;
                return this;
            }

            public Builder pageNum(Integer pageNum) {
                metadata.pageNum = pageNum;
                return this;
            }

            public ImageMetadata build() {
                return metadata;
            }
        }

        public String getCollection() { return collection; }
        public String getDocHash() { return docHash; }
        public Integer getPageNum() { return pageNum; }
    }

    /**
     * 图片信息（输出）
     */
    public static class ImageInfo {
        private String imageId;
        private String filePath;
        private String collection;
        private String docHash;
        private Integer pageNum;
        private Instant createdAt;

        // Getters and Setters
        public String getImageId() { return imageId; }
        public void setImageId(String imageId) { this.imageId = imageId; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }

        public String getDocHash() { return docHash; }
        public void setDocHash(String docHash) { this.docHash = docHash; }

        public Integer getPageNum() { return pageNum; }
        public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }

    /**
     * 存储统计信息
     */
    public static class StorageStats {
        private Long totalImages;
        private String baseDir;

        public Long getTotalImages() { return totalImages; }
        public void setTotalImages(Long totalImages) { this.totalImages = totalImages; }

        public String getBaseDir() { return baseDir; }
        public void setBaseDir(String baseDir) { this.baseDir = baseDir; }
    }
}
