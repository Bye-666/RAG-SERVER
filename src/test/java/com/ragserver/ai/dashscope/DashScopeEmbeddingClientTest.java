package com.ragserver.ai.dashscope;

import com.ragserver.config.DashScopeProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * DashScopeEmbeddingClient单元测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@DisplayName("DashScopeEmbeddingClient单元测试")
class DashScopeEmbeddingClientTest {

    private DashScopeEmbeddingClient embeddingClient;
    private MockRestServiceServer mockServer;
    private DashScopeProperties properties;

    @BeforeEach
    void setUp() {
        // 配置属性
        properties = new DashScopeProperties();
        properties.setApiKey("test-api-key");
        properties.setBaseUrl("http://localhost:8080/mock");
        properties.setEmbeddingModel("text-embedding-v4");
        properties.setEmbeddingDimension(2048);
        properties.setTimeoutMs(60000);

        // 创建RestTemplate和MockServer
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        // 创建客户端
        embeddingClient = new DashScopeEmbeddingClient(properties, restTemplate);
    }

    @AfterEach
    void tearDown() {
        embeddingClient.shutdown();
    }

    @Test
    @DisplayName("应能生成单条文本的Embedding")
    void testEmbedSingle() {
        // Mock响应（简化的2048维向量）
        String mockResponse = createMockEmbeddingResponse(1, 2048);

        mockServer.expect(requestTo("http://localhost:8080/mock/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-api-key"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        // 调用
        List<Double> embedding = embeddingClient.embed("测试文本");

        // 验证
        assertNotNull(embedding, "Embedding不应为null");
        assertEquals(2048, embedding.size(), "向量维度应为2048");

        mockServer.verify();
    }

    @Test
    @DisplayName("应能批量生成Embedding（少于16条）")
    void testEmbedBatchSmall() {
        // Mock响应（5条文本）
        String mockResponse = createMockEmbeddingResponse(5, 2048);

        mockServer.expect(requestTo("http://localhost:8080/mock/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        // 创建5条文本
        List<String> texts = List.of("文本1", "文本2", "文本3", "文本4", "文本5");

        // 调用
        List<List<Double>> embeddings = embeddingClient.embedBatch(texts);

        // 验证
        assertNotNull(embeddings);
        assertEquals(5, embeddings.size(), "应返回5个向量");
        for (List<Double> embedding : embeddings) {
            assertEquals(2048, embedding.size(), "每个向量应为2048维");
        }

        mockServer.verify();
    }

    @Test
    @DisplayName("应能批量生成Embedding（超过16条，自动分批）")
    void testEmbedBatchLarge() {
        // 创建30条文本（应分2批：16 + 14）
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            texts.add("文本" + (i + 1));
        }

        // Mock第1批响应（16条）
        String mockResponse1 = createMockEmbeddingResponse(16, 2048);
        mockServer.expect(requestTo("http://localhost:8080/mock/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(mockResponse1, MediaType.APPLICATION_JSON));

        // Mock第2批响应（14条）
        String mockResponse2 = createMockEmbeddingResponse(14, 2048);
        mockServer.expect(requestTo("http://localhost:8080/mock/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(mockResponse2, MediaType.APPLICATION_JSON));

        // 调用
        List<List<Double>> embeddings = embeddingClient.embedBatch(texts);

        // 验证
        assertNotNull(embeddings);
        assertEquals(30, embeddings.size(), "应返回30个向量");
        for (List<Double> embedding : embeddings) {
            assertEquals(2048, embedding.size(), "每个向量应为2048维");
        }

        mockServer.verify();
    }

    @Test
    @DisplayName("应能处理空列表")
    void testEmbedBatchEmpty() {
        List<List<Double>> embeddings = embeddingClient.embedBatch(List.of());
        assertNotNull(embeddings);
        assertTrue(embeddings.isEmpty(), "空列表应返回空结果");
    }

    @Test
    @DisplayName("应能处理4xx客户端错误（不重试）")
    void testClientError() {
        mockServer.expect(requestTo("http://localhost:8080/mock/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Invalid API key\"}"));

        // 验证抛出异常
        assertThrows(DashScopeChatClient.DashScopeException.class, () -> {
            embeddingClient.embed("测试");
        });

        mockServer.verify();
    }

    @Test
    @DisplayName("应能重试5xx服务器错误")
    void testServerErrorRetry() {
        String successResponse = createMockEmbeddingResponse(1, 2048);

        // 前两次返回500，第三次成功
        mockServer.expect(requestTo("http://localhost:8080/mock/embeddings"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockServer.expect(requestTo("http://localhost:8080/mock/embeddings"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockServer.expect(requestTo("http://localhost:8080/mock/embeddings"))
            .andRespond(withSuccess(successResponse, MediaType.APPLICATION_JSON));

        // 调用（应该在第3次成功）
        List<Double> embedding = embeddingClient.embed("测试重试");

        assertNotNull(embedding);
        assertEquals(2048, embedding.size());

        mockServer.verify();
    }

    @Test
    @DisplayName("EmbeddingRequest工厂方法应正常工作")
    void testEmbeddingRequestFactoryMethods() {
        // 单条文本
        EmbeddingRequest singleReq = EmbeddingRequest.single("test-model", "文本");
        assertEquals("test-model", singleReq.getModel());
        assertEquals(1, singleReq.getInput().size());
        assertEquals("文本", singleReq.getInput().get(0));

        // 批量文本
        List<String> texts = List.of("文本1", "文本2", "文本3");
        EmbeddingRequest batchReq = EmbeddingRequest.batch("test-model", texts);
        assertEquals("test-model", batchReq.getModel());
        assertEquals(3, batchReq.getInput().size());
    }

    @Test
    @DisplayName("EmbeddingRequest批量应限制最多16条")
    void testEmbeddingRequestBatchLimit() {
        // 创建17条文本
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            texts.add("文本" + i);
        }

        // 验证抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingRequest.batch("test-model", texts);
        });
    }

    @Test
    @DisplayName("EmbeddingResponse应能正确提取结果")
    void testEmbeddingResponseMethods() {
        EmbeddingResponse response = new EmbeddingResponse();

        // 创建3个Embedding结果
        List<EmbeddingResponse.EmbeddingData> dataList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            EmbeddingResponse.EmbeddingData data = new EmbeddingResponse.EmbeddingData();
            data.setIndex(i);
            data.setEmbedding(createVector(2048, i));
            dataList.add(data);
        }
        response.setData(dataList);

        // 测试getEmbedding()
        List<Double> firstEmbedding = response.getEmbedding();
        assertNotNull(firstEmbedding);
        assertEquals(2048, firstEmbedding.size());

        // 测试getAllEmbeddings()
        List<List<Double>> allEmbeddings = response.getAllEmbeddings();
        assertEquals(3, allEmbeddings.size());

        // 测试isSuccess()
        assertTrue(response.isSuccess());

        // 测试getDimension()
        assertEquals(2048, response.getDimension());
    }

    /**
     * 创建Mock Embedding响应JSON
     *
     * @param count 向量数量
     * @param dimension 向量维度
     * @return JSON字符串
     */
    private String createMockEmbeddingResponse(int count, int dimension) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"object\": \"list\",");
        json.append("\"model\": \"text-embedding-v4\",");
        json.append("\"data\": [");

        for (int i = 0; i < count; i++) {
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"object\": \"embedding\",");
            json.append("\"index\": ").append(i).append(",");
            json.append("\"embedding\": ").append(createVectorJson(dimension, i));
            json.append("}");
        }

        json.append("],");
        json.append("\"usage\": {");
        json.append("\"prompt_tokens\": ").append(count * 10).append(",");
        json.append("\"total_tokens\": ").append(count * 10);
        json.append("}");
        json.append("}");

        return json.toString();
    }

    /**
     * 创建向量JSON数组
     */
    private String createVectorJson(int dimension, int seed) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < dimension; i++) {
            if (i > 0) json.append(",");
            // 生成简单的值：0.1 * (i + seed)
            json.append(String.format("%.3f", 0.001 * (i + seed)));
        }
        json.append("]");
        return json.toString();
    }

    /**
     * 创建向量（用于测试）
     */
    private List<Double> createVector(int dimension, int seed) {
        List<Double> vector = new ArrayList<>();
        for (int i = 0; i < dimension; i++) {
            vector.add(0.001 * (i + seed));
        }
        return vector;
    }
}
