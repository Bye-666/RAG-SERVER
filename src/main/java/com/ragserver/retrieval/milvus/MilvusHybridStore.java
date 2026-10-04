package com.ragserver.retrieval.milvus;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.ragserver.ai.dashscope.DashScopeEmbeddingClient;
import com.ragserver.config.MilvusProperties;
import com.ragserver.retrieval.BM25Encoder;
import com.ragserver.retrieval.RRFFusion;
import com.ragserver.retrieval.model.Document;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.data.SparseFloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

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
    private final DashScopeEmbeddingClient embeddingClient;
    private final BM25Encoder bm25Encoder;
    private final RRFFusion rrfFusion;

    /**
     * Schema常量
     */
    private static final String FIELD_ID = "id";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_DENSE_VECTOR = "dense_vector";
    private static final String FIELD_SPARSE_VECTOR = "sparse_vector";
    private static final String FIELD_METADATA = "metadata";

    public MilvusHybridStore(MilvusClientV2 milvusClient, MilvusProperties properties,
                             DashScopeEmbeddingClient embeddingClient, BM25Encoder bm25Encoder,
                             RRFFusion rrfFusion) {
        this.milvusClient = milvusClient;
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        this.bm25Encoder = bm25Encoder;
        this.rrfFusion = rrfFusion;
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

    /**
     * Dense向量检索（语义相似度检索）
     *
     * <p>基于查询文本的语义向量，在Milvus中检索最相似的文档。</p>
     *
     * <h3>工作流程</h3>
     * <ol>
     *   <li>将查询文本转换为2048维Dense向量（DashScope Embedding）</li>
     *   <li>在Milvus中搜索最相似的TopK个向量</li>
     *   <li>按余弦相似度排序返回文档</li>
     * </ol>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * List<Document> results = hybridStore.searchDense("什么是RAG技术", 10);
     * results.forEach(doc -> {
     *     System.out.println("得分: " + doc.getScore());
     *     System.out.println("文本: " + doc.getText());
     * });
     * }</pre>
     *
     * @param query 查询文本
     * @param topK 返回前K个最相似文档
     * @return 检索到的文档列表，按相似度降序排序
     */
    public List<Document> searchDense(String query, int topK) {
        log.debug("开始Dense检索：query={}, topK={}", query, topK);

        // 1. 将查询文本转换为向量
        List<Double> queryVectorDouble = embeddingClient.embed(query);

        // 转换为Float列表（Milvus要求）
        List<Float> queryVector = queryVectorDouble.stream()
            .map(Double::floatValue)
            .collect(Collectors.toList());

        log.debug("查询向量维度：{}", queryVector.size());

        // 2. 构建搜索参数
        SearchReq searchReq = SearchReq.builder()
            .collectionName(properties.getCollectionName())
            .data(Collections.singletonList(new FloatVec(queryVector)))
            .annsField(FIELD_DENSE_VECTOR)
            .topK(topK)
            .outputFields(Arrays.asList(FIELD_ID, FIELD_TEXT, FIELD_METADATA))
            .build();

        // 3. 执行搜索
        SearchResp searchResp = milvusClient.search(searchReq);

        // 4. 解析结果
        List<Document> documents = parseSearchResults(searchResp);

        log.info("Dense检索完成：返回{}个文档", documents.size());

        return documents;
    }

    /**
     * 插入文档
     *
     * <p>将文档及其向量插入Milvus Collection。</p>
     *
     * <h3>字段说明</h3>
     * <ul>
     *   <li>id：文档ID（必填）</li>
     *   <li>text：文档文本（必填）</li>
     *   <li>dense_vector：Dense向量（自动生成或手动提供）</li>
     *   <li>sparse_vector：Sparse向量（可选，用于BM25）</li>
     *   <li>metadata：元数据（可选）</li>
     * </ul>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * Document doc = Document.builder()
     *     .id("doc_001")
     *     .text("RAG是检索增强生成技术")
     *     .metadata(Map.of("source", "wiki"))
     *     .build();
     *
     * List<String> ids = hybridStore.insert(Collections.singletonList(doc));
     * System.out.println("插入成功：" + ids);
     * }</pre>
     *
     * @param documents 要插入的文档列表
     * @return 插入的文档ID列表
     */
    public List<String> insert(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("插入文档列表为空");
            return Collections.emptyList();
        }

        log.info("开始插入{}个文档", documents.size());

        // 1. 准备数据
        List<JsonObject> rows = new ArrayList<>();
        Gson gson = new Gson();

        for (Document doc : documents) {
            // 生成Dense向量（如果没有提供）
            List<Float> denseVector = doc.getDenseVector();
            if (denseVector == null || denseVector.isEmpty()) {
                // DashScope返回List<Double>，需要转换为List<Float>
                List<Double> vectorDouble = embeddingClient.embed(doc.getText());
                denseVector = vectorDouble.stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());
                doc.setDenseVector(denseVector);
            }

            // 生成Sparse向量（如果没有提供）
            Map<Integer, Float> sparseVector = doc.getSparseVector();
            if (sparseVector == null || sparseVector.isEmpty()) {
                // 使用BM25编码
                sparseVector = bm25Encoder.encode(doc.getText());
                doc.setSparseVector(sparseVector);
            }

            // 构建数据行（使用JsonObject）
            JsonObject row = new JsonObject();
            row.addProperty(FIELD_ID, doc.getId());
            row.addProperty(FIELD_TEXT, doc.getText());
            row.add(FIELD_DENSE_VECTOR, gson.toJsonTree(denseVector));

            // Sparse向量
            row.add(FIELD_SPARSE_VECTOR, gson.toJsonTree(sparseVector));

            // 元数据
            row.add(FIELD_METADATA, gson.toJsonTree(doc.getMetadata() != null ? doc.getMetadata() : Collections.emptyMap()));

            rows.add(row);
        }

        // 2. 插入数据
        InsertReq insertReq = InsertReq.builder()
            .collectionName(properties.getCollectionName())
            .data(rows)
            .build();

        InsertResp insertResp = milvusClient.insert(insertReq);

        log.info("插入完成：成功插入{}个文档", insertResp.getInsertCnt());

        // 3. 返回插入的ID
        return documents.stream()
            .map(Document::getId)
            .collect(Collectors.toList());
    }

    /**
     * 解析搜索结果
     *
     * @param searchResp Milvus搜索响应
     * @return 文档列表
     */
    private List<Document> parseSearchResults(SearchResp searchResp) {
        List<Document> documents = new ArrayList<>();

        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
        if (searchResults == null || searchResults.isEmpty()) {
            return documents;
        }

        // 取第一个查询的结果（单查询）
        List<SearchResp.SearchResult> results = searchResults.get(0);

        for (SearchResp.SearchResult result : results) {
            Map<String, Object> entity = result.getEntity();

            Document doc = Document.builder()
                .id((String) entity.get(FIELD_ID))
                .text((String) entity.get(FIELD_TEXT))
                .metadata((Map<String, Object>) entity.get(FIELD_METADATA))
                .score(result.getScore())
                .build();

            documents.add(doc);
        }

        return documents;
    }

    /**
     * Sparse向量检索（关键词检索）
     *
     * <p>基于BM25算法的关键词检索，适合精确匹配场景。</p>
     *
     * <h3>工作流程</h3>
     * <ol>
     *   <li>将查询文本编码为BM25稀疏向量</li>
     *   <li>在Milvus中搜索最匹配的TopK个文档</li>
     *   <li>按BM25分数排序返回文档</li>
     * </ol>
     *
     * <h3>适用场景</h3>
     * <ul>
     *   <li>精确关键词匹配</li>
     *   <li>专有名词检索</li>
     *   <li>代码片段检索</li>
     * </ul>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * List<Document> results = hybridStore.searchSparse("Milvus向量数据库", 10);
     * results.forEach(doc -> {
     *     System.out.println("得分: " + doc.getScore());
     *     System.out.println("文本: " + doc.getText());
     * });
     * }</pre>
     *
     * @param query 查询文本
     * @param topK 返回前K个最相关文档
     * @return 检索到的文档列表，按BM25分数降序排序
     */
    public List<Document> searchSparse(String query, int topK) {
        log.debug("开始Sparse检索：query={}, topK={}", query, topK);

        // 1. 将查询文本编码为稀疏向量
        Map<Integer, Float> querySparseVector = bm25Encoder.encode(query);

        if (querySparseVector.isEmpty()) {
            log.warn("查询编码为空，返回空结果");
            return Collections.emptyList();
        }

        log.debug("查询稀疏向量维度：{}", querySparseVector.size());

        // 2. 转换为SparseFloatVec格式（需要SortedMap<Long, Float>）
        SortedMap<Long, Float> sparseMap = new TreeMap<>();
        for (Map.Entry<Integer, Float> entry : querySparseVector.entrySet()) {
            sparseMap.put(entry.getKey().longValue(), entry.getValue());
        }
        SparseFloatVec sparseVec = new SparseFloatVec(sparseMap);

        // 3. 构建搜索参数
        SearchReq searchReq = SearchReq.builder()
            .collectionName(properties.getCollectionName())
            .data(Collections.singletonList(sparseVec))
            .annsField(FIELD_SPARSE_VECTOR)
            .topK(topK)
            .outputFields(Arrays.asList(FIELD_ID, FIELD_TEXT, FIELD_METADATA))
            .build();

        // 4. 执行搜索
        SearchResp searchResp = milvusClient.search(searchReq);

        // 5. 解析结果
        List<Document> documents = parseSearchResults(searchResp);

        log.info("Sparse检索完成：返回{}个文档", documents.size());

        return documents;
    }

    /**
     * 混合检索（Dense + Sparse + RRF融合）
     *
     * <p>结合语义检索和关键词检索，通过RRF算法融合结果。</p>
     *
     * <h3>工作流程</h3>
     * <ol>
     *   <li>并行执行Dense检索（语义相似度）</li>
     *   <li>并行执行Sparse检索（BM25关键词）</li>
     *   <li>使用RRF算法融合两路结果</li>
     *   <li>返回融合后的TopK文档</li>
     * </ol>
     *
     * <h3>优势</h3>
     * <ul>
     *   <li>兼顾语义理解和关键词匹配</li>
     *   <li>提高召回率和准确性</li>
     *   <li>对不同查询类型有更好的鲁棒性</li>
     * </ul>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 混合检索：既考虑语义，也考虑关键词
     * List<Document> results = hybridStore.searchHybrid("Milvus向量数据库", 10);
     *
     * results.forEach(doc -> {
     *     System.out.println("RRF分数: " + doc.getScore());
     *     System.out.println("文本: " + doc.getText());
     * });
     * }</pre>
     *
     * <h3>参数建议</h3>
     * <ul>
     *   <li>topK：建议10-20，平衡性能和效果</li>
     *   <li>retrievalTopK：建议是topK的2-3倍，增加融合候选</li>
     * </ul>
     *
     * @param query 查询文本
     * @param topK 最终返回的文档数量
     * @return 融合后的文档列表，按RRF分数降序排序
     */
    public List<Document> searchHybrid(String query, int topK) {
        return searchHybrid(query, topK, topK * 2);
    }

    /**
     * 混合检索（可配置检索数量）
     *
     * <p>允许单独控制每路检索的文档数量和最终返回数量。</p>
     *
     * @param query 查询文本
     * @param topK 最终返回的文档数量
     * @param retrievalTopK 每路检索的文档数量
     * @return 融合后的文档列表
     */
    public List<Document> searchHybrid(String query, int topK, int retrievalTopK) {
        log.info("开始混合检索：query={}, topK={}, retrievalTopK={}", query, topK, retrievalTopK);

        long startTime = System.currentTimeMillis();

        // 1. Dense检索（语义相似度）
        List<Document> denseResults = searchDense(query, retrievalTopK);
        log.debug("Dense检索完成：{}个文档", denseResults.size());

        // 2. Sparse检索（BM25关键词）
        List<Document> sparseResults = searchSparse(query, retrievalTopK);
        log.debug("Sparse检索完成：{}个文档", sparseResults.size());

        // 3. RRF融合
        List<Document> fusedResults = rrfFusion.fuseTwoWay(denseResults, sparseResults, topK);

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("混合检索完成：返回{}个文档，耗时{}ms", fusedResults.size(), elapsedTime);

        return fusedResults;
    }
}
