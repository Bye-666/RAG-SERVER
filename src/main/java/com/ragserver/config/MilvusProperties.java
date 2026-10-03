package com.ragserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Milvus配置属性类
 *
 * <p>绑定application.yaml中milvus开头的配置项。</p>
 *
 * <h3>配置项说明</h3>
 * <ul>
 *   <li>uri：Milvus服务地址（格式：http://host:port）</li>
 *   <li>collection-name：默认Collection名称</li>
 *   <li>auto-create-collection：是否自动创建Collection</li>
 *   <li>dense-field：Dense向量字段名</li>
 *   <li>sparse-field：Sparse向量字段名</li>
 *   <li>metric：相似度度量方式</li>
 * </ul>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * milvus:
 *   uri: http://localhost:19530
 *   collection-name: rag_knowledge_hub
 *   auto-create-collection: true
 *   metric: COSINE
 * }</pre>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * @Component
 * public class MilvusHybridStore {
 *     private final MilvusProperties properties;
 *
 *     public MilvusHybridStore(MilvusProperties properties) {
 *         this.properties = properties;
 *         // 初始化Milvus连接
 *         this.client = new MilvusServiceClient(
 *             ConnectParam.newBuilder()
 *                 .withUri(properties.getUri())
 *                 .build()
 *         );
 *     }
 * }
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    /**
     * Milvus服务地址
     *
     * <p>格式：http://host:port 或 https://host:port</p>
     *
     * <p>默认值：http://localhost:19530</p>
     *
     * <p>本地开发：</p>
     * <pre>
     * docker run -d --name milvus-standalone \
     *   -p 19530:19530 \
     *   -p 9091:9091 \
     *   milvusdb/milvus:v2.5.0
     * </pre>
     *
     * <p>生产环境建议从环境变量读取：</p>
     * <pre>
     * uri: ${MILVUS_URI:http://localhost:19530}
     * </pre>
     */
    private String uri = "http://localhost:19530";

    /**
     * Collection名称
     *
     * <p>默认值：rag_knowledge_hub</p>
     *
     * <p>命名规范：</p>
     * <ul>
     *   <li>只能包含字母、数字、下划线</li>
     *   <li>必须以字母或下划线开头</li>
     *   <li>长度不超过255个字符</li>
     * </ul>
     *
     * <p>不同环境建议使用不同Collection：</p>
     * <ul>
     *   <li>开发环境：rag_knowledge_hub_dev</li>
     *   <li>测试环境：rag_knowledge_hub_test</li>
     *   <li>生产环境：rag_knowledge_hub</li>
     * </ul>
     */
    private String collectionName = "rag_knowledge_hub";

    /**
     * 是否自动创建Collection
     *
     * <p>默认值：true</p>
     *
     * <p>开发/测试环境：建议设为true，便于快速启动</p>
     * <p>生产环境：建议设为false，手动创建和优化索引</p>
     *
     * <p>自动创建的Schema：</p>
     * <ul>
     *   <li>id: VarChar(128) - 主键</li>
     *   <li>text: VarChar(65535) - 原始文本</li>
     *   <li>dense_vector: FloatVector(2048) - Dense向量</li>
     *   <li>sparse_vector: SparseFloatVector - Sparse向量（BM25）</li>
     *   <li>metadata: JSON - 元数据</li>
     * </ul>
     */
    private Boolean autoCreateCollection = true;

    /**
     * Dense向量字段名
     *
     * <p>默认值：dense_vector</p>
     *
     * <p>存储从DashScope Embedding生成的2048维语义向量。</p>
     */
    private String denseField = "dense_vector";

    /**
     * Sparse向量字段名
     *
     * <p>默认值：sparse_vector</p>
     *
     * <p>存储BM25算法生成的稀疏向量，用于关键词匹配。</p>
     */
    private String sparseField = "sparse_vector";

    /**
     * 相似度度量方式
     *
     * <p>默认值：COSINE（余弦相似度）</p>
     *
     * <p>可选值：</p>
     * <ul>
     *   <li>COSINE：余弦相似度（推荐，适合归一化向量）</li>
     *   <li>L2：欧几里得距离</li>
     *   <li>IP：内积（适合已归一化的向量）</li>
     * </ul>
     *
     * <p>DashScope的text-embedding-v4已归一化，使用COSINE或IP都可以。</p>
     */
    private String metric = "COSINE";
}
