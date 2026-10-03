package com.ragserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RAG-SERVER 应用程序主类
 *
 * <p>这是基于Spring Boot 3.2和Spring AI 1.0.0-M3构建的企业级RAG框架。</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>多阶段检索增强生成（RAG）：Dense向量 + Sparse BM25 + RRF融合</li>
 *   <li>阿里云DashScope集成：通义千问LLM和文本向量化</li>
 *   <li>Milvus向量数据库：支持混合检索的高性能向量存储</li>
 *   <li>MCP协议支持：标准化的AI Agent工具接口</li>
 *   <li>完整的文档摄取流程：PDF解析、语义切分、向量化存储</li>
 *   <li>可观测性：Actuator监控、进度追踪、Dashboard管理</li>
 * </ul>
 *
 * <h3>技术栈</h3>
 * <ul>
 *   <li>框架：Spring Boot 3.2.1（Java 17+）</li>
 *   <li>AI抽象：Spring AI 1.0.0-M3（ChatClient、EmbeddingClient）</li>
 *   <li>LLM：阿里云DashScope（通义千问qwen-max）</li>
 *   <li>Embedding：DashScope text-embedding-v4（2048维）</li>
 *   <li>向量库：Milvus 2.5+（混合检索）</li>
 *   <li>数据库：H2/SQLite（元数据存储）</li>
 *   <li>文档处理：Apache PDFBox 3.0.8</li>
 *   <li>协议：MCP 2.0.0（Model Context Protocol）</li>
 * </ul>
 *
 * <h3>启动方式</h3>
 * <pre>{@code
 * // 方式1：Maven命令启动
 * mvn spring-boot:run
 *
 * // 方式2：JAR包启动
 * java -jar target/rag-server-1.0.0.jar
 *
 * // 方式3：指定配置文件启动
 * mvn spring-boot:run -Dspring-boot.run.profiles=prod
 * }</pre>
 *
 * <h3>环境变量配置</h3>
 * <p>启动前需要配置以下环境变量：</p>
 * <ul>
 *   <li>DASHSCOPE_API_KEY：阿里云DashScope API密钥（必需）</li>
 *   <li>MILVUS_URI：Milvus服务地址（可选，默认http://localhost:19530）</li>
 * </ul>
 *
 * <h3>健康检查</h3>
 * <p>应用启动后，可通过以下端点检查状态：</p>
 * <pre>
 * GET http://localhost:8080/actuator/health
 * 响应示例：{"status":"UP"}
 * </pre>
 *
 * <h3>配置文件</h3>
 * <ul>
 *   <li>application.yaml：通用配置</li>
 *   <li>application-dev.yaml：开发环境配置</li>
 *   <li>application-prod.yaml：生产环境配置</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @version 1.0.0
 * @since 2025-01-XX
 * @see <a href="https://github.com/Bye-666/RAG-SERVER">项目仓库</a>
 */
@SpringBootApplication
public class RagServerApplication {

    /**
     * 应用程序入口方法
     *
     * <p>启动Spring Boot应用容器，初始化所有Bean和服务。</p>
     *
     * <h4>启动流程</h4>
     * <ol>
     *   <li>加载配置文件（application.yaml及环境特定配置）</li>
     *   <li>扫描并注册所有@Component、@Service、@Repository、@Controller</li>
     *   <li>初始化数据源和JPA（如果配置了）</li>
     *   <li>启动内嵌Tomcat服务器（默认端口8080）</li>
     *   <li>注册Actuator端点</li>
     *   <li>应用就绪，开始接受请求</li>
     * </ol>
     *
     * <h4>启动日志示例</h4>
     * <pre>
     * 2025-01-XX 10:00:00.000  INFO --- [main] c.r.RagServerApplication : Starting RagServerApplication
     * 2025-01-XX 10:00:01.500  INFO --- [main] o.s.b.w.e.tomcat.TomcatWebServer : Tomcat started on port(s): 8080 (http)
     * 2025-01-XX 10:00:01.600  INFO --- [main] c.r.RagServerApplication : Started RagServerApplication in 2.5 seconds
     * </pre>
     *
     * @param args 命令行参数
     *             <ul>
     *               <li>--spring.profiles.active=dev：指定激活的配置文件</li>
     *               <li>--server.port=8081：指定服务端口</li>
     *               <li>--logging.level.com.ragserver=DEBUG：设置日志级别</li>
     *             </ul>
     */
    public static void main(String[] args) {
        // 启动Spring Boot应用
        SpringApplication.run(RagServerApplication.class, args);

        // 启动成功提示（可选）
        System.out.println("\n" +
                "========================================\n" +
                "   RAG-SERVER 启动成功！\n" +
                "========================================\n" +
                "   访问地址：http://localhost:8080\n" +
                "   健康检查：http://localhost:8080/actuator/health\n" +
                "   API文档：http://localhost:8080/swagger-ui.html（待实现）\n" +
                "========================================\n");
    }
}
