package com.ragserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger 配置
 *
 * <p>自动生成 REST API 文档，提供交互式 API 测试界面。</p>
 *
 * <h3>访问地址</h3>
 * <ul>
 *   <li>Swagger UI: <a href="http://localhost:8080/swagger-ui.html">http://localhost:8080/swagger-ui.html</a></li>
 *   <li>OpenAPI JSON: <a href="http://localhost:8080/v3/api-docs">http://localhost:8080/v3/api-docs</a></li>
 * </ul>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>自动扫描 Controller 生成 API 文档</li>
 *   <li>支持在线测试 API 接口</li>
 *   <li>自动识别请求参数和响应格式</li>
 *   <li>支持 Schema 定义和示例</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Configuration
public class OpenApiConfig {

    /**
     * 配置 OpenAPI 信息
     *
     * <p>定义 API 文档的元数据信息。</p>
     *
     * @return OpenAPI 配置对象
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers());
    }

    /**
     * API 基本信息
     */
    private Info apiInfo() {
        return new Info()
                .title("RAG-SERVER API 文档")
                .description("""
                        ## RAG-SERVER REST API

                        基于 Spring Boot 3.2 和 Spring AI 的企业级 RAG 框架 API 文档。

                        ### 核心功能
                        - 📄 **文档摄取**：上传 PDF 文档，自动向量化存储
                        - 🔍 **RAG 查询**：基于知识库的智能问答
                        - 📊 **统计监控**：系统运行状态和性能指标
                        - 📋 **历史管理**：查询和上传历史记录

                        ### 技术栈
                        - **向量数据库**：Milvus 2.5.3（混合检索）
                        - **LLM**：阿里云 DashScope qwen3.8-2.4t-a95b
                        - **Embedding**：qwen3.7-text-embedding-flash (1024维)
                        - **数据库**：MySQL 8.0
                        - **缓存**：Redis 7.2

                        ### 快速开始
                        1. 访问 [文档摄取接口](#/文档摄取/ingest) 上传文档
                        2. 访问 [RAG 查询接口](#/RAG查询/queryStream) 测试问答
                        3. 查看 [统计信息](#/统计监控/getOverview) 了解系统状态
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("RAG-SERVER 开发团队")
                        .url("https://github.com/your-org/rag-server")
                        .email("support@example.com"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    /**
     * 服务器列表
     */
    private List<Server> servers() {
        Server devServer = new Server()
                .url("http://localhost:8080")
                .description("开发环境");

        Server prodServer = new Server()
                .url("https://api.example.com")
                .description("生产环境（待部署）");

        return List.of(devServer, prodServer);
    }
}
