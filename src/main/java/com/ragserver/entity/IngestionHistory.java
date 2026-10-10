package com.ragserver.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

/**
 * 文档摄取历史记录实体
 *
 * <p>记录每个文档的摄取状态，用于：</p>
 * <ul>
 *   <li>防重复摄取：通过文件哈希判断文档是否已处理</li>
 *   <li>进度追踪：记录处理状态（SUCCESS/FAILED/PROCESSING）</li>
 *   <li>错误诊断：保存失败原因</li>
 *   <li>统计分析：chunk数量、处理时间等</li>
 * </ul>
 *
 * <h3>业务流程</h3>
 * <ol>
 *   <li>用户上传PDF文档</li>
 *   <li>计算文件SHA256哈希值</li>
 *   <li>查询ingestion_history表检查是否已处理</li>
 *   <li>如果已处理且状态为SUCCESS，跳过</li>
 *   <li>否则创建PROCESSING记录开始摄取</li>
 *   <li>摄取完成后更新为SUCCESS或FAILED</li>
 * </ol>
 *
 * <h3>表结构</h3>
 * <pre>
 * CREATE TABLE ingestion_history (
 *     file_hash VARCHAR(64) PRIMARY KEY,    -- SHA256哈希值（64字符）
 *     file_path VARCHAR(512) NOT NULL,      -- 文件路径
 *     file_size BIGINT,                     -- 文件大小（字节）
 *     status VARCHAR(20),                   -- 处理状态
 *     processed_at TIMESTAMP,               -- 处理时间
 *     error_msg TEXT,                       -- 错误消息
 *     chunk_count INTEGER                   -- 生成的chunk数量
 * );
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
@Entity
@Table(name = "ingestion_history")
public class IngestionHistory {

    /**
     * 文件哈希值（SHA256，主键）
     *
     * <p>使用SHA256算法计算文件内容的哈希值，作为唯一标识。</p>
     *
     * <p>计算方式：</p>
     * <pre>{@code
     * MessageDigest digest = MessageDigest.getInstance("SHA-256");
     * byte[] hash = digest.digest(fileBytes);
     * String fileHash = Hex.encodeHexString(hash); // 64字符
     * }</pre>
     *
     * <p>特点：</p>
     * <ul>
     *   <li>同一文件内容必然产生相同哈希</li>
     *   <li>不同文件几乎不可能产生相同哈希</li>
     *   <li>文件改名不影响哈希值</li>
     * </ul>
     */
    @Id
    @Column(name = "file_hash", length = 64, nullable = false)
    private String fileHash;

    /**
     * 文件路径
     *
     * <p>文档的原始路径或上传时的文件名。</p>
     *
     * <p>示例：</p>
     * <ul>
     *   <li>/uploads/2025/01/产品手册.pdf</li>
     *   <li>C:\Documents\技术文档.pdf</li>
     *   <li>产品说明书_v2.0.pdf</li>
     * </ul>
     *
     * <p>注意：此字段仅用于记录和显示，业务逻辑应使用fileHash。</p>
     */
    @Column(name = "file_path", length = 512, nullable = false)
    private String filePath;

    /**
     * 文件大小（字节）
     *
     * <p>用于：</p>
     * <ul>
     *   <li>显示摄取统计</li>
     *   <li>判断是否超过限制</li>
     *   <li>估算处理时间</li>
     * </ul>
     *
     * <p>示例值：</p>
     * <ul>
     *   <li>1024 = 1KB</li>
     *   <li>1048576 = 1MB</li>
     *   <li>104857600 = 100MB</li>
     * </ul>
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * 处理状态
     *
     * <p>可选值：</p>
     * <ul>
     *   <li>PROCESSING：正在处理（摄取中）</li>
     *   <li>SUCCESS：处理成功</li>
     *   <li>FAILED：处理失败（查看errorMsg了解原因）</li>
     * </ul>
     *
     * <p>状态转换：</p>
     * <pre>
     * PROCESSING → SUCCESS（摄取成功）
     * PROCESSING → FAILED（摄取失败）
     * FAILED → PROCESSING（重新摄取）
     * </pre>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private IngestionStatus status;

    /**
     * 处理时间
     *
     * <p>记录摄取完成（成功或失败）的时间。</p>
     *
     * <p>注意：PROCESSING状态下此字段为null。</p>
     */
    @Column(name = "processed_at")
    private Instant processedAt;

    /**
     * 错误消息
     *
     * <p>当status为FAILED时，记录失败原因。</p>
     *
     * <p>常见错误：</p>
     * <ul>
     *   <li>PDF解析失败：文件损坏或加密</li>
     *   <li>Embedding生成失败：DashScope API调用超时</li>
     *   <li>Milvus插入失败：向量库连接异常</li>
     *   <li>文件过大：超过100MB限制</li>
     * </ul>
     *
     * <p>示例：</p>
     * <pre>
     * PDF解析失败: Unable to decrypt PDF, password required
     * Embedding生成超时: Read timed out after 60000ms
     * Milvus插入失败: Collection 'rag_knowledge_hub' does not exist
     * </pre>
     */
    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    /**
     * 生成的chunk数量
     *
     * <p>文档切分后生成的chunk总数。</p>
     *
     * <p>用于：</p>
     * <ul>
     *   <li>显示摄取统计</li>
     *   <li>验证摄取完整性</li>
     *   <li>评估文档复杂度</li>
     * </ul>
     *
     * <p>示例：</p>
     * <ul>
     *   <li>10页PDF，chunk_size=512 → 约20-30个chunk</li>
     *   <li>100页PDF → 约200-300个chunk</li>
     * </ul>
     *
     * <p>注意：PROCESSING或FAILED状态下此字段可能为null。</p>
     */
    @Column(name = "chunk_count")
    private Integer chunkCount;

    /**
     * Collection名称
     *
     * <p>文档所属的Collection，用于组织和分类文档。</p>
     *
     * <p>示例：</p>
     * <ul>
     *   <li>tech_docs - 技术文档</li>
     *   <li>product_manual - 产品手册</li>
     *   <li>rag_knowledge_hub - 默认知识库</li>
     * </ul>
     */
    @Column(name = "collection_name", length = 100)
    private String collectionName;

    /**
     * 摄取状态枚举
     *
     * <p>定义文档摄取的三种状态。</p>
     */
    public enum IngestionStatus {
        /**
         * 正在处理
         *
         * <p>文档正在进行摄取流程：</p>
         * <ol>
         *   <li>PDF解析</li>
         *   <li>文本提取</li>
         *   <li>语义切分</li>
         *   <li>Embedding生成</li>
         *   <li>Milvus插入</li>
         * </ol>
         */
        PROCESSING,

        /**
         * 处理成功
         *
         * <p>文档已成功摄取到向量库，可以被检索。</p>
         */
        SUCCESS,

        /**
         * 处理失败
         *
         * <p>摄取过程中发生错误，查看errorMsg字段了解原因。</p>
         */
        FAILED
    }
}
