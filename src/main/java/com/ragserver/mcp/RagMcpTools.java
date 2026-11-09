package com.ragserver.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.ragserver.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

/**
 * RAG MCP Tools Configuration
 *
 * <p>定义 RAG 相关的 MCP Tools，使用 Spring AI Function Calling 机制。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class RagMcpTools {

    /**
     * RAG Query 请求参数
     */
    public record RagQueryRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("User question to query the knowledge base")
            String question,

            @JsonPropertyDescription("Number of documents to retrieve (default: 10, range: 1-50)")
            Integer topK,

            @JsonPropertyDescription("Enable LLM reranking for better results (default: false)")
            Boolean enableRerank
    ) {}

    /**
     * 注册 rag_query Tool
     *
     * @param ragService RAG 核心服务
     * @return Function
     */
    @Bean
    @Description("Query the RAG knowledge base with hybrid retrieval (Dense + Sparse)")
    public Function<RagQueryRequest, String> ragQuery(RagService ragService) {
        log.info("注册 MCP Tool: ragQuery");

        return request -> {
            try {
                String question = request.question();
                int topK = (request.topK() != null) ? request.topK() : 10;
                boolean enableRerank = (request.enableRerank() != null) ? request.enableRerank() : false;

                log.info("MCP Tool 调用: ragQuery, question={}, topK={}, enableRerank={}",
                        question, topK, enableRerank);

                RagService.QueryResult result = ragService.query(question, topK, enableRerank);

                return result.getAnswer() + "\n\n--- Performance ---\n" +
                        "Total: " + result.getTotalDurationMs() + " ms\n" +
                        "Retrieval: " + result.getRetrievalDurationMs() + " ms\n" +
                        "LLM: " + result.getLlmGenerationDurationMs() + " ms";

            } catch (Exception e) {
                log.error("ragQuery failed", e);
                return "Error: " + e.getMessage();
            }
        };
    }
}

