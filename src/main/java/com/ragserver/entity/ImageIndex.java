package com.ragserver.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

/**
 * 图片索引实体
 *
 * <p>记录从PDF中提取的图片信息，用于：</p>
 * <ul>
 *   <li>图片管理：追踪提取的图片位置和来源</li>
 *   <li>Vision LLM：为图片生成描述文本（可选功能）</li>
 *   <li>多模态检索：支持图文混合检索（未来扩展）</li>
 *   <li>数据清理：批量删除文档时同步删除关联图片</li>
 * </ul>
 *
 * <h3>业务流程</h3>
 * <ol>
 *   <li>PDF摄取时，使用PDFBox提取嵌入的图片</li>
 *   <li>为每张图片生成唯一ID（UUID）</li>
 *   <li>保存图片到本地目录（./data/images/）</li>
 *   <li>在image_index表记录图片元数据</li>
 *   <li>（可选）调用Vision LLM生成图片描述</li>
 *   <li>将描述文本作为chunk插入Milvus</li>
 * </ol>
 *
 * <h3>表结构</h3>
 * <pre>
 * CREATE TABLE image_index (
 *     image_id VARCHAR(64) PRIMARY KEY,      -- 图片唯一标识
 *     file_path VARCHAR(512),                -- 图片文件路径
 *     collection VARCHAR(128),               -- 关联的Milvus Collection
 *     doc_hash VARCHAR(64),                  -- 来源文档哈希值
 *     page_num INTEGER,                      -- 来源页码
 *     created_at TIMESTAMP                   -- 创建时间
 * );
 * </pre>
 *
 * <h3>关联关系</h3>
 * <pre>
 * IngestionHistory (1) ----< (N) ImageIndex
 *   file_hash                      doc_hash
 *
 * 一个文档可以包含多张图片
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
@Entity
@Table(name = "image_index")
public class ImageIndex {

    /**
     * 图片唯一标识（主键）
     *
     * <p>使用UUID生成唯一标识，确保图片ID全局唯一。</p>
     *
     * <p>生成方式：</p>
     * <pre>{@code
     * String imageId = UUID.randomUUID().toString().replace("-", "");
     * // 示例：a1b2c3d4e5f67890a1b2c3d4e5f67890
     * }</pre>
     *
     * <p>格式：32字符十六进制字符串（去掉连字符的UUID）</p>
     */
    @Id
    @Column(name = "image_id", length = 64, nullable = false)
    private String imageId;

    /**
     * 图片文件路径
     *
     * <p>图片在本地文件系统的存储路径。</p>
     *
     * <p>路径格式：</p>
     * <pre>
     * ./data/images/{doc_hash}/{image_id}.png
     * 或
     * ./data/images/{doc_hash}/{image_id}.jpg
     * </pre>
     *
     * <p>示例：</p>
     * <ul>
     *   <li>./data/images/abc123.../a1b2c3d4e5f67890.png</li>
     *   <li>/var/rag-server/data/images/def456.../b2c3d4e5f6789012.jpg</li>
     * </ul>
     *
     * <p>目录结构：</p>
     * <pre>
     * data/images/
     *   └── {doc_hash}/          # 按文档哈希分目录
     *       ├── image1.png
     *       ├── image2.jpg
     *       └── image3.png
     * </pre>
     */
    @Column(name = "file_path", length = 512)
    private String filePath;

    /**
     * 关联的Milvus Collection名称
     *
     * <p>图片描述文本插入的Collection名称。</p>
     *
     * <p>用途：</p>
     * <ul>
     *   <li>多租户场景：不同用户使用不同Collection</li>
     *   <li>数据隔离：开发/测试/生产环境使用不同Collection</li>
     *   <li>批量删除：根据Collection批量删除数据</li>
     * </ul>
     *
     * <p>示例值：</p>
     * <ul>
     *   <li>rag_knowledge_hub（生产环境）</li>
     *   <li>rag_knowledge_hub_dev（开发环境）</li>
     *   <li>rag_knowledge_hub_test（测试环境）</li>
     * </ul>
     */
    @Column(name = "collection", length = 128)
    private String collection;

    /**
     * 来源文档哈希值
     *
     * <p>图片来源的PDF文档的SHA256哈希值，关联到IngestionHistory表。</p>
     *
     * <p>用途：</p>
     * <ul>
     *   <li>追溯图片来源</li>
     *   <li>删除文档时同步删除关联图片</li>
     *   <li>重新摄取时清理旧图片</li>
     * </ul>
     *
     * <p>查询示例：</p>
     * <pre>{@code
     * // 查询某个文档的所有图片
     * List<ImageIndex> images = imageIndexRepository.findByDocHash(docHash);
     *
     * // 删除文档时清理图片
     * imageIndexRepository.deleteByDocHash(docHash);
     * }</pre>
     */
    @Column(name = "doc_hash", length = 64)
    private String docHash;

    /**
     * 来源页码
     *
     * <p>图片在PDF中的页码（从1开始）。</p>
     *
     * <p>用途：</p>
     * <ul>
     *   <li>定位图片在原文档中的位置</li>
     *   <li>生成图片描述时的上下文信息</li>
     *   <li>用户查看时提供页码信息</li>
     * </ul>
     *
     * <p>示例：</p>
     * <ul>
     *   <li>1 = 第1页</li>
     *   <li>10 = 第10页</li>
     *   <li>null = 未知页码（某些提取场景）</li>
     * </ul>
     */
    @Column(name = "page_num")
    private Integer pageNum;

    /**
     * 创建时间
     *
     * <p>图片提取并保存到数据库的时间。</p>
     *
     * <p>用途：</p>
     * <ul>
     *   <li>审计和追踪</li>
     *   <li>按时间排序显示</li>
     *   <li>数据清理（删除过期图片）</li>
     * </ul>
     *
     * <p>建议在保存前自动设置：</p>
     * <pre>{@code
     * @PrePersist
     * protected void onCreate() {
     *     if (createdAt == null) {
     *         createdAt = Instant.now();
     *     }
     * }
     * }</pre>
     */
    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * JPA生命周期回调：持久化前设置创建时间
     *
     * <p>在实体保存到数据库前自动调用，确保createdAt字段有值。</p>
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
