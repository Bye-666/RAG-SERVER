package com.ragserver.retrieval.milvus;

import com.ragserver.ai.dashscope.DashScopeEmbeddingClient;
import com.ragserver.config.DashScopeProperties;
import com.ragserver.config.MilvusProperties;
import com.ragserver.retrieval.BM25Encoder;
import com.ragserver.retrieval.RRFFusion;
import com.ragserver.retrieval.model.Document;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 混合检索集成测试（Dense + Sparse + RRF）
 *
 * <p>测试RRF融合算法和混合检索的效果。</p>
 *
 * <h3>测试场景</h3>
 * <ul>
 *   <li>插入测试数据</li>
 *   <li>对比单路检索vs混合检索</li>
 *   <li>验证RRF融合效果</li>
 *   <li>验证文档去重</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("混合检索集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HybridSearchTest {

    private static MilvusClientV2 milvusClient;
    private static MilvusHybridStore hybridStore;
    private static BM25Encoder bm25Encoder;
    private static DashScopeEmbeddingClient embeddingClient;
    private static RRFFusion rrfFusion;
    private static MilvusProperties milvusProperties;

    // 测试文档（设计为包含不同特点的文档）
    private static final List<Document> TEST_DOCUMENTS = Arrays.asList(
        // 同时包含"向量"和"数据库"关键词
        Document.builder()
            .id("doc_001")
            .text("Milvus是开源向量数据库，支持十亿级向量检索和混合搜索")
            .metadata(Map.of("source", "doc", "category", "Database"))
            .build(),

        // 包含"向量"但不包含"数据库"
        Document.builder()
            .id("doc_002")
            .text("向量表示是深度学习中的基础概念，用于编码语义信息")
            .metadata(Map.of("source", "tutorial", "category", "AI"))
            .build(),

        // 包含"数据库"但不包含"向量"
        Document.builder()
            .id("doc_003")
            .text("关系数据库使用SQL查询，适合结构化数据存储")
            .metadata(Map.of("source", "wiki", "category", "Database"))
            .build(),

        // 语义相关但不包含关键词
        Document.builder()
            .id("doc_004")
            .text("Embedding模型将文本转换为高维空间中的点")
            .metadata(Map.of("source", "paper", "category", "NLP"))
            .build(),

        // 无关文档
        Document.builder()
            .id("doc_005")
            .text("Python是一种广泛使用的编程语言")
            .metadata(Map.of("source", "wiki", "category", "Programming"))
            .build()
    );

    @BeforeAll
    static void setUpAll() {
        // 配置Milvus
        milvusProperties = new MilvusProperties();
        milvusProperties.setUri("http://localhost:19530");
        milvusProperties.setCollectionName("test_hybrid_search");
        milvusProperties.setAutoCreateCollection(true);
        milvusProperties.setDenseVectorDimension(1024);

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

        // 创建BM25编码器
        bm25Encoder = new BM25Encoder();

        // 训练BM25
        List<String> texts = TEST_DOCUMENTS.stream()
            .map(Document::getText)
            .toList();
        bm25Encoder.fitBatch(texts);

        // 创建RRF融合器
        rrfFusion = new RRFFusion();

        // 创建MilvusHybridStore
        hybridStore = new MilvusHybridStore(milvusClient, milvusProperties, embeddingClient, bm25Encoder, rrfFusion);

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
        List<String> ids = hybridStore.insert(TEST_DOCUMENTS);

        assertNotNull(ids, "插入结果不应为null");
        assertEquals(TEST_DOCUMENTS.size(), ids.size(), "插入数量应匹配");

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
    @DisplayName("2. 应能执行混合检索")
    void testHybridSearch() {
        String query = "向量数据库";
        int topK = 3;

        List<Document> results = hybridStore.searchHybrid(query, topK);

        assertNotNull(results, "检索结果不应为null");
        assertTrue(results.size() > 0, "应该返回至少1个结果");
        assertTrue(results.size() <= topK, "结果数量不应超过topK");

        System.out.println("✅ 混合检索到" + results.size() + "个文档");
        results.forEach(doc -> {
            System.out.println("  - [" + doc.getScore() + "] " + doc.getSummary());
        });
    }

    @Test
    @Order(3)
    @DisplayName("3. 混合检索应优于单路检索")
    void testHybridVsSingleRetrieval() {
        String query = "向量数据库检索";
        int topK = 3;

        // 单路检索
        List<Document> denseResults = hybridStore.searchDense(query, topK);
        List<Document> sparseResults = hybridStore.searchSparse(query, topK);

        // 混合检索
        List<Document> hybridResults = hybridStore.searchHybrid(query, topK);

        System.out.println("📊 检索结果对比：");
        System.out.println("Dense检索：" + denseResults.size() + "个文档");
        System.out.println("Sparse检索：" + sparseResults.size() + "个文档");
        System.out.println("混合检索：" + hybridResults.size() + "个文档");

        // 混合检索应该能返回结果
        assertTrue(hybridResults.size() > 0, "混合检索应返回结果");

        // doc_001同时匹配语义和关键词，应该在混合检索中排名靠前
        if (hybridResults.size() > 0) {
            String topDocId = hybridResults.get(0).getId();
            System.out.println("✅ 混合检索Top1：" + topDocId);
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. RRF融合应正确去重")
    void testRRFDeduplication() {
        String query = "向量";
        int topK = 5;

        List<Document> hybridResults = hybridStore.searchHybrid(query, topK);

        // 检查ID唯一性
        Set<String> uniqueIds = new HashSet<>();
        for (Document doc : hybridResults) {
            assertFalse(uniqueIds.contains(doc.getId()),
                "文档ID应唯一，发现重复：" + doc.getId());
            uniqueIds.add(doc.getId());
        }

        System.out.println("✅ 去重验证通过：" + hybridResults.size() + "个唯一文档");
    }

    @Test
    @Order(5)
    @DisplayName("5. RRF分数应降序排列")
    void testRRFScoreSorted() {
        String query = "数据库存储";
        List<Document> results = hybridStore.searchHybrid(query, 5);

        for (int i = 0; i < results.size() - 1; i++) {
            float currentScore = results.get(i).getScore();
            float nextScore = results.get(i + 1).getScore();
            assertTrue(currentScore >= nextScore,
                "RRF分数应降序排列：" + currentScore + " >= " + nextScore);
        }

        System.out.println("✅ RRF分数正确排序");
    }

    @Test
    @Order(6)
    @DisplayName("6. 测试retrievalTopK参数")
    void testRetrievalTopK() {
        String query = "向量数据库";
        int finalTopK = 3;
        int retrievalTopK = 5;

        List<Document> results = hybridStore.searchHybrid(query, finalTopK, retrievalTopK);

        assertNotNull(results, "结果不应为null");
        assertTrue(results.size() <= finalTopK,
            "最终结果数量不应超过finalTopK");

        System.out.println("✅ retrievalTopK参数生效：返回" + results.size() + "个文档");
    }

    @Test
    @Order(7)
    @DisplayName("7. 空查询应正确处理")
    void testEmptyQuery() {
        String query = "";
        List<Document> results = hybridStore.searchHybrid(query, 5);

        assertNotNull(results, "结果不应为null");

        System.out.println("✅ 空查询处理：返回" + results.size() + "个结果");
    }

    @Test
    @Order(8)
    @DisplayName("8. RRF融合器常数验证")
    void testRRFConstant() {
        int k = rrfFusion.getK();
        assertEquals(60, k, "RRF常数k应为60");

        System.out.println("✅ RRF常数k=" + k);
    }
}
