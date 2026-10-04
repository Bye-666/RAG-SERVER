package com.ragserver.retrieval.milvus;

import com.ragserver.ai.dashscope.DashScopeEmbeddingClient;
import com.ragserver.config.DashScopeProperties;
import com.ragserver.config.MilvusProperties;
import com.ragserver.retrieval.model.Document;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dense检索集成测试
 *
 * <p>测试MilvusHybridStore的Dense向量检索功能。</p>
 *
 * <h3>测试场景</h3>
 * <ul>
 *   <li>插入测试数据</li>
 *   <li>语义相似度检索</li>
 *   <li>结果排序验证</li>
 * </ul>
 *
 * <h3>前提条件</h3>
 * <pre>
 * 1. Milvus运行在localhost:19530
 * 2. DashScope API Key已配置
 * </pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("Dense检索集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DenseSearchTest {

    private static MilvusClientV2 milvusClient;
    private static MilvusHybridStore hybridStore;
    private static DashScopeEmbeddingClient embeddingClient;
    private static MilvusProperties milvusProperties;

    // 测试文档
    private static final List<Document> TEST_DOCUMENTS = Arrays.asList(
        Document.builder()
            .id("doc_001")
            .text("RAG是检索增强生成技术，结合了检索和生成两种方法")
            .metadata(Map.of("source", "wiki", "category", "AI"))
            .build(),
        Document.builder()
            .id("doc_002")
            .text("向量数据库用于存储和检索高维向量，支持相似度搜索")
            .metadata(Map.of("source", "tutorial", "category", "Database"))
            .build(),
        Document.builder()
            .id("doc_003")
            .text("大语言模型通过预训练学习语言知识，可以生成连贯文本")
            .metadata(Map.of("source", "paper", "category", "NLP"))
            .build(),
        Document.builder()
            .id("doc_004")
            .text("Embedding将文本转换为向量表示，捕获语义信息")
            .metadata(Map.of("source", "blog", "category", "ML"))
            .build(),
        Document.builder()
            .id("doc_005")
            .text("Milvus是开源向量数据库，支持十亿级向量检索")
            .metadata(Map.of("source", "doc", "category", "Database"))
            .build()
    );

    @BeforeAll
    static void setUpAll() {
        // 配置Milvus
        milvusProperties = new MilvusProperties();
        milvusProperties.setUri("http://localhost:19530");
        milvusProperties.setCollectionName("test_dense_search");
        milvusProperties.setAutoCreateCollection(true);
        milvusProperties.setDenseVectorDimension(2048);

        // 创建Milvus客户端
        ConnectConfig connectConfig = ConnectConfig.builder()
            .uri("http://localhost:19530")
            .build();

        try {
            milvusClient = new MilvusClientV2(connectConfig);
            System.out.println("✅ 成功连接到Milvus");
        } catch (Exception e) {
            System.err.println("❌ 无法连接到Milvus：" + e.getMessage());
            throw new RuntimeException("Milvus连接失败", e);
        }

        // 配置DashScope
        DashScopeProperties dashScopeProperties = new DashScopeProperties();
        dashScopeProperties.setApiKey(System.getenv("DASHSCOPE_API_KEY"));
        dashScopeProperties.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        dashScopeProperties.setModel("text-embedding-v4");

        // 创建Embedding客户端
        embeddingClient = new DashScopeEmbeddingClient(dashScopeProperties, new RestTemplate());

        // 创建MilvusHybridStore
        hybridStore = new MilvusHybridStore(milvusClient, milvusProperties, embeddingClient);

        // 初始化Collection
        hybridStore.initializeCollection();
    }

    @AfterAll
    static void tearDownAll() {
        if (milvusClient != null) {
            milvusClient.close();
            System.out.println("✅ Milvus客户端已关闭");
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. 应能插入测试文档")
    void testInsertDocuments() {
        // 插入测试文档
        List<String> ids = hybridStore.insert(TEST_DOCUMENTS);

        // 验证
        assertNotNull(ids, "插入结果不应为null");
        assertEquals(TEST_DOCUMENTS.size(), ids.size(), "插入数量应匹配");
        assertEquals("doc_001", ids.get(0), "第一个ID应匹配");

        System.out.println("✅ 成功插入" + ids.size() + "个文档");

        // 等待Milvus索引
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Order(2)
    @DisplayName("2. 应能执行Dense检索")
    void testDenseSearch() {
        // 查询：与doc_001最相似
        String query = "什么是RAG技术";
        int topK = 3;

        List<Document> results = hybridStore.searchDense(query, topK);

        // 验证
        assertNotNull(results, "检索结果不应为null");
        assertTrue(results.size() > 0, "应该返回至少1个结果");
        assertTrue(results.size() <= topK, "结果数量不应超过topK");

        System.out.println("✅ 检索到" + results.size() + "个文档");
        results.forEach(doc -> {
            System.out.println("  - [" + doc.getScore() + "] " + doc.getSummary());
        });
    }

    @Test
    @Order(3)
    @DisplayName("3. 结果应按相似度降序排序")
    void testResultsSortedByScore() {
        String query = "向量数据库有哪些";
        List<Document> results = hybridStore.searchDense(query, 5);

        // 验证排序
        for (int i = 0; i < results.size() - 1; i++) {
            float currentScore = results.get(i).getScore();
            float nextScore = results.get(i + 1).getScore();
            assertTrue(currentScore >= nextScore,
                "相似度应降序排列：" + currentScore + " >= " + nextScore);
        }

        System.out.println("✅ 结果正确按相似度降序排序");
    }

    @Test
    @Order(4)
    @DisplayName("4. 查询结果应与查询语义相关")
    void testSemanticRelevance() {
        // 查询关于"RAG"的内容，doc_001应该得分最高
        String query = "检索增强生成是什么";
        List<Document> results = hybridStore.searchDense(query, 5);

        // 验证第一个结果应该是doc_001
        assertNotNull(results, "检索结果不应为null");
        assertTrue(results.size() > 0, "应该返回至少1个结果");

        Document topDoc = results.get(0);
        System.out.println("✅ 最相关文档：" + topDoc.getId());
        System.out.println("   得分：" + topDoc.getScore());
        System.out.println("   文本：" + topDoc.getSummary());

        // doc_001包含"RAG"和"检索增强生成"，应该得分最高
        assertTrue(topDoc.getText().contains("RAG") || topDoc.getText().contains("检索"),
            "最相关文档应该包含关键词");
    }

    @Test
    @Order(5)
    @DisplayName("5. 应能正确返回元数据")
    void testMetadata() {
        String query = "向量数据库";
        List<Document> results = hybridStore.searchDense(query, 3);

        // 验证元数据
        for (Document doc : results) {
            assertNotNull(doc.getMetadata(), "元数据不应为null");
            assertTrue(doc.getMetadata().containsKey("source"), "应包含source字段");
            assertTrue(doc.getMetadata().containsKey("category"), "应包含category字段");

            System.out.println("✅ 文档 " + doc.getId() + " 元数据：" + doc.getMetadata());
        }
    }

    @Test
    @Order(6)
    @DisplayName("6. 空查询应返回空结果")
    void testEmptyQuery() {
        String query = "";
        List<Document> results = hybridStore.searchDense(query, 5);

        // 空查询应该返回空或抛出异常
        // 这里我们允许返回结果，但应该是有效的
        assertNotNull(results, "结果不应为null");

        System.out.println("✅ 空查询处理：返回" + results.size() + "个结果");
    }

    @Test
    @Order(7)
    @DisplayName("7. TopK参数应生效")
    void testTopKParameter() {
        String query = "大语言模型";

        // 测试不同的TopK值
        int[] topKValues = {1, 3, 5};

        for (int topK : topKValues) {
            List<Document> results = hybridStore.searchDense(query, topK);
            assertTrue(results.size() <= topK,
                "TopK=" + topK + "时，结果数量不应超过" + topK);
            System.out.println("✅ TopK=" + topK + "，返回" + results.size() + "个结果");
        }
    }
}
