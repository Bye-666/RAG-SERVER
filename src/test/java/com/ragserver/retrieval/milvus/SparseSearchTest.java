package com.ragserver.retrieval.milvus;

import com.ragserver.ai.dashscope.DashScopeEmbeddingClient;
import com.ragserver.config.DashScopeProperties;
import com.ragserver.config.MilvusProperties;
import com.ragserver.retrieval.BM25Encoder;
import com.ragserver.retrieval.model.Document;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sparse检索集成测试
 *
 * <p>测试MilvusHybridStore的Sparse向量检索功能（BM25）。</p>
 *
 * <h3>测试场景</h3>
 * <ul>
 *   <li>插入测试数据（自动生成Sparse向量）</li>
 *   <li>关键词检索</li>
 *   <li>结果排序验证</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("Sparse检索集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SparseSearchTest {

    private static MilvusClientV2 milvusClient;
    private static MilvusHybridStore hybridStore;
    private static BM25Encoder bm25Encoder;
    private static DashScopeEmbeddingClient embeddingClient;
    private static MilvusProperties milvusProperties;

    // 测试文档（包含明显关键词）
    private static final List<Document> TEST_DOCUMENTS = Arrays.asList(
        Document.builder()
            .id("doc_001")
            .text("Milvus是开源向量数据库，支持十亿级向量检索")
            .metadata(Map.of("source", "doc", "category", "Database"))
            .build(),
        Document.builder()
            .id("doc_002")
            .text("向量数据库用于存储和检索高维向量数据")
            .metadata(Map.of("source", "tutorial", "category", "Database"))
            .build(),
        Document.builder()
            .id("doc_003")
            .text("Python是一种广泛使用的编程语言")
            .metadata(Map.of("source", "wiki", "category", "Programming"))
            .build(),
        Document.builder()
            .id("doc_004")
            .text("机器学习算法需要大量训练数据")
            .metadata(Map.of("source", "paper", "category", "ML"))
            .build(),
        Document.builder()
            .id("doc_005")
            .text("数据库索引可以加速查询性能")
            .metadata(Map.of("source", "blog", "category", "Database"))
            .build()
    );

    @BeforeAll
    static void setUpAll() {
        // 配置Milvus
        milvusProperties = new MilvusProperties();
        milvusProperties.setUri("http://localhost:19530");
        milvusProperties.setCollectionName("test_sparse_search");
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

        // 创建BM25编码器
        bm25Encoder = new BM25Encoder();

        // 训练BM25（使用测试文档）
        List<String> texts = TEST_DOCUMENTS.stream()
            .map(Document::getText)
            .toList();
        bm25Encoder.fitBatch(texts);

        // 创建MilvusHybridStore
        hybridStore = new MilvusHybridStore(milvusClient, milvusProperties, embeddingClient, bm25Encoder);

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
    @DisplayName("1. 应能插入测试文档（自动生成Sparse向量）")
    void testInsertDocuments() {
        // 插入测试文档
        List<String> ids = hybridStore.insert(TEST_DOCUMENTS);

        // 验证
        assertNotNull(ids, "插入结果不应为null");
        assertEquals(TEST_DOCUMENTS.size(), ids.size(), "插入数量应匹配");

        System.out.println("✅ 成功插入" + ids.size() + "个文档（包含Sparse向量）");

        // 等待Milvus索引
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Order(2)
    @DisplayName("2. 应能执行Sparse检索")
    void testSparseSearch() {
        // 查询：包含"向量数据库"关键词
        String query = "向量数据库";
        int topK = 3;

        List<Document> results = hybridStore.searchSparse(query, topK);

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
    @DisplayName("3. 关键词匹配应返回相关文档")
    void testKeywordMatching() {
        // 查询：明确包含"Milvus"关键词
        String query = "Milvus";
        List<Document> results = hybridStore.searchSparse(query, 5);

        // 验证：doc_001包含"Milvus"，应该得分较高
        assertNotNull(results, "检索结果不应为null");
        assertTrue(results.size() > 0, "应该返回至少1个结果");

        Document topDoc = results.get(0);
        System.out.println("✅ 最相关文档：" + topDoc.getId());
        System.out.println("   得分：" + topDoc.getScore());
        System.out.println("   文本：" + topDoc.getSummary());

        // doc_001或doc_002应该排名靠前（都包含"向量"相关词）
        boolean relevantDocFound = results.stream()
            .limit(2)
            .anyMatch(doc -> doc.getId().equals("doc_001") || doc.getId().equals("doc_002"));

        assertTrue(relevantDocFound, "前2名应包含相关文档");
    }

    @Test
    @Order(4)
    @DisplayName("4. 结果应按BM25分数降序排序")
    void testResultsSortedByScore() {
        String query = "数据库检索";
        List<Document> results = hybridStore.searchSparse(query, 5);

        // 验证排序
        for (int i = 0; i < results.size() - 1; i++) {
            float currentScore = results.get(i).getScore();
            float nextScore = results.get(i + 1).getScore();
            assertTrue(currentScore >= nextScore,
                "BM25分数应降序排列：" + currentScore + " >= " + nextScore);
        }

        System.out.println("✅ 结果正确按BM25分数降序排序");
    }

    @Test
    @Order(5)
    @DisplayName("5. 不同查询应返回不同结果")
    void testDifferentQueries() {
        // 查询1：数据库相关
        List<Document> dbResults = hybridStore.searchSparse("数据库", 3);

        // 查询2：编程相关
        List<Document> progResults = hybridStore.searchSparse("编程语言", 3);

        // 验证
        assertNotNull(dbResults, "数据库查询结果不应为null");
        assertNotNull(progResults, "编程查询结果不应为null");

        System.out.println("✅ 数据库查询：" + dbResults.size() + "个结果");
        System.out.println("✅ 编程查询：" + progResults.size() + "个结果");

        // 结果应该不完全相同（至少有一个文档不同）
        Set<String> dbIds = new HashSet<>();
        for (Document doc : dbResults) {
            dbIds.add(doc.getId());
        }

        Set<String> progIds = new HashSet<>();
        for (Document doc : progResults) {
            progIds.add(doc.getId());
        }

        assertNotEquals(dbIds, progIds, "不同查询应返回不同结果集");
    }

    @Test
    @Order(6)
    @DisplayName("6. BM25编码器统计信息应正确")
    void testBM25Stats() {
        int vocabSize = bm25Encoder.getVocabularySize();
        int totalDocs = bm25Encoder.getTotalDocuments();

        assertTrue(vocabSize > 0, "词表大小应大于0");
        assertEquals(TEST_DOCUMENTS.size(), totalDocs, "文档总数应匹配");

        System.out.println("✅ BM25统计：");
        System.out.println("   词表大小：" + vocabSize);
        System.out.println("   文档总数：" + totalDocs);
    }

    @Test
    @Order(7)
    @DisplayName("7. 空查询应返回空或处理正确")
    void testEmptyQuery() {
        String query = "";
        List<Document> results = hybridStore.searchSparse(query, 5);

        // 空查询应该返回空结果
        assertNotNull(results, "结果不应为null");
        assertEquals(0, results.size(), "空查询应返回空结果");

        System.out.println("✅ 空查询处理正确：返回空结果");
    }
}
