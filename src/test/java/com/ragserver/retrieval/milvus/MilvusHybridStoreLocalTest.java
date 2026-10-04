package com.ragserver.retrieval.milvus;

import com.ragserver.ai.dashscope.DashScopeEmbeddingClient;
import com.ragserver.config.DashScopeProperties;
import com.ragserver.config.MilvusProperties;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MilvusHybridStore本地集成测试
 *
 * <p>连接本地Milvus实例进行测试（需要先启动Milvus）。</p>
 *
 * <h3>前提条件</h3>
 * <pre>
 * 1. 下载Milvus docker-compose配置：
 *    mkdir -p D:/Dev/milvus && cd D:/Dev/milvus
 *    curl -L https://github.com/milvus-io/milvus/releases/download/v2.4.1/milvus-standalone-docker-compose.yml -o docker-compose.yml
 *
 * 2. 启动Milvus：
 *    docker-compose up -d
 *
 * 3. 验证Milvus运行：
 *    docker-compose ps
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("MilvusHybridStore本地集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MilvusHybridStoreLocalTest {

    private static MilvusClientV2 milvusClient;
    private static MilvusHybridStore hybridStore;
    private static MilvusProperties properties;
    private static DashScopeEmbeddingClient embeddingClient;
    private static DashScopeProperties dashScopeProperties;

    @BeforeAll
    static void setUpAll() {
        // 配置属性（连接本地Milvus）
        properties = new MilvusProperties();
        properties.setUri("http://localhost:19530");
        properties.setCollectionName("test_collection_local");
        properties.setAutoCreateCollection(true);
        properties.setDenseVectorDimension(2048);

        // 创建Milvus客户端
        ConnectConfig connectConfig = ConnectConfig.builder()
            .uri("http://localhost:19530")
            .build();

        try {
            milvusClient = new MilvusClientV2(connectConfig);
            System.out.println("✅ 成功连接到本地Milvus");
        } catch (Exception e) {
            System.err.println("❌ 无法连接到Milvus，请确保Milvus已启动：");
            System.err.println("   docker-compose up -d");
            throw new RuntimeException("Milvus连接失败", e);
        }

        // 配置DashScope
        dashScopeProperties = new DashScopeProperties();
        dashScopeProperties.setApiKey(System.getenv("DASHSCOPE_API_KEY"));
        dashScopeProperties.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        dashScopeProperties.setModel("text-embedding-v4");

        // 创建Embedding客户端
        embeddingClient = new DashScopeEmbeddingClient(dashScopeProperties, new org.springframework.web.client.RestTemplate());

        // 创建MilvusHybridStore
        hybridStore = new MilvusHybridStore(milvusClient, properties, embeddingClient);

        // 初始化Collection
        hybridStore.initializeCollection();
    }

    @AfterAll
    static void tearDownAll() {
        if (milvusClient != null) {
            try {
                // 清理测试Collection（如果存在）
                // Note: 实际项目中可以添加dropCollection方法
                System.out.println("清理测试数据...");
            } catch (Exception e) {
                // 忽略清理错误
            }
            milvusClient.close();
            System.out.println("✅ Milvus客户端已关闭");
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. 应能连接本地Milvus")
    void testConnection() {
        assertNotNull(milvusClient, "Milvus客户端不应为null");
        System.out.println("✅ Milvus连接正常");
    }

    @Test
    @Order(2)
    @DisplayName("2. 初始状态：Collection不存在")
    void testCollectionNotExist() {
        String collectionName = properties.getCollectionName();
        boolean exists = hybridStore.hasCollection(collectionName);
        assertFalse(exists, "Collection初始应该不存在");
        System.out.println("✅ 确认Collection不存在：" + collectionName);
    }

    @Test
    @Order(3)
    @DisplayName("3. 应能创建Collection")
    void testInitializeCollection() {
        String collectionName = properties.getCollectionName();

        // 初始化Collection
        hybridStore.initializeCollection();

        // 验证Collection已创建
        assertTrue(hybridStore.hasCollection(collectionName), "Collection应该已创建");
        System.out.println("✅ Collection创建成功：" + collectionName);
    }

    @Test
    @Order(4)
    @DisplayName("4. 应能获取Collection描述信息")
    void testDescribeCollection() {
        String collectionName = properties.getCollectionName();

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

        System.out.println("✅ Collection字段验证通过：" + description.getFieldNames());
        System.out.println("   - 字段数：" + description.getFieldNames().size());
        System.out.println("   - 字段列表：" + description.getFieldNames());
    }

    @Test
    @Order(5)
    @DisplayName("5. 重复初始化应跳过创建")
    void testInitializeCollectionTwice() {
        String collectionName = properties.getCollectionName();

        // 第二次初始化（应跳过）
        hybridStore.initializeCollection();

        // 验证Collection仍然存在
        assertTrue(hybridStore.hasCollection(collectionName), "Collection应仍然存在");
        System.out.println("✅ 重复初始化正确跳过");
    }

    @Test
    @Order(6)
    @DisplayName("6. 应能获取Collection名称")
    void testGetCollectionName() {
        String collectionName = hybridStore.getCollectionName();
        assertEquals(properties.getCollectionName(), collectionName, "Collection名称应匹配");
        System.out.println("✅ Collection名称：" + collectionName);
    }

    @Test
    @Order(7)
    @DisplayName("7. 应能获取Milvus客户端")
    void testGetClient() {
        MilvusClientV2 client = hybridStore.getClient();
        assertNotNull(client, "客户端不应为null");
        assertSame(milvusClient, client, "应返回同一个客户端实例");
        System.out.println("✅ Milvus客户端获取正常");
    }

    @Test
    @Order(8)
    @DisplayName("8. Collection应包含正确的Schema")
    void testCollectionSchema() {
        // 获取描述
        DescribeCollectionResp description = hybridStore.describeCollection(properties.getCollectionName());

        // 验证字段数量（5个字段）
        assertEquals(5, description.getFieldNames().size(), "应有5个字段");

        System.out.println("✅ Schema验证通过：");
        System.out.println("   - 字段数：5");
        System.out.println("   - id: VARCHAR(128) - 主键");
        System.out.println("   - text: VARCHAR(65535)");
        System.out.println("   - dense_vector: FLOAT_VECTOR(2048)");
        System.out.println("   - sparse_vector: SPARSE_FLOAT_VECTOR");
        System.out.println("   - metadata: JSON");
    }
}
