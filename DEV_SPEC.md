# RAG-SERVER 开发规范

> 基于 Spring Boot 3.x + Spring AI 的企业级 RAG 框架
>
> **版本**: v1.0.0  
> **最后更新**: 2026-11-10  
> **技术栈**: Spring Boot 3.4.1 | Spring AI 1.0.0 | 阿里云 DashScope | Milvus 2.5.3

---

# 目录

1. [项目概述](#1-项目概述)
2. [技术架构](#2-技术架构)
3. [核心组件设计](#3-核心组件设计)
4. [数据模型](#4-数据模型)
5. [功能清单](#5-功能清单)
6. [测试策略](#6-测试策略)
7. [部署与运维](#7-部署与运维)
8. [附录](#8-附录)

---

# 1. 项目概述

## 1.1 项目定位

RAG-SERVER是一个**生产级RAG框架**，采用Spring生态构建，提供：

- 🚀 **企业级架构**：Spring Boot + 依赖注入 + 配置管理
- 🎯 **混合检索**：Dense向量 + Sparse BM25 + RRF融合
- 🔌 **MCP协议**：标准化AI Agent工具接口
- 📊 **完整可观测**：Actuator监控 + Dashboard管理
- 🧩 **灵活扩展**：模型层抽象 + 业务逻辑解耦

## 1.2 核心特性

### 技术选型原则

```
Spring Boot       → 服务框架层（依赖注入、配置、REST API）
Spring AI         → 模型抽象层（仅ChatClient/EmbeddingClient）
纯Java实现        → 核心RAG逻辑（混合检索、Pipeline编排）
```

**为什么这样选**：
- ✅ Spring Boot：企业级特性开箱即用
- ✅ Spring AI：模型调用统一抽象（便于切换LLM/Embedding提供商）
- ✅ 纯Java：复杂检索逻辑需要完全自主控制
- ❌ 不用Spring AI的VectorStore（无法支持混合检索）
- ❌ 不用Spring AI的DocumentReader（PDFBox更灵活）

### 核心流程

```
┌────────────────────────────────────────────────────────────┐
│                    离线摄取流程                             │
│  PDF → PDFBox加载 → RecursiveSplitter → ChunkRefiner      │
│     → BatchEmbedding → BM25Encoding → Milvus批量存储       │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│                    在线查询流程                             │
│  Query → HybridRetriever(Dense∥Sparse → RRF融合)          │
│       → Reranker(可选) → PromptBuilder → ChatClient生成    │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│                    MCP对外服务                              │
│  MCP Client → McpServer(stdio) → RagService → Response    │
└────────────────────────────────────────────────────────────┘
```

---

# 2. 技术架构

## 2.1 整体架构

```
┌──────────────────────────────────────────────────────────────┐
│                   外部调用层                                  │
│  REST API | MCP Tools | Dashboard                           │
└───────────────────────────┬──────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────┐
│              Spring Boot Application                         │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         Controller Layer                               │ │
│  │  QueryController | IngestionController | DashboardCtrl │ │
│  └─────────────────────────┬──────────────────────────────┘ │
│                            ▼                                 │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         Service Layer (业务编排)                       │ │
│  │  RagService | IngestionService | DocumentService       │ │
│  └─────────────────────────┬──────────────────────────────┘ │
│                            ▼                                 │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         Component Layer                                │ │
│  │  ┌──────────────┐  ┌─────────────────────────────┐    │ │
│  │  │ Spring AI层  │  │  纯Java实现层                 │    │ │
│  │  │              │  │                               │    │ │
│  │  │ ChatClient   │  │  MilvusHybridStore           │    │ │
│  │  │ EmbeddingClient│ HybridRetriever              │    │ │
│  │  │ Document     │  │  RRFFusion                   │    │ │
│  │  │              │  │  BM25Encoder                 │    │ │
│  │  │              │  │  RecursiveSplitter           │    │ │
│  │  └──────────────┘  └─────────────────────────────┘    │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                   外部服务层                                  │
│  DashScope API | Milvus Server | H2/SQLite Database         │
└──────────────────────────────────────────────────────────────┘
```

## 2.2 技术栈明细

| 层次 | 组件 | 版本 | 说明 |
|-----|------|------|------|
| **框架** | Spring Boot | 3.4.1 | 服务框架 |
| **AI抽象** | Spring AI | 1.0.0 | ChatClient/EmbeddingClient/MCP Server |
| **LLM** | 阿里云DashScope | API v1 | qwen-max |
| **Embedding** | DashScope Embedding | API v1 | text-embedding-v3 (1536维) |
| **向量库** | Milvus | 2.5.3 | 混合检索（Dense+Sparse） |
| **关系数据库** | MySQL | 8.0 | 元数据存储 |
| **缓存** | Redis | 7.2 | 查询缓存 |
| **PDF** | Apache PDFBox | 3.0.3 | 文档加载 |
| **MCP** | Spring AI MCP | 内置 | MCP 协议支持 |
| **测试** | Spring Boot Test | 内置 | JUnit 5 + Mockito |

## 2.3 目录结构

```
RAG-SERVER/
├── src/
│   ├── main/
│   │   ├── java/com/ragserver/
│   │   │   ├── RagServerApplication.java
│   │   │   │
│   │   │   ├── config/                    # 配置类
│   │   │   │   ├── AiConfig.java         # Spring AI配置
│   │   │   │   ├── MilvusConfig.java     # Milvus配置
│   │   │   │   ├── DataSourceConfig.java
│   │   │   │   └── McpConfig.java
│   │   │   │
│   │   │   ├── ai/                        # Spring AI实现层
│   │   │   │   ├── dashscope/
│   │   │   │   │   ├── DashScopeChatClient.java
│   │   │   │   │   ├── DashScopeEmbeddingClient.java
│   │   │   │   │   ├── DashScopeProperties.java
│   │   │   │   │   └── RateLimiter.java
│   │   │   │   └── model/
│   │   │   │       └── ChunkDocument.java  # 扩展Spring AI的Document
│   │   │   │
│   │   │   ├── retrieval/                 # 检索层（纯Java）
│   │   │   │   ├── milvus/
│   │   │   │   │   ├── MilvusHybridStore.java
│   │   │   │   │   ├── MilvusProperties.java
│   │   │   │   │   └── CollectionManager.java
│   │   │   │   ├── HybridRetriever.java
│   │   │   │   ├── DenseRetriever.java
│   │   │   │   ├── SparseRetriever.java
│   │   │   │   ├── RRFFusion.java
│   │   │   │   ├── BM25Encoder.java
│   │   │   │   └── RerankerService.java
│   │   │   │
│   │   │   ├── ingestion/                 # 摄取层
│   │   │   │   ├── loader/
│   │   │   │   │   ├── PdfLoader.java
│   │   │   │   │   └── FileIntegrityService.java
│   │   │   │   ├── splitter/
│   │   │   │   │   └── RecursiveSplitter.java
│   │   │   │   ├── transformer/
│   │   │   │   │   ├── ChunkRefiner.java
│   │   │   │   │   ├── MetadataEnricher.java
│   │   │   │   │   └── ImageCaptioner.java (可选)
│   │   │   │   ├── IngestionPipeline.java
│   │   │   │   └── BatchProcessor.java
│   │   │   │
│   │   │   ├── service/                   # 业务服务
│   │   │   │   ├── RagService.java
│   │   │   │   ├── IngestionService.java
│   │   │   │   ├── DocumentService.java
│   │   │   │   ├── PromptService.java
│   │   │   │   └── EvaluationService.java
│   │   │   │
│   │   │   ├── controller/                # REST控制器
│   │   │   │   ├── QueryController.java
│   │   │   │   ├── IngestionController.java
│   │   │   │   ├── DocumentController.java
│   │   │   │   └── DashboardController.java
│   │   │   │
│   │   │   ├── mcp/                       # MCP集成
│   │   │   │   ├── McpServerRunner.java
│   │   │   │   └── McpToolsProvider.java
│   │   │   │
│   │   │   ├── repository/                # 数据访问
│   │   │   │   ├── IngestionHistoryRepository.java
│   │   │   │   └── ImageIndexRepository.java
│   │   │   │
│   │   │   ├── entity/                    # JPA实体
│   │   │   │   ├── IngestionHistory.java
│   │   │   │   └── ImageIndex.java
│   │   │   │
│   │   │   └── domain/                    # 领域模型
│   │   │       ├── ChunkMetadata.java
│   │   │       ├── ImageRef.java
│   │   │       ├── HybridSearchResult.java
│   │   │       └── EvaluationReport.java
│   │   │
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-dev.yaml
│   │       ├── application-prod.yaml
│   │       ├── prompts/
│   │       │   ├── rag-query.st
│   │       │   ├── chunk-refinement.st
│   │       │   └── rerank.st
│   │       ├── db/migration/
│   │       │   └── V1__init_schema.sql
│   │       └── static/dashboard/
│   │
│   └── test/
│       ├── java/com/ragserver/
│       │   ├── ai/                        # AI组件测试
│       │   ├── retrieval/                 # 检索测试
│       │   ├── service/                   # 服务测试
│       │   ├── integration/               # 集成测试
│       │   └── e2e/                       # E2E测试
│       └── resources/
│           ├── application-test.yaml
│           └── fixtures/
│               ├── sample.pdf
│               └── golden_test_set.json
│
├── data/                                  # 运行时数据 (gitignore)
│   ├── documents/
│   ├── images/
│   └── db/
├── logs/                                  # 日志 (gitignore)
├── docker-compose.yml
├── pom.xml
├── README.md
└── SPRING_AI_GUIDE.md                     # Spring AI使用指南
```

---

# 3. 核心组件设计

## 3.1 模型层（Spring AI抽象）

### 3.1.1 DashScopeChatClient

**实现Spring AI的ChatClient接口**，对接阿里云通义千问。

```java
package com.ragserver.ai.dashscope;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * DashScope ChatClient实现
 * 
 * 功能：
 * - 调用通义千问API
 * - 支持限流与重试
 * - 实现Spring AI标准接口
 */
public class DashScopeChatClient implements ChatClient {
    
    private final DashScopeProperties properties;
    private final RestTemplate restTemplate;
    private final RateLimiter rateLimiter;
    
    public DashScopeChatClient(DashScopeProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
        this.rateLimiter = new RateLimiter(properties.getQps());
    }
    
    @Override
    public ChatResponse call(Prompt prompt) {
        rateLimiter.acquire();  // 限流
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        
        Map<String, Object> request = Map.of(
            "model", properties.getModel(),
            "input", Map.of(
                "messages", List.of(
                    Map.of("role", "user", "content", prompt.getContents())
                )
            ),
            "parameters", Map.of(
                "result_format", "message"
            )
        );
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        
        // 重试逻辑
        int retries = 3;
        Exception lastException = null;
        
        for (int i = 0; i < retries; i++) {
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(
                    properties.getBaseUrl() + "/services/aigc/text-generation/generation",
                    entity,
                    Map.class
                );
                
                return parseToChatResponse(response.getBody());
                
            } catch (Exception e) {
                lastException = e;
                if (i < retries - 1) {
                    try {
                        Thread.sleep((long) Math.pow(2, i) * 1000);  // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        throw new RuntimeException("DashScope API调用失败", lastException);
    }
    
    private ChatResponse parseToChatResponse(Map<String, Object> response) {
        Map<String, Object> output = (Map<String, Object>) response.get("output");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");
        
        Generation generation = new Generation(content);
        return new ChatResponse(List.of(generation));
    }
}
```

**配置类**：

```java
@ConfigurationProperties(prefix = "spring.ai.dashscope")
@Data
public class DashScopeProperties {
    private String apiKey;
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String model = "qwen-max";
    private Integer timeoutMs = 60000;
    private Integer qps = 10;  // 限流阈值
}
```

---

### 3.1.2 DashScopeEmbeddingClient

**实现Spring AI的EmbeddingClient接口**。

```java
package com.ragserver.ai.dashscope;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DashScope EmbeddingClient实现
 * 
 * 功能：
 * - 调用text-embedding-v4 API
 * - 批量处理（batch_size=16）
 * - 返回2048维向量
 */
public class DashScopeEmbeddingClient implements EmbeddingClient {
    
    private final DashScopeProperties properties;
    private final RestTemplate restTemplate;
    private final RateLimiter rateLimiter;
    
    private static final int BATCH_SIZE = 16;
    private static final int DIMENSION = 2048;
    
    public DashScopeEmbeddingClient(DashScopeProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
        this.rateLimiter = new RateLimiter(properties.getQps());
    }
    
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>();
        
        // 分批处理
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(
                i, 
                Math.min(i + BATCH_SIZE, texts.size())
            );
            
            embeddings.addAll(embedBatch(batch));
        }
        
        return new EmbeddingResponse(embeddings);
    }
    
    private List<Embedding> embedBatch(List<String> texts) {
        rateLimiter.acquire();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        
        Map<String, Object> request = Map.of(
            "model", "text-embedding-v4",
            "input", Map.of("texts", texts)
        );
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            properties.getBaseUrl() + "/services/embeddings/text-embedding/text-embedding",
            entity,
            Map.class
        );
        
        return parseToEmbeddings(response.getBody());
    }
    
    private List<Embedding> parseToEmbeddings(Map<String, Object> response) {
        Map<String, Object> output = (Map<String, Object>) response.get("output");
        List<Map<String, Object>> embeddings = (List<Map<String, Object>>) output.get("embeddings");
        
        return embeddings.stream()
            .map(e -> {
                List<Double> vector = (List<Double>) e.get("embedding");
                return new Embedding(vector, (Integer) e.get("text_index"));
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public int dimensions() {
        return DIMENSION;
    }
    
    // 便捷方法：单文本embed
    public List<Double> embed(String text) {
        return call(new EmbeddingRequest(List.of(text), null))
            .getResults().get(0).getOutput();
    }
}
```

---

### 3.1.3 Spring AI配置

```java
@Configuration
@EnableConfigurationProperties(DashScopeProperties.class)
public class AiConfig {
    
    @Bean
    public ChatClient chatClient(DashScopeProperties properties) {
        return new DashScopeChatClient(properties);
    }
    
    @Bean
    public EmbeddingClient embeddingClient(DashScopeProperties properties) {
        return new DashScopeEmbeddingClient(properties);
    }
}
```

**application.yaml**：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model: qwen-max
      qps: 10
```

---

## 3.2 检索层（纯Java实现）

### 3.2.1 MilvusHybridStore

**为什么不用Spring AI的VectorStore**：
- Spring AI的`VectorStore`接口只支持单一向量
- 无法表达混合检索（Dense + Sparse）
- 强行适配会失去Milvus的能力

**解决方案**：直接使用Milvus SDK，自定义接口。

```java
package com.ragserver.retrieval.milvus;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.*;
import io.milvus.param.index.*;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Milvus混合向量存储
 * 
 * 功能：
 * - 支持Dense + Sparse双向量
 * - 混合检索（两路并行）
 * - RRF融合
 * - 批量Upsert（幂等）
 */
@Component
public class MilvusHybridStore {
    
    private final MilvusServiceClient milvusClient;
    private final EmbeddingClient embeddingClient;
    private final MilvusProperties properties;
    private final String collectionName;
    
    public MilvusHybridStore(MilvusProperties properties, 
                             EmbeddingClient embeddingClient) {
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        this.collectionName = properties.getCollectionName();
        
        // 初始化Milvus连接
        this.milvusClient = new MilvusServiceClient(
            ConnectParam.newBuilder()
                .withUri(properties.getUri())
                .build()
        );
        
        // 自动创建Collection
        if (properties.isAutoCreateCollection()) {
            initCollection();
        }
    }
    
    /**
     * 初始化Collection Schema
     */
    private void initCollection() {
        // 检查是否已存在
        R<Boolean> hasCollection = milvusClient.hasCollection(
            HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build()
        );
        
        if (hasCollection.getData()) {
            return;  // 已存在，跳过
        }
        
        // 定义Schema
        FieldType idField = FieldType.newBuilder()
            .withName("id")
            .withDataType(DataType.VarChar)
            .withMaxLength(128)
            .withPrimaryKey(true)
            .build();
        
        FieldType textField = FieldType.newBuilder()
            .withName("text")
            .withDataType(DataType.VarChar)
            .withMaxLength(65535)
            .build();
        
        FieldType denseVectorField = FieldType.newBuilder()
            .withName("dense_vector")
            .withDataType(DataType.FloatVector)
            .withDimension(2048)
            .build();
        
        FieldType sparseVectorField = FieldType.newBuilder()
            .withName("sparse_vector")
            .withDataType(DataType.SparseFloatVector)
            .build();
        
        FieldType metadataField = FieldType.newBuilder()
            .withName("metadata")
            .withDataType(DataType.JSON)
            .build();
        
        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
            .withEnableDynamicField(true)
            .addFieldType(idField)
            .addFieldType(textField)
            .addFieldType(denseVectorField)
            .addFieldType(sparseVectorField)
            .addFieldType(metadataField)
            .build();
        
        // 创建Collection
        milvusClient.createCollection(
            CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withSchema(schema)
                .build()
        );
        
        // 创建索引
        createIndexes();
        
        // 加载到内存
        milvusClient.loadCollection(
            LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build()
        );
    }
    
    /**
     * 创建索引
     */
    private void createIndexes() {
        // Dense向量索引（AUTOINDEX + COSINE）
        milvusClient.createIndex(
            CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("dense_vector")
                .withIndexType(IndexType.AUTOINDEX)
                .withMetricType(MetricType.COSINE)
                .build()
        );
        
        // Sparse向量索引（SPARSE_INVERTED_INDEX + IP）
        milvusClient.createIndex(
            CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("sparse_vector")
                .withIndexType(IndexType.SPARSE_INVERTED_INDEX)
                .withMetricType(MetricType.IP)
                .build()
        );
    }
    
    /**
     * 批量插入/更新文档
     */
    public void batchUpsert(List<Document> documents) {
        List<InsertParam.Field> fields = new ArrayList<>();
        
        List<String> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<List<Float>> denseVectors = new ArrayList<>();
        List<SortedMap<Long, Float>> sparseVectors = new ArrayList<>();
        List<String> metadataJsons = new ArrayList<>();
        
        for (Document doc : documents) {
            // 1. ID
            ids.add(doc.getId());
            
            // 2. 文本
            texts.add(doc.getContent());
            
            // 3. Dense向量
            List<Double> embedding = embeddingClient.embed(doc.getContent());
            denseVectors.add(embedding.stream()
                .map(Double::floatValue)
                .collect(Collectors.toList()));
            
            // 4. Sparse向量（BM25）
            sparseVectors.add(generateBM25Vector(doc.getContent()));
            
            // 5. Metadata（JSON序列化）
            metadataJsons.add(serializeMetadata(doc.getMetadata()));
        }
        
        fields.add(new InsertParam.Field("id", ids));
        fields.add(new InsertParam.Field("text", texts));
        fields.add(new InsertParam.Field("dense_vector", denseVectors));
        fields.add(new InsertParam.Field("sparse_vector", sparseVectors));
        fields.add(new InsertParam.Field("metadata", metadataJsons));
        
        // 执行插入
        milvusClient.insert(
            InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build()
        );
    }
    
    /**
     * 混合检索
     */
    public HybridSearchResult hybridSearch(String query, int topK) {
        // 1. Dense检索
        List<Document> denseResults = searchDense(query, topK * 2);
        
        // 2. Sparse检索
        List<Document> sparseResults = searchSparse(query, topK * 2);
        
        // 3. RRF融合
        List<Document> fusedResults = rrfFusion(denseResults, sparseResults, topK);
        
        return new HybridSearchResult(fusedResults, denseResults, sparseResults);
    }
    
    /**
     * Dense检索（语义相似度）
     */
    private List<Document> searchDense(String query, int topK) {
        List<Double> queryVector = embeddingClient.embed(query);
        List<Float> queryVectorFloat = queryVector.stream()
            .map(Double::floatValue)
            .collect(Collectors.toList());
        
        SearchParam searchParam = SearchParam.newBuilder()
            .withCollectionName(collectionName)
            .withVectorFieldName("dense_vector")
            .withVectors(List.of(queryVectorFloat))
            .withTopK(topK)
            .withMetricType(MetricType.COSINE)
            .withOutFields(List.of("id", "text", "metadata"))
            .build();
        
        R<SearchResults> response = milvusClient.search(searchParam);
        return parseSearchResults(response.getData());
    }
    
    /**
     * Sparse检索（BM25关键词）
     */
    private List<Document> searchSparse(String query, int topK) {
        SortedMap<Long, Float> queryVector = generateBM25Vector(query);
        
        SearchParam searchParam = SearchParam.newBuilder()
            .withCollectionName(collectionName)
            .withVectorFieldName("sparse_vector")
            .withVectors(List.of(queryVector))
            .withTopK(topK)
            .withMetricType(MetricType.IP)
            .withOutFields(List.of("id", "text", "metadata"))
            .build();
        
        R<SearchResults> response = milvusClient.search(searchParam);
        return parseSearchResults(response.getData());
    }
    
    /**
     * RRF融合算法
     */
    private List<Document> rrfFusion(List<Document> dense, 
                                     List<Document> sparse, 
                                     int topK) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();
        int k = 60;  // RRF常数
        
        // Dense路贡献
        for (int i = 0; i < dense.size(); i++) {
            Document doc = dense.get(i);
            scores.merge(doc.getId(), 1.0 / (k + i + 1), Double::sum);
            docMap.put(doc.getId(), doc);
        }
        
        // Sparse路贡献
        for (int i = 0; i < sparse.size(); i++) {
            Document doc = sparse.get(i);
            scores.merge(doc.getId(), 1.0 / (k + i + 1), Double::sum);
            docMap.putIfAbsent(doc.getId(), doc);
        }
        
        // 排序并返回Top-K
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> docMap.get(e.getKey()))
            .collect(Collectors.toList());
    }
    
    /**
     * 生成BM25稀疏向量
     * 
     * 注意：这里需要实现BM25编码逻辑
     * 可以调用Milvus内置函数，或自己实现
     */
    private SortedMap<Long, Float> generateBM25Vector(String text) {
        // TODO: 实现BM25编码
        // 方案A: 调用Milvus BM25函数
        // 方案B: 自己实现（分词 + TF-IDF + BM25公式）
        return new TreeMap<>();
    }
    
    /**
     * 解析搜索结果
     */
    private List<Document> parseSearchResults(SearchResults results) {
        List<Document> documents = new ArrayList<>();
        
        for (SearchResults.Row row : results.getRowRecords()) {
            String id = (String) row.get("id");
            String text = (String) row.get("text");
            String metadataJson = (String) row.get("metadata");
            
            Map<String, Object> metadata = deserializeMetadata(metadataJson);
            documents.add(new Document(id, text, metadata));
        }
        
        return documents;
    }
    
    /**
     * 按source_path删除文档
     */
    public void deleteBySourcePath(String sourcePath) {
        String expr = String.format("metadata['source_path'] == '%s'", sourcePath);
        
        milvusClient.delete(
            DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(expr)
                .build()
        );
    }
    
    // 辅助方法
    private String serializeMetadata(Map<String, Object> metadata) {
        // 使用Jackson序列化为JSON
        return "{}";  // TODO: 实现
    }
    
    private Map<String, Object> deserializeMetadata(String json) {
        // 使用Jackson反序列化
        return new HashMap<>();  // TODO: 实现
    }
}
```

---

### 3.2.2 HybridRetriever

**业务层检索器**，协调混合检索流程。

```java
package com.ragserver.retrieval;

import com.ragserver.retrieval.milvus.MilvusHybridStore;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 混合检索器
 * 
 * 功能：
 * - 协调Dense + Sparse检索
 * - 可选Rerank
 * - 异常降级（单路检索）
 */
@Component
public class HybridRetriever {
    
    private final MilvusHybridStore vectorStore;
    private final RerankerService reranker;
    
    public HybridRetriever(MilvusHybridStore vectorStore, 
                          RerankerService reranker) {
        this.vectorStore = vectorStore;
        this.reranker = reranker;
    }
    
    /**
     * 检索文档
     * 
     * @param query 查询文本
     * @param topK 返回数量
     * @param enableRerank 是否启用重排序
     */
    public List<Document> retrieve(String query, int topK, boolean enableRerank) {
        // 1. 混合检索（召回更多候选）
        HybridSearchResult searchResult = vectorStore.hybridSearch(
            query, 
            enableRerank ? topK * 2 : topK
        );
        
        List<Document> candidates = searchResult.getFusedResults();
        
        // 2. 可选Rerank
        if (enableRerank) {
            candidates = reranker.rerank(query, candidates, topK);
        }
        
        return candidates;
    }
}
```

---

### 3.2.3 BM25Encoder

**BM25稀疏向量编码器**。

```java
package com.ragserver.retrieval;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * BM25编码器
 * 
 * 功能：
 * - 分词（中英文）
 * - TF-IDF计算
 * - BM25公式
 */
@Component
public class BM25Encoder {
    
    private static final double K1 = 1.5;
    private static final double B = 0.75;
    
    private final Tokenizer tokenizer;
    private final IDFCalculator idfCalculator;
    
    public BM25Encoder() {
        this.tokenizer = new Tokenizer();
        this.idfCalculator = new IDFCalculator();
    }
    
    /**
     * 编码文本为稀疏向量
     * 
     * @return SortedMap<termId, weight>
     */
    public SortedMap<Long, Float> encode(String text) {
        // 1. 分词
        List<String> tokens = tokenizer.tokenize(text);
        
        // 2. 计算词频
        Map<String, Integer> termFreq = new HashMap<>();
        for (String token : tokens) {
            termFreq.merge(token, 1, Integer::sum);
        }
        
        // 3. 计算BM25分数
        SortedMap<Long, Float> sparseVector = new TreeMap<>();
        double avgDocLen = idfCalculator.getAvgDocLength();
        double docLen = tokens.size();
        
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();
            
            long termId = idfCalculator.getTermId(term);
            double idf = idfCalculator.getIDF(term);
            
            // BM25公式
            double score = idf * (tf * (K1 + 1)) / 
                          (tf + K1 * (1 - B + B * (docLen / avgDocLen)));
            
            sparseVector.put(termId, (float) score);
        }
        
        return sparseVector;
    }
    
    /**
     * 更新IDF统计（在摄取时调用）
     */
    public void updateCorpusStats(List<String> newTexts) {
        idfCalculator.update(newTexts);
    }
}
```

---

## 3.3 摄取层

### 3.3.1 IngestionPipeline

**文档摄取流程编排**。

```java
package com.ragserver.ingestion;

import com.ragserver.ingestion.loader.PdfLoader;
import com.ragserver.ingestion.splitter.RecursiveSplitter;
import com.ragserver.ingestion.transformer.*;
import com.ragserver.retrieval.milvus.MilvusHybridStore;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

/**
 * 摄取Pipeline
 * 
 * 流程：
 * PDF → Load → Split → Refine → Enrich → Caption → Embed → Store
 */
@Service
public class IngestionPipeline {
    
    private final PdfLoader pdfLoader;
    private final RecursiveSplitter splitter;
    private final ChunkRefiner refiner;
    private final MetadataEnricher enricher;
    private final ImageCaptioner captioner;
    private final MilvusHybridStore vectorStore;
    private final FileIntegrityService integrityService;
    
    @Transactional
    public void ingest(Path pdfPath, String collection) {
        // 0. 去重检查
        if (integrityService.shouldSkip(pdfPath)) {
            return;
        }
        
        try {
            // 1. 加载PDF
            List<Document> pages = pdfLoader.load(pdfPath);
            
            // 2. 切分
            List<Document> chunks = splitter.split(pages);
            
            // 3. 转换Pipeline
            chunks = refiner.refine(chunks);
            chunks = enricher.enrich(chunks);
            chunks = captioner.caption(chunks);  // 可选
            
            // 4. 存储（自动embed）
            vectorStore.batchUpsert(chunks);
            
            // 5. 标记成功
            integrityService.markSuccess(pdfPath, chunks.size());
            
        } catch (Exception e) {
            integrityService.markFailed(pdfPath, e.getMessage());
            throw new RuntimeException("摄取失败", e);
        }
    }
}
```

---

## 3.4 业务服务层

### 3.4.1 RagService

**核心RAG服务**。

```java
package com.ragserver.service;

import com.ragserver.retrieval.HybridRetriever;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG核心服务
 * 
 * 功能：
 * - 混合检索
 * - Prompt构建
 * - LLM生成
 * - Citation生成
 */
@Service
public class RagService {
    
    private final HybridRetriever retriever;
    private final ChatClient chatClient;
    private final PromptService promptService;
    
    public RagService(HybridRetriever retriever, 
                      ChatClient chatClient,
                      PromptService promptService) {
        this.retriever = retriever;
        this.chatClient = chatClient;
        this.promptService = promptService;
    }
    
    /**
     * RAG查询
     */
    public String query(String question) {
        return query(question, 10, false);
    }
    
    public String query(String question, int topK, boolean enableRerank) {
        // 1. 检索
        List<Document> context = retriever.retrieve(question, topK, enableRerank);
        
        // 2. 构建Prompt
        String prompt = promptService.buildRagPrompt(question, context);
        
        // 3. LLM生成
        ChatResponse response = chatClient.call(new Prompt(prompt));
        String answer = response.getResult().getOutput().getContent();
        
        // 4. 添加引用
        String citations = promptService.buildCitations(context);
        
        return answer + "\n\n" + citations;
    }
}
```

---

# 4. 数据模型

## 4.1 Spring AI原生模型

```java
// Spring AI提供
org.springframework.ai.document.Document
  - String id
  - String content
  - Map<String, Object> metadata
  - List<Double> embedding (可选)

org.springframework.ai.chat.ChatResponse
  - List<Generation> results
  - ChatResponseMetadata metadata

org.springframework.ai.embedding.EmbeddingResponse
  - List<Embedding> embeddings
```

## 4.2 JPA实体

### IngestionHistory

```java
@Entity
@Table(name = "ingestion_history")
@Data
public class IngestionHistory {
    @Id
    private String fileHash;  // SHA256
    
    @Column(nullable = false)
    private String filePath;
    
    private Long fileSize;
    
    @Enumerated(EnumType.STRING)
    private IngestionStatus status;  // SUCCESS | FAILED | PROCESSING
    
    private Instant processedAt;
    
    private String errorMsg;
    
    private Integer chunkCount;
}
```

### ImageIndex

```java
@Entity
@Table(name = "image_index")
@Data
public class ImageIndex {
    @Id
    private String imageId;
    
    private String filePath;
    
    private String collection;
    
    private String docHash;
    
    private Integer pageNum;
    
    private Instant createdAt;
}
```

## 4.3 领域模型

### ChunkMetadata

```java
@Data
public class ChunkMetadata {
    private String sourcePath;
    private String docType;
    private Integer page;
    private Integer chunkIndex;
    private String title;
    private List<String> tags;
    private List<ImageRef> images;
    private String refinedBy;  // "rule" | "llm"
    private Instant createdAt;
}
```

### HybridSearchResult

```java
@Data
public class HybridSearchResult {
    private List<Document> fusedResults;    // RRF融合结果
    private List<Document> denseResults;    // Dense路原始结果
    private List<Document> sparseResults;   // Sparse路原始结果
}
```

---

# 5. 功能清单

本章节列出 RAG-SERVER 已实现的核心功能和特性。

## 5.1 AI 集成

### DashScope Chat Client
- ✅ 基于 Spring AI 的统一抽象
- ✅ 支持流式输出（SSE）
- ✅ 限流控制（QPS 限制）
- ✅ 自动重试机制（指数退避）
- ✅ 支持多模型切换（qwen-max、qwen-plus）

### DashScope Embedding Client
- ✅ 批量向量化（最大 25 个/批次）
- ✅ Redis 缓存（避免重复计算）
- ✅ 向量维度：1536 维（text-embedding-v3）
- ✅ 异常处理和重试

### DashScope Vision Client
- ✅ 图片理解和描述生成
- ✅ 支持本地文件和 URL
- ✅ 多模态内容处理

## 5.2 向量检索

### Milvus 混合检索
- ✅ Dense 向量检索（基于语义相似度）
- ✅ Sparse 检索（BM25 全文检索）
- ✅ RRF 融合算法（Reciprocal Rank Fusion）
- ✅ 支持 Rerank 重排序
- ✅ 性能监控（检索耗时统计）

### HybridRetriever
- ✅ 并行检索（Dense 和 Sparse 同时执行）
- ✅ 容错机制（单路失败降级）
- ✅ 可配置的 topK 和候选数量
- ✅ 灵活的检索模式（Dense only、Sparse only、Hybrid）

### BM25 Encoder
- ✅ 基于 Lucene 的分词和 TF-IDF 计算
- ✅ 支持中英文混合文本
- ✅ Sparse 向量编码

## 5.3 文档摄取

### PDF 文档加载
- ✅ 基于 Apache PDFBox
- ✅ 支持文本提取
- ✅ 支持图片提取
- ✅ 文件完整性校验（SHA256）

### 文档切分
- ✅ RecursiveSplitter 递归切分
- ✅ 智能段落识别
- ✅ 可配置的 chunk 大小和重叠
- ✅ 保留元数据信息

### Chunk 精炼
- ✅ 段落清理和格式化
- ✅ 元数据增强
- ✅ 图片关联处理

### 批量处理
- ✅ 批量向量化
- ✅ 批量 BM25 编码
- ✅ 批量存储到 Milvus
- ✅ 性能统计（各阶段耗时）

### 去重机制
- ✅ 基于文件 SHA256 哈希
- ✅ 避免重复摄取
- ✅ 摄取历史记录

## 5.4 RAG 服务

### 查询生成
- ✅ 检索相关文档
- ✅ 构建 Prompt（系统提示词 + 上下文 + 问题）
- ✅ LLM 生成答案
- ✅ 流式输出支持
- ✅ 引用文档追踪

### 性能优化
- ✅ 查询缓存（Redis）
- ✅ 并行检索
- ✅ 异步处理
- ✅ 性能监控

### 异常处理
- ✅ 统一异常体系
- ✅ 降级策略
- ✅ 错误日志记录

## 5.5 MCP 协议集成

### MCP Tools
- ✅ ragQuery - 查询知识库
  - 参数：question（必需）、topK（可选，默认 5）、enableRerank（可选，默认 false）
  - 返回：answer + retrievedDocuments + totalDurationMs

- ✅ ragIngest - 上传文档
  - 参数：filePath（必需）、collection（可选）
  - 返回：chunkCount + collection + durationMs

- ✅ ragListDocs - 列出文档
  - 参数：status（可选，SUCCESS/FAILED）
  - 返回：documents[] + total

- ✅ ragDeleteDoc - 删除文档
  - 参数：documentId（可选）、filePath（可选）
  - 返回：deletedChunks + message

### MCP Server
- ✅ 基于 Spring AI MCP 实现
- ✅ Stdio 模式通信
- ✅ 与 Claude Desktop/CLI 无缝集成
- ✅ 自动工具注册

### 异常处理
- ✅ McpException 基础异常
- ✅ McpValidationException 参数验证
- ✅ McpNotFoundException 资源不存在
- ✅ McpInternalException 内部错误
- ✅ 统一错误格式：`[ERROR_CODE] message`

### 集成测试
- ✅ 25 个集成测试（24 个通过）
- ✅ 覆盖正常流程 + 异常场景 + 参数验证

## 5.6 元数据管理

### 文档管理
- ✅ DocumentService - 文档生命周期管理
  - listDocuments() - 列出所有文档
  - getDocumentDetail() - 获取文档详情
  - deleteDocument() - 删除文档（联动清理向量和历史）
  - getCollectionStats() - 获取统计信息

### 图片存储
- ✅ ImageStorageService - 图片文件管理
  - save() - 保存图片
  - find() - 查找图片
  - deleteByDoc() - 按文档删除
  - getStats() - 获取统计

### 摄取历史
- ✅ IngestionHistoryRepository - 摄取记录管理
  - 记录每次摄取的元数据
  - 支持按状态查询
  - 支持按 Collection 分组

### 查询历史
- ✅ QueryHistory 实体
  - 记录查询问题、答案、检索文档
  - 记录查询参数和性能指标
  - 支持成功/失败状态

### 批量删除
- ✅ deleteCollection() - 按 Collection 批量删除
  - 清理向量库数据
  - 清理图片文件
  - 清理历史记录
  - 事务保证

## 5.7 REST API

### 文档摄取
- ✅ POST /api/ingest - 上传文档
  - 支持 multipart/form-data
  - 支持自定义 Collection
  - 返回摄取结果和性能统计

### 文档管理
- ✅ GET /api/documents - 列出文档
- ✅ GET /api/documents/{id} - 获取文档详情
- ✅ DELETE /api/documents/{id} - 删除文档

### RAG 查询
- ✅ POST /api/query - RAG 查询
  - 支持流式输出（SSE）
  - 支持 topK 和 Rerank 参数
  - 返回答案和引用文档

### 统计信息
- ✅ GET /api/stats/overview - 系统总览
  - 总文档数、成功/失败数
  - 总 Chunk 数、向量数
  - Collection 数、图片数

- ✅ GET /api/stats/collections - Collection 统计
  - 按 Collection 分组统计
  - 文档数和 Chunk 数

### Collection 管理
- ✅ GET /api/collections - 列出 Collection

### API 文档
- ✅ Swagger UI 自动生成
- ✅ OpenAPI 3.0 规范
- ✅ 访问地址：http://localhost:8080/swagger-ui.html

## 5.8 Dashboard 管理界面

### 前端实现
- ✅ 原生 HTML + CSS + JavaScript
- ✅ SPA 单页应用体验
- ✅ 响应式设计
- ✅ 代码量：1636 行（index.html + style.css + app.js）

### 系统总览页面
- ✅ 统计数据卡片展示
- ✅ 文档数、Chunk 数、Collection 数
- ✅ 图片数、系统状态
- ✅ 实时数据刷新

### 文档浏览器
- ✅ 文档列表展示
- ✅ 分页和搜索
- ✅ 文档详情查看
- ✅ 按状态筛选

### 文档摄取管理
- ✅ 文件上传界面
- ✅ 上传进度显示
- ✅ 错误提示
- ✅ Collection 选择

### 查询测试页面
- ✅ 查询输入界面
- ✅ 参数配置（topK、enableRerank）
- ✅ 实时结果展示
- ✅ 性能统计显示

### 历史记录
- ✅ 上传历史页面
- ✅ 查询历史页面
- ✅ 时间线展示

### 访问地址
- ✅ http://localhost:8080/

## 5.9 测试覆盖

### 核心测试
- ✅ RagServiceTest - 12/12 测试通过（100%）
- ✅ HybridRetrieverTest - 9/9 测试通过（100%）
- ✅ RagControllerTest - 7/7 测试通过（100%）
- ✅ 总计：28/28 核心测试通过（100%）

### MCP 集成测试
- ✅ RagMcpToolsIntegrationTest - 24/25 测试通过（96%）
- ✅ 覆盖所有 4 个 MCP Tools
- ✅ 覆盖正常流程、异常处理、参数验证

### 测试框架
- ✅ JUnit 5
- ✅ Mockito
- ✅ Spring Boot Test
- ✅ @SpringBootTest 集成测试

## 5.10 部署与运维

### Docker Compose
- ✅ 6 个服务编排
  - MySQL 8.0
  - Redis 7.2
  - Milvus 2.5.3
  - Etcd
  - MinIO
  - Attu（Milvus Web UI）

### 配置管理
- ✅ application.yaml 统一配置
- ✅ 环境变量支持
- ✅ Profile 分离（dev/prod）

### 监控
- ✅ Spring Boot Actuator
- ✅ Health Check 端点
- ✅ Metrics 指标收集
- ✅ 日志记录（Slf4j + Logback）

### 数据持久化
- ✅ Docker Volume 挂载
- ✅ 数据库备份机制
- ✅ 向量数据持久化

---

# 6. 测试策略

## 6.1 分层策略

| 层次 | 覆盖 | 工具 | 约束 |
|-----|------|------|------|
| 单元测试 | Service、Retriever、Transformer | JUnit 5 + Mockito | Mock外部依赖 |
| 集成测试 | ChatClient、EmbeddingClient、MilvusHybridStore | Spring Boot Test + Testcontainers | 真实Milvus（Docker） |
| E2E测试 | 完整摄取→查询、MCP对接 | SpringBootTest + WireMock | 模拟外部API |

## 6.2 Testcontainers配置

```java
@Testcontainers
@SpringBootTest
public class MilvusIntegrationTest {
    
    @Container
    static MilvusContainer milvus = new MilvusContainer(
        DockerImageName.parse("milvusdb/milvus:v2.5.0")
    );
    
    @DynamicPropertySource
    static void configureMilvus(DynamicPropertyRegistry registry) {
        registry.add("milvus.uri", milvus::getEndpoint);
    }
}
```

## 6.3 质量目标

- **单元测试覆盖率**: ≥ 80%
- **集成测试**: 关键路径100%
- **E2E测试**: ≥ 3个核心场景
- **RAG质量**: Hit@5 ≥ 85%, MRR ≥ 0.75
- **性能**: Query P99 < 2s

---

# 7. 部署与运维

## 7.1 本地开发

```bash
# 1. 启动Milvus
docker-compose up -d

# 2. 配置环境变量
export DASHSCOPE_API_KEY="your-key"

# 3. 启动应用
mvn spring-boot:run

# 4. 访问
# - Dashboard: http://localhost:8080/dashboard
# - Actuator: http://localhost:8080/actuator
```

## 7.2 生产部署

### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/rag-server-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml

```yaml
version: '3.8'
services:
  milvus:
    image: milvusdb/milvus:v2.5.0
    ports:
      - "19530:19530"
    volumes:
      - milvus_data:/var/lib/milvus
  
  rag-server:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DASHSCOPE_API_KEY=${DASHSCOPE_API_KEY}
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - milvus
volumes:
  milvus_data:
```

---

# 8. 附录

## 附录A：pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.1</version>
    </parent>
    
    <groupId>com.ragserver</groupId>
    <artifactId>rag-server</artifactId>
    <version>1.0.0</version>
    
    <properties>
        <java.version>17</java.version>
        <spring-ai.version>1.0.0-M3</spring-ai.version>
        <milvus.version>2.6.24</milvus.version>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        
        <!-- Spring AI -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-core</artifactId>
        </dependency>
        
        <!-- Milvus -->
        <dependency>
            <groupId>io.milvus</groupId>
            <artifactId>milvus-sdk-java</artifactId>
            <version>${milvus.version}</version>
        </dependency>
        
        <!-- MCP SDK -->
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp</artifactId>
            <version>2.0.0</version>
        </dependency>
        
        <!-- PDFBox -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.8</version>
        </dependency>
        
        <!-- Database -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>milvus</artifactId>
            <version>1.19.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
    
    <repositories>
        <repository>
            <id>spring-milestones</id>
            <url>https://repo.spring.io/milestone</url>
        </repository>
    </repositories>
</project>
```

## 附录B：application.yaml完整配置

```yaml
spring:
  application:
    name: rag-server
  profiles:
    active: dev
  
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model: qwen-max
      qps: 10
  
  datasource:
    url: jdbc:h2:file:./data/db/rag_server
    driver-class-name: org.h2.Driver
  
  jpa:
    hibernate:
      ddl-auto: update

milvus:
  uri: http://localhost:19530
  collection-name: rag_knowledge_hub
  auto-create-collection: true

rag:
  chunking:
    chunk-size: 512
    chunk-overlap: 128
  retrieval:
    top-k-dense: 20
    top-k-sparse: 20
    top-k-final: 10
    enable-rerank: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

---

**文档版本**: v1.0.0  
**最后更新**: 2025-01-XX  
**维护者**: RAG-SERVER开发团队
