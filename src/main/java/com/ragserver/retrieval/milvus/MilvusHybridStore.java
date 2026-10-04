package com.ragserver.retrieval.milvus;

import com.ragserver.config.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Milvus混合检索存储
 *
 * <p>封装Milvus向量数据库操作，支持：</p>
 * <ul>
 *   <li>Collection管理（创建、删除、查询）</li>
 *   <li>Dense向量检索（语义相似度）</li>
 *   <li>Sparse向量检索（关键词匹配）</li>
 *   <li>Hybrid检索（Dense + Sparse融合）</li>
 * </ul>
 *
 * <h3>Schema设计</h3>
 * <pre>
 * Collection: rag_knowledge_hub
 * Fields:
 *   - id: VARCHAR(128, Primary Key)           # 文档唯一标识
 *   - text: VARCHAR(65535)                    # 原始文本
 *   - dense_vector: FLOAT_VECTOR(2048)        # 密集向量（语义）
 *   - sparse_vector: SPARSE_FLOAT_VECTOR      # 稀疏向量（关键词）
 *   - metadata: JSON                          # 元数据（文件名、页码等）
 * Indexes:
 *   - dense_vector: AUTOINDEX / COSINE
 *   - sparse_vector: SPARSE_INVERTED_INDEX / IP
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class MilvusHybridStore {

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties properties;

    /**
     * Schema常量
     */
    private static final String FIELD_ID = "id";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_DENSE_VECTOR = "dense_vector";
    private static final String FIELD_SPARSE_VECTOR = "sparse_vector";
    private static final String FIELD_METADATA = "metadata";

    public MilvusHybridStore(MilvusClientV2 milvusClient, MilvusProperties properties) {
        this.milvusClient = milvusClient;
        this.properties = properties;
    }

    /**
     * 初始化Collection
     *
     * <p>如果Collection不存在则创建，存在则跳过。</p>
     */
    public void initializeCollection() {
        String collectionName = properties.getCollectionName();

        // 检查Collection是否存在
        if (hasCollection(collectionName)) {
            log.info("Collection已存在，跳过创建：{}", collectionName);

            // 打印Collection信息
            DescribeCollectionResp description = describeCollection(collectionName);
            log.info("Collection信息：字段数={}",
                description.getFieldNames().size());

            return;
        }

        log.info("创建Collection：{}", collectionName);
        createCollection(collectionName);
        log.info("Collection创建成功：{}", collectionName);
    }

    /**
     * 检查Collection是否存在
     *
     * @param collectionName Collection名称
     * @return 是否存在
     */
    public boolean hasCollection(String collectionName) {
        HasCollectionReq request = HasCollectionReq.builder()
            .collectionName(collectionName)
            .build();

        Boolean exists = milvusClient.hasCollection(request);
        return exists != null && exists;
    }

    /**
     * 查询Collection描述信息
     *
     * @param collectionName Collection名称
     * @return Collection描述
     */
    public DescribeCollectionResp describeCollection(String collectionName) {
        DescribeCollectionReq request = DescribeCollectionReq.builder()
            .collectionName(collectionName)
            .build();

        return milvusClient.describeCollection(request);
    }

    /**
     * 创建Collection
     *
     * <p>创建包含Dense向量、Sparse向量和元数据的Collection。</p>
     *
     * @param collectionName Collection名称
     */
    private void createCollection(String collectionName) {
        // 定义Schema
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
            .build();

        // 添加字段
        List<CreateCollectionReq.FieldSchema> fields = new ArrayList<>();

        // 1. ID字段（主键）
        fields.add(CreateCollectionReq.FieldSchema.builder()
            .name(FIELD_ID)
            .dataType(DataType.VarChar)
            .maxLength(128)
            .isPrimaryKey(true)
            .autoID(false) // 手动指定ID
            .build());

        // 2. 文本字段
        fields.add(CreateCollectionReq.FieldSchema.builder()
            .name(FIELD_TEXT)
            .dataType(DataType.VarChar)
            .maxLength(65535) // Milvus最大支持65535字符
            .build());

        // 3. Dense向量字段（语义向量）
        fields.add(CreateCollectionReq.FieldSchema.builder()
            .name(FIELD_DENSE_VECTOR)
            .dataType(DataType.FloatVector)
            .dimension(properties.getDenseVectorDimension()) // 2048维
            .build());

        // 4. Sparse向量字段（关键词向量）
        fields.add(CreateCollectionReq.FieldSchema.builder()
            .name(FIELD_SPARSE_VECTOR)
            .dataType(DataType.SparseFloatVector)
            .build());

        // 5. 元数据字段（JSON）
        fields.add(CreateCollectionReq.FieldSchema.builder()
            .name(FIELD_METADATA)
            .dataType(DataType.JSON)
            .build());

        schema.setFieldSchemaList(fields);

        // 定义索引
        List<IndexParam> indexes = new ArrayList<>();

        // Dense向量索引（AUTOINDEX + COSINE相似度）
        IndexParam denseIndex = IndexParam.builder()
            .fieldName(FIELD_DENSE_VECTOR)
            .indexType(IndexParam.IndexType.AUTOINDEX) // 自动索引
            .metricType(IndexParam.MetricType.COSINE)  // 余弦相似度
            .build();
        indexes.add(denseIndex);

        // Sparse向量索引（SPARSE_INVERTED_INDEX + IP相似度）
        IndexParam sparseIndex = IndexParam.builder()
            .fieldName(FIELD_SPARSE_VECTOR)
            .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX) // 稀疏倒排索引
            .metricType(IndexParam.MetricType.IP)                   // 内积相似度
            .build();
        indexes.add(sparseIndex);

        // 创建Collection请求
        CreateCollectionReq request = CreateCollectionReq.builder()
            .collectionName(collectionName)
            .collectionSchema(schema)
            .indexParams(indexes)
            .consistencyLevel(ConsistencyLevel.BOUNDED) // 有界一致性（平衡性能和一致性）
            .build();

        // 执行创建
        milvusClient.createCollection(request);

        log.info("Collection创建完成：{}", collectionName);
        log.info("  - 字段：id, text, dense_vector({}D), sparse_vector, metadata",
            properties.getDenseVectorDimension());
        log.info("  - 索引：dense_vector(AUTOINDEX/COSINE), sparse_vector(SPARSE_INVERTED_INDEX/IP)");
    }

    /**
     * 获取Collection名称
     *
     * @return Collection名称
     */
    public String getCollectionName() {
        return properties.getCollectionName();
    }

    /**
     * 获取Milvus客户端
     *
     * @return MilvusClientV2实例
     */
    public MilvusClientV2 getClient() {
        return milvusClient;
    }
}
