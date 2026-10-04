package com.ragserver.repository;

import com.ragserver.entity.ImageIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 图片索引Repository
 *
 * <p>提供ImageIndex实体的数据访问接口，继承Spring Data JPA的JpaRepository。</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>查询文档的所有图片：用于显示和管理</li>
 *   <li>按Collection查询：支持多租户和数据隔离</li>
 *   <li>批量删除：清理文档关联图片</li>
 *   <li>统计分析：了解图片提取情况</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Service
 * public class ImageService {
 *     @Autowired
 *     private ImageIndexRepository repository;
 *
 *     public void extractImagesFromPdf(String docHash, PDDocument pdf) {
 *         for (int pageNum = 0; pageNum < pdf.getNumberOfPages(); pageNum++) {
 *             PDPage page = pdf.getPage(pageNum);
 *             List<RenderedImage> images = extractImages(page);
 *
 *             for (RenderedImage img : images) {
 *                 String imageId = UUID.randomUUID().toString().replace("-", "");
 *                 String filePath = saveImage(img, docHash, imageId);
 *
 *                 ImageIndex index = new ImageIndex();
 *                 index.setImageId(imageId);
 *                 index.setFilePath(filePath);
 *                 index.setDocHash(docHash);
 *                 index.setPageNum(pageNum + 1);
 *                 index.setCollection("rag_knowledge_hub");
 *
 *                 repository.save(index);
 *             }
 *         }
 *     }
 *
 *     public void deleteDocumentImages(String docHash) {
 *         List<ImageIndex> images = repository.findByDocHash(docHash);
 *         for (ImageIndex img : images) {
 *             Files.deleteIfExists(Paths.get(img.getFilePath()));
 *         }
 *         repository.deleteByDocHash(docHash);
 *     }
 * }
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Repository
public interface ImageIndexRepository extends JpaRepository<ImageIndex, String> {

    /**
     * 查询指定文档的所有图片
     *
     * <p>根据文档哈希值查询该文档关联的所有图片。</p>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>显示文档的所有图片</li>
     *   <li>删除文档时清理关联图片</li>
     *   <li>重新摄取时覆盖旧图片</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>{@code
     * List<ImageIndex> images = repository.findByDocHash(docHash);
     * log.info("文档{}包含{}张图片", docHash, images.size());
     *
     * for (ImageIndex img : images) {
     *     log.info("- 图片ID: {}, 页码: {}, 路径: {}",
     *         img.getImageId(), img.getPageNum(), img.getFilePath());
     * }
     * }</pre>
     *
     * @param docHash 文档哈希值（关联到IngestionHistory.fileHash）
     * @return 该文档的所有图片索引列表
     */
    List<ImageIndex> findByDocHash(String docHash);

    /**
     * 查询指定Collection的所有图片
     *
     * <p>用于多租户场景和数据隔离。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 查询生产环境的所有图片
     * List<ImageIndex> prodImages = repository.findByCollection("rag_knowledge_hub");
     *
     * // 查询开发环境的所有图片
     * List<ImageIndex> devImages = repository.findByCollection("rag_knowledge_hub_dev");
     * }</pre>
     *
     * @param collection Collection名称
     * @return 该Collection的所有图片索引列表
     */
    List<ImageIndex> findByCollection(String collection);

    /**
     * 查询指定文档和页码的图片
     *
     * <p>精确定位某个文档某一页的图片。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 查询文档第5页的所有图片
     * List<ImageIndex> page5Images = repository.findByDocHashAndPageNum(docHash, 5);
     * }</pre>
     *
     * @param docHash 文档哈希值
     * @param pageNum 页码（从1开始）
     * @return 该文档该页的所有图片列表
     */
    List<ImageIndex> findByDocHashAndPageNum(String docHash, Integer pageNum);

    /**
     * 查询指定时间范围内创建的图片
     *
     * <p>用于统计和分析。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 查询今天提取的所有图片
     * Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
     * Instant now = Instant.now();
     * List<ImageIndex> todayImages = repository.findByCreatedAtBetween(startOfDay, now);
     * log.info("今天提取了{}张图片", todayImages.size());
     * }</pre>
     *
     * @param start 开始时间（包含）
     * @param end 结束时间（包含）
     * @return 时间范围内的图片索引列表
     */
    List<ImageIndex> findByCreatedAtBetween(Instant start, Instant end);

    /**
     * 统计指定文档的图片数量
     *
     * <p>快速获取文档包含的图片数量，无需加载完整对象。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * long imageCount = repository.countByDocHash(docHash);
     * log.info("文档包含{}张图片", imageCount);
     * }</pre>
     *
     * @param docHash 文档哈希值
     * @return 该文档的图片数量
     */
    long countByDocHash(String docHash);

    /**
     * 统计指定Collection的图片数量
     *
     * <p>用于Dashboard显示。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * long totalImages = repository.countByCollection("rag_knowledge_hub");
     * log.info("系统中共有{}张图片", totalImages);
     * }</pre>
     *
     * @param collection Collection名称
     * @return 该Collection的图片数量
     */
    long countByCollection(String collection);

    /**
     * 删除指定文档的所有图片索引
     *
     * <p>用于清理文档关联的图片记录。</p>
     *
     * <p>⚠️ 注意：此方法只删除数据库记录，不删除实际图片文件。
     * 删除文件需要额外操作：</p>
     * <pre>{@code
     * // 先查询图片列表
     * List<ImageIndex> images = repository.findByDocHash(docHash);
     *
     * // 删除实际文件
     * for (ImageIndex img : images) {
     *     Files.deleteIfExists(Paths.get(img.getFilePath()));
     * }
     *
     * // 再删除数据库记录
     * long deleted = repository.deleteByDocHash(docHash);
     * log.info("已删除{}条图片索引记录", deleted);
     * }</pre>
     *
     * @param docHash 文档哈希值
     * @return 删除的记录数量
     */
    long deleteByDocHash(String docHash);

    /**
     * 删除指定Collection的所有图片索引
     *
     * <p>用于批量清理数据。</p>
     *
     * <p>⚠️ 警告：此操作不可逆，请谨慎使用！</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * // 清理测试环境的所有图片
     * long deleted = repository.deleteByCollection("rag_knowledge_hub_test");
     * log.info("已清理测试环境的{}条图片记录", deleted);
     * }</pre>
     *
     * @param collection Collection名称
     * @return 删除的记录数量
     */
    long deleteByCollection(String collection);

    /**
     * 查询最近创建的N条图片记录
     *
     * <p>用于Dashboard显示最近提取的图片。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * List<ImageIndex> recentImages = repository.findTop10ByOrderByCreatedAtDesc();
     * for (ImageIndex img : recentImages) {
     *     log.info("最近提取: {}, 页码: {}", img.getFilePath(), img.getPageNum());
     * }
     * }</pre>
     *
     * @return 最近10条图片记录
     */
    List<ImageIndex> findTop10ByOrderByCreatedAtDesc();

    /**
     * 检查图片是否存在
     *
     * <p>快速检查某个图片ID是否已存在，避免重复插入。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * if (repository.existsById(imageId)) {
     *     log.warn("图片ID已存在，跳过: {}", imageId);
     *     return;
     * }
     * }</pre>
     *
     * @param imageId 图片ID
     * @return 是否存在
     */
    @Override
    boolean existsById(String imageId);
}
