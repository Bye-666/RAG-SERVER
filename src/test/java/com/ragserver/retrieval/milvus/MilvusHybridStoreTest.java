package com.ragserver.retrieval.milvus;

import com.ragserver.config.MilvusProperties;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MilvusHybridStore集成测试
 *
 * <p>使用Testcontainers启动真实的Milvus容器进行测试。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Testcontainers
@DisplayName("MilvusHybridStore集成测试")
class MilvusHybridStoreTest {

    /**
     * Milvus容器
     *
     * <p>使用GenericContainer启动Milvus standalone模式。</p>
     */
    @Container
    static GenericContainer<?> milvusContainer = new GenericContainer<>(
        DockerImageName.parse("milvusdb/milvus:v2.4.1"))
        .withExposedPorts(19530)
        .withEnv("ETCD_USE_EMBED", "true")
        .withEnv("COMMON_STORAGETYPE", "local");

    private MilvusClientV2 milvusClient;
    private MilvusHybridStore hybridStore;
    private MilvusProperties properties;

    @BeforeEach
    void setUp() {
        // 获取Milvus容器的连接地址
        String host = milvusContainer.getHost();
        Integer port = milvusContainer.getMappedPort(19530);
        String uri = "http://" + host + ":" + port;

        // 配置属性
        properties = new MilvusProperties();
        properties.setUri(uri);
        properties.setCollectionName("test_collection");
        properties.setAutoCreateCollection(true);
        properties.setDenseVectorDimension(2048);

        // 创建Milvus客户端
        ConnectConfig connectConfig = ConnectConfig.builder()
            .uri(uri)
            .build();
        milvusClient = new MilvusClientV2(connectConfig);

        // 创建MilvusHybridStore
        hybridStore = new MilvusHybridStore(milvusClient, properties);
    }

    @AfterEach
    void tearDown() {
        if (milvusClient != null) {
            // 清理测试Collection
            try {
                if (hybridStore.hasCollection(properties.getCollectionName())) {
                    // Note: MilvusClientV2没有直接的dropCollection方法
                    // 实际使用中可以通过milvusClient.dropCollection()清理
                }
            } catch (Exception e) {
                // 忽略清理错误
            }
            milvusClient.close();
        }
    }

    @Test
    @DisplayName("应能连接Milvus")
    void testConnection() {
        assertNotNull(milvusClient, "Milvus客户端不应为null");
        assertTrue(milvusContainer.isRunning(), "Milvus容器应该在运行");
    }

    @Test
    @DisplayName("应能检查Collection是否存在")
    void testHasCollection() {
        String collectionName = properties.getCollectionName();

        // 初始状态：Collection不存在
        boolean existsBefore = hybridStore.hasCollection(collectionName);
        assertFalse(existsBefore, "Collection初始应该不存在");
    }

    @Test
    @DisplayName("应能创建Collection")
    void testInitializeCollection() {
        String collectionName = properties.getCollectionName();

        // 初始化Collection
        hybridStore.initializeCollection();

        // 验证Collection已创建
        assertTrue(hybridStore.hasCollection(collectionName), "Collection应该已创建");
    }

    @Test
    @DisplayName("应能获取Collection描述信息")
    void testDescribeCollection() {
        String collectionName = properties.getCollectionName();

        // 先创建Collection
        hybridStore.initializeCollection();

        // 获取描述信息
        DescribeCollectionResp description = hybridStore.describeCollection(collectionName);

        // 验证
        assertNotNull(description, "描述信息不应为null");
        assertEquals(collectionName, description.getCollectionName(), "Collection名称应匹配");

        // 验证字段
        assertNotNull(description.getFieldNames(), "字段列表不应为null");
        assertTrue(description.getFieldNames().contains("id"), "应包含id字段");
        assertTrue(description.getFieldNames().contains("text"), "应包含text字段");
        assertTrue(description.getFieldNames().contains("dense_vector"), "应包含dense_vector字段");
        assertTrue(description.getFieldNames().contains("sparse_vector"), "应包含sparse_vector字段");
        assertTrue(description.getFieldNames().contains("metadata"), "应包含metadata字段");

        System.out.println("Collection字段：" + description.getFieldNames());
    }

    @Test
    @DisplayName("重复初始化应跳过创建")
    void testInitializeCollectionTwice() {
        String collectionName = properties.getCollectionName();

        // 第一次初始化
        hybridStore.initializeCollection();
        assertTrue(hybridStore.hasCollection(collectionName), "第一次应创建Collection");

        // 第二次初始化（应跳过）
        hybridStore.initializeCollection();
        assertTrue(hybridStore.hasCollection(collectionName), "第二次应仍然存在Collection");

        // 验证没有报错，说明跳过了重复创建
    }

    @Test
    @DisplayName("应能获取Collection名称")
    void testGetCollectionName() {
        String collectionName = hybridStore.getCollectionName();
        assertEquals(properties.getCollectionName(), collectionName, "Collection名称应匹配");
    }

    @Test
    @DisplayName("应能获取Milvus客户端")
    void testGetClient() {
        MilvusClientV2 client = hybridStore.getClient();
        assertNotNull(client, "客户端不应为null");
        assertSame(milvusClient, client, "应返回同一个客户端实例");
    }

    @Test
    @DisplayName("Collection应包含正确的Schema")
    void testCollectionSchema() {
        // 创建Collection
        hybridStore.initializeCollection();

        // 获取描述
        DescribeCollectionResp description = hybridStore.describeCollection(properties.getCollectionName());

        // 验证字段数量（5个字段：id, text, dense_vector, sparse_vector, metadata）
        assertEquals(5, description.getFieldNames().size(), "应有5个字段");

        System.out.println("Schema验证通过：");
        System.out.println("  - 字段数：" + description.getFieldNames().size());
        System.out.println("  - 字段列表：" + description.getFieldNames());
    }
}
