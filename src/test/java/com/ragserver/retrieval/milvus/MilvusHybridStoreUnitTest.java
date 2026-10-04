package com.ragserver.retrieval.milvus;

import com.ragserver.config.MilvusProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MilvusHybridStore单元测试
 *
 * <p>测试基本逻辑，不依赖真实的Milvus容器。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("MilvusHybridStore单元测试")
class MilvusHybridStoreUnitTest {

    @Test
    @DisplayName("应能创建MilvusProperties")
    void testMilvusProperties() {
        MilvusProperties properties = new MilvusProperties();
        properties.setUri("http://localhost:19530");
        properties.setCollectionName("test_collection");
        properties.setAutoCreateCollection(true);
        properties.setDenseVectorDimension(2048);

        assertEquals("http://localhost:19530", properties.getUri());
        assertEquals("test_collection", properties.getCollectionName());
        assertTrue(properties.getAutoCreateCollection());
        assertEquals(2048, properties.getDenseVectorDimension());
    }

    @Test
    @DisplayName("应能获取Collection名称")
    void testGetCollectionName() {
        MilvusProperties properties = new MilvusProperties();
        properties.setCollectionName("rag_knowledge_hub");

        assertEquals("rag_knowledge_hub", properties.getCollectionName());
    }

    @Test
    @DisplayName("应能配置Dense向量维度")
    void testDenseVectorDimension() {
        MilvusProperties properties = new MilvusProperties();

        // 默认值
        assertEquals(2048, properties.getDenseVectorDimension());

        // 自定义值
        properties.setDenseVectorDimension(1024);
        assertEquals(1024, properties.getDenseVectorDimension());
    }

    @Test
    @DisplayName("应能配置自动创建Collection")
    void testAutoCreateCollection() {
        MilvusProperties properties = new MilvusProperties();

        // 默认值
        assertTrue(properties.getAutoCreateCollection());

        // 关闭自动创建
        properties.setAutoCreateCollection(false);
        assertFalse(properties.getAutoCreateCollection());
    }

    @Test
    @DisplayName("Schema常量应该正确定义")
    void testSchemaConstants() {
        // 验证字段名（通过文档注释中的信息）
        // 这些常量在MilvusHybridStore中定义：
        // - id: VARCHAR(128, Primary Key)
        // - text: VARCHAR(65535)
        // - dense_vector: FLOAT_VECTOR(2048)
        // - sparse_vector: SPARSE_FLOAT_VECTOR
        // - metadata: JSON

        // 验证默认配置
        MilvusProperties properties = new MilvusProperties();
        assertEquals("rag_knowledge_hub", properties.getCollectionName());
        assertEquals("dense_vector", properties.getDenseField());
        assertEquals("sparse_vector", properties.getSparseField());
        assertEquals("COSINE", properties.getMetric());
    }

    @Test
    @DisplayName("应支持配置相似度度量方式")
    void testMetricConfiguration() {
        MilvusProperties properties = new MilvusProperties();

        // 默认COSINE
        assertEquals("COSINE", properties.getMetric());

        // 修改为L2
        properties.setMetric("L2");
        assertEquals("L2", properties.getMetric());

        // 修改为IP
        properties.setMetric("IP");
        assertEquals("IP", properties.getMetric());
    }

    @Test
    @DisplayName("应支持配置字段名")
    void testFieldNameConfiguration() {
        MilvusProperties properties = new MilvusProperties();

        // 默认字段名
        assertEquals("dense_vector", properties.getDenseField());
        assertEquals("sparse_vector", properties.getSparseField());

        // 自定义字段名
        properties.setDenseField("custom_dense");
        properties.setSparseField("custom_sparse");

        assertEquals("custom_dense", properties.getDenseField());
        assertEquals("custom_sparse", properties.getSparseField());
    }
}
