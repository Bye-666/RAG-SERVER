package com.ragserver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置属性测试类
 *
 * <p>验证@ConfigurationProperties自动装配是否正常工作。</p>
 *
 * <h3>测试内容</h3>
 * <ul>
 *   <li>DashScope配置属性是否正确加载</li>
 *   <li>Milvus配置属性是否正确加载</li>
 *   <li>环境变量是否正确解析</li>
 *   <li>默认值是否生效</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("配置属性测试")
class ConfigurationPropertiesTest {

    /**
     * DashScope配置属性（自动注入）
     */
    @Autowired
    private DashScopeProperties dashScopeProperties;

    /**
     * Milvus配置属性（自动注入）
     */
    @Autowired
    private MilvusProperties milvusProperties;

    /**
     * 测试DashScope配置属性加载
     *
     * <p>验证application-test.yaml中的配置是否正确绑定。</p>
     */
    @Test
    @DisplayName("DashScope配置属性应正确加载")
    void testDashScopePropertiesLoaded() {
        // 验证配置对象不为null
        assertNotNull(dashScopeProperties, "DashScope配置对象应被成功注入");

        // 验证API密钥（测试环境使用Mock值）
        assertEquals("test-api-key", dashScopeProperties.getApiKey(),
                "API密钥应为test-api-key");

        // 验证Base URL
        assertNotNull(dashScopeProperties.getBaseUrl(),
                "Base URL不应为null");
        assertTrue(dashScopeProperties.getBaseUrl().startsWith("http"),
                "Base URL应以http开头");

        // 验证模型名称
        assertEquals("qwen-max", dashScopeProperties.getModel(),
                "模型名称应为qwen-max");

        // 验证Embedding模型名称
        assertEquals("text-embedding-v4", dashScopeProperties.getEmbeddingModel(),
                "Embedding模型应为text-embedding-v4");

        // 验证Embedding维度
        assertEquals(2048, dashScopeProperties.getEmbeddingDimension(),
                "Embedding维度应为2048");

        // 验证超时时间
        assertNotNull(dashScopeProperties.getTimeoutMs(),
                "超时时间不应为null");
        assertTrue(dashScopeProperties.getTimeoutMs() > 0,
                "超时时间应大于0");

        // 验证QPS限制
        assertNotNull(dashScopeProperties.getQps(),
                "QPS限制不应为null");
        assertTrue(dashScopeProperties.getQps() > 0,
                "QPS应大于0");
    }

    /**
     * 测试Milvus配置属性加载
     *
     * <p>验证application-test.yaml中的Milvus配置是否正确绑定。</p>
     */
    @Test
    @DisplayName("Milvus配置属性应正确加载")
    void testMilvusPropertiesLoaded() {
        // 验证配置对象不为null
        assertNotNull(milvusProperties, "Milvus配置对象应被成功注入");

        // 验证URI
        assertNotNull(milvusProperties.getUri(),
                "Milvus URI不应为null");
        assertTrue(milvusProperties.getUri().startsWith("http"),
                "Milvus URI应以http开头");

        // 验证Collection名称
        assertNotNull(milvusProperties.getCollectionName(),
                "Collection名称不应为null");
        assertFalse(milvusProperties.getCollectionName().isEmpty(),
                "Collection名称不应为空");
        assertTrue(milvusProperties.getCollectionName().contains("test"),
                "测试环境Collection名称应包含'test'");

        // 验证自动创建标志
        assertNotNull(milvusProperties.getAutoCreateCollection(),
                "自动创建标志不应为null");
        assertTrue(milvusProperties.getAutoCreateCollection(),
                "测试环境应启用自动创建Collection");

        // 验证Dense字段名
        assertEquals("dense_vector", milvusProperties.getDenseField(),
                "Dense字段名应为dense_vector");

        // 验证Sparse字段名
        assertEquals("sparse_vector", milvusProperties.getSparseField(),
                "Sparse字段名应为sparse_vector");

        // 验证度量方式
        assertEquals("COSINE", milvusProperties.getMetric(),
                "度量方式应为COSINE");
    }

    /**
     * 测试配置属性的默认值
     *
     * <p>验证当配置文件中未指定某些属性时，默认值是否生效。</p>
     */
    @Test
    @DisplayName("配置属性的默认值应正确生效")
    void testDefaultValues() {
        // DashScope默认值
        if (dashScopeProperties.getModel() == null) {
            fail("模型名称不应为null，应有默认值");
        }

        if (dashScopeProperties.getEmbeddingDimension() == null) {
            fail("Embedding维度不应为null，应有默认值2048");
        }

        // Milvus默认值
        if (milvusProperties.getDenseField() == null) {
            fail("Dense字段名不应为null，应有默认值");
        }

        if (milvusProperties.getMetric() == null) {
            fail("度量方式不应为null，应有默认值");
        }
    }

    /**
     * 测试配置属性的完整性
     *
     * <p>验证所有必需的配置项都已加载。</p>
     */
    @Test
    @DisplayName("所有必需的配置项应存在")
    void testAllRequiredPropertiesPresent() {
        // DashScope必需配置
        assertNotNull(dashScopeProperties.getApiKey(),
                "API密钥是必需的");
        assertNotNull(dashScopeProperties.getBaseUrl(),
                "Base URL是必需的");
        assertNotNull(dashScopeProperties.getModel(),
                "模型名称是必需的");

        // Milvus必需配置
        assertNotNull(milvusProperties.getUri(),
                "Milvus URI是必需的");
        assertNotNull(milvusProperties.getCollectionName(),
                "Collection名称是必需的");
    }

    /**
     * 测试配置属性的格式正确性
     *
     * <p>验证配置值的格式是否符合预期。</p>
     */
    @Test
    @DisplayName("配置属性的格式应正确")
    void testPropertyFormats() {
        // 验证URI格式
        String milvusUri = milvusProperties.getUri();
        assertTrue(milvusUri.matches("https?://.*"),
                "Milvus URI格式应为http://或https://开头");

        // 验证Collection名称格式（只能包含字母、数字、下划线）
        String collectionName = milvusProperties.getCollectionName();
        assertTrue(collectionName.matches("[a-zA-Z_][a-zA-Z0-9_]*"),
                "Collection名称格式不正确，应只包含字母、数字、下划线，且以字母或下划线开头");

        // 验证数值范围
        assertTrue(dashScopeProperties.getQps() > 0 && dashScopeProperties.getQps() <= 1000,
                "QPS应在合理范围内（1-1000）");

        assertTrue(dashScopeProperties.getTimeoutMs() >= 1000,
                "超时时间应至少为1000毫秒");
    }
}
