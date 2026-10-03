package com.ragserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DashScope配置属性类
 *
 * <p>绑定application.yaml中spring.ai.dashscope开头的配置项。</p>
 *
 * <h3>配置项说明</h3>
 * <ul>
 *   <li>api-key：阿里云DashScope API密钥（必需，从环境变量读取）</li>
 *   <li>base-url：API基础URL（默认DashScope兼容模式地址）</li>
 *   <li>model：LLM模型名称（默认qwen-max）</li>
 *   <li>embedding-model：Embedding模型名称（默认text-embedding-v4）</li>
 *   <li>embedding-dimension：Embedding向量维度（默认2048）</li>
 *   <li>timeout-ms：API超时时间（毫秒，默认60000）</li>
 *   <li>qps：每秒请求数限制（默认10）</li>
 * </ul>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * spring:
 *   ai:
 *     dashscope:
 *       api-key: ${DASHSCOPE_API_KEY}
 *       base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
 *       model: qwen-max
 *       qps: 10
 * }</pre>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * @Service
 * public class DashScopeService {
 *     private final DashScopeProperties properties;
 *
 *     public DashScopeService(DashScopeProperties properties) {
 *         this.properties = properties;
 *     }
 *
 *     public String getApiKey() {
 *         return properties.getApiKey();
 *     }
 * }
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.dashscope")
public class DashScopeProperties {

    /**
     * DashScope API密钥
     *
     * <p>从环境变量DASHSCOPE_API_KEY读取，格式：sk-xxxxxxxxxxxx</p>
     *
     * <p>获取方式：</p>
     * <ol>
     *   <li>登录阿里云控制台</li>
     *   <li>进入DashScope服务</li>
     *   <li>创建API Key</li>
     *   <li>设置环境变量：export DASHSCOPE_API_KEY=your-key</li>
     * </ol>
     */
    private String apiKey;

    /**
     * API基础URL
     *
     * <p>默认值：https://dashscope.aliyuncs.com/compatible-mode/v1</p>
     *
     * <p>DashScope提供两种API模式：</p>
     * <ul>
     *   <li>兼容模式（compatible-mode）：OpenAI兼容接口</li>
     *   <li>原生模式（api）：DashScope原生接口</li>
     * </ul>
     *
     * <p>本项目使用兼容模式，便于后续切换到其他LLM提供商。</p>
     */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /**
     * LLM模型名称
     *
     * <p>默认值：qwen-max</p>
     *
     * <p>可选模型：</p>
     * <ul>
     *   <li>qwen-max：最强模型，适合复杂推理</li>
     *   <li>qwen-plus：平衡性能和成本</li>
     *   <li>qwen-turbo：快速响应，适合简单任务</li>
     * </ul>
     */
    private String model = "qwen-max";

    /**
     * Embedding模型名称
     *
     * <p>默认值：text-embedding-v4</p>
     *
     * <p>DashScope Embedding模型特点：</p>
     * <ul>
     *   <li>text-embedding-v4：2048维，支持中英文</li>
     *   <li>批量处理：最多16条文本</li>
     *   <li>最大输入长度：2048 tokens</li>
     * </ul>
     */
    private String embeddingModel = "text-embedding-v4";

    /**
     * Embedding向量维度
     *
     * <p>默认值：2048</p>
     *
     * <p>text-embedding-v4固定输出2048维向量。</p>
     */
    private Integer embeddingDimension = 2048;

    /**
     * API超时时间（毫秒）
     *
     * <p>默认值：60000（60秒）</p>
     *
     * <p>建议值：</p>
     * <ul>
     *   <li>Embedding：10000（10秒）</li>
     *   <li>Chat（短文本）：30000（30秒）</li>
     *   <li>Chat（长文本）：60000（60秒）</li>
     * </ul>
     */
    private Integer timeoutMs = 60000;

    /**
     * 每秒请求数限制（QPS）
     *
     * <p>默认值：10</p>
     *
     * <p>用于客户端限流，防止超过DashScope API限制：</p>
     * <ul>
     *   <li>免费账户：通常10 QPS</li>
     *   <li>付费账户：根据套餐不同</li>
     * </ul>
     *
     * <p>建议设置为实际限制的80%，留有余量。</p>
     */
    private Integer qps = 10;
}
