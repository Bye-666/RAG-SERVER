# RAG-SERVER 开发规范

> 基于Spring Boot 3.x + Spring AI的企业级RAG框架
>
> **版本**: v1.0.0  
> **最后更新**: 2025-01-XX  
> **技术栈**: Spring Boot 3.2+ | Spring AI 1.0.0-M3 | 阿里云DashScope | Milvus 2.5+

---

# 目录

1. [项目概述](#1-项目概述)
2. [技术架构](#2-技术架构)
3. [核心组件设计](#3-核心组件设计)
4. [数据模型](#4-数据模型)
5. [开发阶段与任务](#5-开发阶段与任务)
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
| **框架** | Spring Boot | 3.2.1 | 服务框架 |
| **AI抽象** | Spring AI | 1.0.0-M3 | 仅ChatClient/EmbeddingClient |
| **LLM** | 阿里云DashScope | API v1 | qwen-max |
| **Embedding** | DashScope Embedding | API v1 | text-embedding-v4 (2048维) |
| **向量库** | Milvus | 2.5+ | 混合检索（Dense+Sparse） |
| **数据库** | H2/SQLite | - | 元数据存储 |
| **PDF** | Apache PDFBox | 3.0.8 | 文档加载 |
| **MCP** | MCP Java SDK | 2.0.0 | 协议实现 |
| **测试** | Spring Boot Test | 内置 | JUnit 5 + Testcontainers |

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

# 5. 开发阶段与任务

## 5.1 阶段总览

| 阶段 | 目的 | 核心任务 | 可选任务 | 总计 | 优先级 |
|-----|------|---------|---------|------|--------|
| A | Spring Boot初始化 | 3 | 0 | 3 | P0 |
| B | DashScope集成 | 3 | 1 | 4 | P0 |
| C | Milvus集成 | 5 | 0 | 5 | P0 |
| D | 文档摄取Pipeline | 6 | 2 | 8 | P0 |
| E | RAG检索与生成 | 5 | 2 | 7 | P0 |
| F | MCP协议集成 | 4 | 1 | 5 | P0 |
| G | 元数据与管理 | 5 | 1 | 6 | P1 |
| H | Dashboard | 4 | 2 | 6 | P1 |
| I | 评估与优化 | 4 | 1 | 5 | P1 |
| J | 端到端验收 | 3 | 1 | 4 | P0 |
| **总计** | | **42** | **11** | **53** | |

**说明**：
- **P0（核心任务）**: RAG基本流程必需，42个任务
- **P1（可选任务）**: 增强功能，可后续补充，11个任务

---

## 阶段A：Spring Boot工程初始化（3个任务）

### A1：Maven项目初始化

**目标**：创建Spring Boot 3.2项目骨架

**修改文件**：
- `pom.xml`
- `.gitignore`
- `src/main/java/com/ragserver/RagServerApplication.java`
- `src/main/resources/application.yaml`

**验收标准**：
- `mvn spring-boot:run` 成功启动
- Actuator endpoint `/actuator/health` 返回UP

**测试方法**：`mvn clean test`

---

### A2：配置管理体系

**目标**：建立分环境配置

**修改文件**：
- `application.yaml`（通用）
- `application-dev.yaml`（开发）
- `application-prod.yaml`（生产）
- `application-test.yaml`（测试）

**配置内容**：
```yaml
spring:
  application:
    name: rag-server
  profiles:
    active: dev

spring.ai.dashscope:
  api-key: ${DASHSCOPE_API_KEY}
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model: qwen-max
  qps: 10

milvus:
  uri: http://localhost:19530
  collection-name: rag_knowledge_hub
  auto-create-collection: true
```

**验收标准**：
- 环境变量`${DASHSCOPE_API_KEY}`正确解析
- `@ConfigurationProperties`自动装配

**测试方法**：单元测试读取配置

---

### A3：数据库初始化

**目标**：建立H2/SQLite数据源

**修改文件**：
- `DataSourceConfig.java`
- `src/main/resources/db/migration/V1__init_schema.sql`（Flyway）
- `IngestionHistoryRepository.java`
- `ImageIndexRepository.java`

**Schema**：
```sql
CREATE TABLE ingestion_history (
    file_hash VARCHAR(64) PRIMARY KEY,
    file_path VARCHAR(512) NOT NULL,
    file_size BIGINT,
    status VARCHAR(20),
    processed_at TIMESTAMP,
    error_msg TEXT,
    chunk_count INTEGER
);

CREATE TABLE image_index (
    image_id VARCHAR(64) PRIMARY KEY,
    file_path VARCHAR(512),
    collection VARCHAR(128),
    doc_hash VARCHAR(64),
    page_num INTEGER,
    created_at TIMESTAMP
);
```

**验收标准**：
- 启动后两表自动创建
- Repository CRUD操作正常

**测试方法**：`@DataJpaTest`

---

## 阶段B：DashScope集成（4个任务 = 3核心 + 1可选）

### B1：DashScopeChatClient实现（P0）

**目标**：实现Spring AI的ChatClient接口

**修改文件**：
- `ai/dashscope/DashScopeChatClient.java`
- `ai/dashscope/DashScopeProperties.java`
- `config/AiConfig.java`

**实现要点**：
- HTTP调用DashScope API
- 解析响应为`ChatResponse`
- 异常处理与重试（3次，指数退避）

**验收标准**：
- `chatClient.call(new Prompt("你好"))`返回正常
- 超时/网络错误有合理异常

**测试方法**：
- 单元测试：Mock RestTemplate
- 集成测试：真实API调用

---

### B2：DashScopeEmbeddingClient实现（P0）

**目标**：实现EmbeddingClient接口

**修改文件**：
- `ai/dashscope/DashScopeEmbeddingClient.java`

**实现要点**：
- 批量处理（batch_size=16）
- 返回2048维向量
- 性能优化（并发调用）

**验收标准**：
- `embeddingClient.embed("测试")`返回2048维
- 批量embed 100条文本<5秒

**测试方法**：
- 单元测试：Mock HTTP
- 集成测试：真实API + 性能测试

---

### B3：API限流与重试（P0）

**目标**：防止超过DashScope限流

**修改文件**：
- `ai/dashscope/RateLimiter.java`

**实现**：
- 令牌桶算法（默认10 QPS）
- 指数退避重试
- 429状态码处理

**验收标准**：
- 超限时阻塞等待
- 返回429自动重试

**测试方法**：压力测试（100并发）

---

### B4：DashScope Vision LLM（P1 - 可选）

**目标**：图片描述能力

**修改文件**：
- `ai/dashscope/DashScopeVisionClient.java`

**实现**：调用qwen-vl-max API

**原型阶段提示**：可暂时跳过，先验证纯文本RAG

---

## 阶段C：Milvus集成（5个任务）

### C1：Milvus连接与Schema创建（P0）

**目标**：建立Milvus客户端并创建Collection

**修改文件**：
- `retrieval/milvus/MilvusHybridStore.java`
- `retrieval/milvus/MilvusProperties.java`
- `config/MilvusConfig.java`

**Schema设计**：
```
Collection: rag_knowledge_hub
Fields:
  - id: VARCHAR(128, Primary Key)
  - text: VARCHAR(65535)
  - dense_vector: FLOAT_VECTOR(2048)
  - sparse_vector: SPARSE_FLOAT_VECTOR
  - metadata: JSON
Indexes:
  - dense_vector: AUTOINDEX / COSINE
  - sparse_vector: SPARSE_INVERTED_INDEX / IP
```

**验收标准**：
- 连接Milvus成功
- 首次启动自动创建Collection
- 重复启动跳过

**测试方法**：集成测试（Testcontainers启动Milvus）

---

### C2：Dense检索实现（P0）

**目标**：语义相似度检索

**实现**：
```java
public List<Document> searchDense(String query, int topK) {
    List<Double> queryVector = embeddingClient.embed(query);
    SearchParam param = SearchParam.newBuilder()
        .withVectorFieldName("dense_vector")
        .withVectors(List.of(queryVector))
        .withTopK(topK)
        .withMetricType(MetricType.COSINE)
        .build();
    return parseResults(milvusClient.search(param));
}
```

**验收标准**：
- 查询返回相关文档
- 按余弦相似度排序

**测试方法**：集成测试（预置数据→查询→验证排序）

---

### C3：Sparse检索实现（BM25）（P0）

**目标**：关键词检索

**修改文件**：
- `retrieval/BM25Encoder.java`
- `retrieval/milvus/MilvusHybridStore.java`

**实现方案**：
- **方案A**：使用Milvus 2.5+ BM25内置函数
- **方案B**：自己实现BM25算法（分词+TF-IDF）

**验收标准**：
- 关键词匹配文档召回
- 稀疏向量格式正确

**测试方法**：关键词查询测试

---

### C4：RRF融合实现（P0）

**目标**：融合Dense和Sparse结果

**算法**：
```
score(doc) = Σ 1/(k + rank_in_list_i)
k = 60
```

**验收标准**：
- 融合结果兼顾语义和关键词
- 重复文档去重

**测试方法**：对比单路vs融合的Hit@5

---

### C5：批量Upsert方法（P0）

**目标**：高效批量存储

**实现**：
```java
public void batchUpsert(List<Document> docs) {
    // 1. 批量embedding
    List<List<Double>> denseVectors = batchEmbed(docs);
    
    // 2. 批量BM25编码
    List<SparseVector> sparseVectors = batchBM25(docs);
    
    // 3. 构造InsertParam
    // 4. 批量insert
}
```

**验收标准**：
- 插入1000条文档<30秒
- 幂等性（相同ID更新）

**测试方法**：性能测试

---

## 阶段D：文档摄取Pipeline（8个任务 = 6核心 + 2可选）

### D1：PDF加载（P0）

**目标**：使用PDFBox加载PDF

**修改文件**：
- `ingestion/loader/PdfLoader.java`

**实现**：
```java
public List<Document> load(Path pdfPath) {
    PDDocument pdf = PDDocument.load(pdfPath.toFile());
    List<Document> pages = new ArrayList<>();
    
    for (int i = 0; i < pdf.getNumberOfPages(); i++) {
        PDPage page = pdf.getPage(i);
        String text = extractText(page);
        
        Map<String, Object> metadata = Map.of(
            "source_path", pdfPath.toString(),
            "page", i + 1
        );
        
        pages.add(new Document(UUID.randomUUID().toString(), text, metadata));
    }
    
    return pages;
}
```

**验收标准**：
- 多页PDF返回多个Document
- metadata包含page_number

**测试方法**：单元测试（fixtures/sample.pdf）

---

### D2：RecursiveSplitter实现（P0）

**目标**：语义感知切分

**修改文件**：
- `ingestion/splitter/RecursiveSplitter.java`

**实现**：
- 按Markdown结构递归切分（## / ### / 段落）
- chunk_size=512, overlap=128
- 元数据继承

**验收标准**：
- 长文档切分为多chunk
- chunk大小在512±20
- overlap生效

**测试方法**：单元测试

---

### D3：ChunkRefiner实现（P0）

**目标**：去噪和格式清理

**修改文件**：
- `ingestion/transformer/ChunkRefiner.java`

**实现**：
- 规则模式：去除多余空白、页眉页脚
- LLM模式（可选）：调用ChatClient精炼

**原型阶段建议**：只实现规则模式

**验收标准**：
- 噪声清理后可读性提升
- 代码块格式不破坏

**测试方法**：单元测试（noisy_chunks → 验证清理）

---

### D4：MetadataEnricher实现（P0）

**目标**：元数据增强

**修改文件**：
- `ingestion/transformer/MetadataEnricher.java`

**实现**：
- 规则模式：提取标题、关键词
- LLM模式（可选）：生成摘要和tags

**验收标准**：
- 每个chunk有title、tags
- LLM失败回退规则

**测试方法**：单元测试

---

### D5：批量Embedding编码（P0）

**目标**：高效向量化

**修改文件**：
- `service/EmbeddingService.java`

**实现**：
```java
public List<List<Double>> batchEmbed(List<String> texts) {
    return partition(texts, 16).stream()
        .map(embeddingClient::embed)
        .flatMap(List::stream)
        .collect(Collectors.toList());
}
```

**验收标准**：
- 1000条文本<30秒
- 内容哈希缓存生效

**测试方法**：性能测试

---

### D6：Pipeline编排（P0）

**目标**：串联所有摄取步骤

**修改文件**：
- `ingestion/IngestionPipeline.java`
- `service/IngestionService.java`

**流程**：
```
PDF → PdfLoader → RecursiveSplitter → ChunkRefiner 
    → MetadataEnricher → BatchEmbedding → MilvusHybridStore
```

**验收标准**：
- 完整流程跑通
- 每步失败有清晰异常

**测试方法**：集成测试

---

### D7：ImageCaptioner实现（P1 - 可选）

**目标**：图片描述生成

**修改文件**：
- `ingestion/transformer/ImageCaptioner.java`

**原型阶段提示**：可跳过，先专注文本

---

### D8：增量摄取与去重（P0）

**目标**：SHA256去重

**修改文件**：
- `ingestion/loader/FileIntegrityService.java`
- `repository/IngestionHistoryRepository.java`

**实现**：
```java
public boolean shouldSkip(Path file) {
    String hash = computeSHA256(file);
    return ingestionHistoryRepo.existsByFileHashAndStatus(
        hash, IngestionStatus.SUCCESS
    );
}
```

**验收标准**：
- 重复文件跳过
- 修改后重新摄取

**测试方法**：集成测试

---

## 阶段E：RAG检索与生成（7个任务 = 5核心 + 2可选）

### E1：HybridRetriever实现（P0）

**目标**：协调Dense + Sparse检索

**修改文件**：
- `retrieval/HybridRetriever.java`

**实现**（见3.2.2节）

**验收标准**：
- 两路并行执行
- 任一路失败可降级

**测试方法**：单元测试（Mock两路结果）

---

### E2：Reranker实现（P0）

**目标**：LLM精排

**修改文件**：
- `retrieval/RerankerService.java`

**实现**：
```java
public List<Document> rerank(String query, List<Document> candidates) {
    String prompt = buildRerankPrompt(query, candidates);
    ChatResponse response = chatClient.call(new Prompt(prompt));
    return parseRankedDocs(response);
}
```

**验收标准**：
- 排序结果相关性提升
- 超时回退原排序

**测试方法**：集成测试

---

### E3：Prompt模板管理（P0）

**目标**：结构化Prompt

**修改文件**：
- `resources/prompts/rag-query.st`
- `service/PromptService.java`

**模板**：
```
你是专业知识库助手。基于以下上下文回答问题。

上下文:
{{#context}}
文档: {{source_path}}
内容: {{content}}
{{/context}}

问题: {{query}}

要求：
1. 仅基于上下文
2. 引用来源
3. 不知道请说明
```

**验收标准**：模板渲染正确

**测试方法**：单元测试

---

### E4：RagService核心实现（P0）

**目标**：串联检索→Rerank→生成

**修改文件**：
- `service/RagService.java`

**实现**（见3.4.1节）

**验收标准**：完整RAG流程返回答案

**测试方法**：集成测试

---

### E5：Citation生成（P0）

**目标**：标注来源

**修改文件**：
- `service/PromptService.java`

**格式**：
```
引用来源:
[1] document.pdf, 第5页
[2] guide.md, 第2节
```

**验收标准**：引用信息准确

**测试方法**：单元测试

---

### E6：流式输出支持（P1 - 可选）

**目标**：SSE流式返回

**修改文件**：
- `controller/QueryController.java`

**实现**：
```java
@GetMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> queryStream(@RequestParam String q) {
    return ragService.queryStream(q);
}
```

---

### E7：缓存策略（P1 - 可选）

**目标**：缓存高频查询

**修改文件**：
- `config/CacheConfig.java`

**实现**：Spring Cache + Caffeine

---

## 阶段F：MCP协议集成（5个任务 = 4核心 + 1可选）

### F1：MCP Server初始化（P0）

**目标**：启动stdio模式

**修改文件**：
- `mcp/McpServerRunner.java`

**实现**：
```java
@Component
public class McpServerRunner implements CommandLineRunner {
    @Override
    public void run(String... args) {
        McpServer server = McpServer.sync(new StdioServerTransport());
        server.setServerInfo(new ServerInfo("rag-server", "1.0.0"));
        toolsProvider.registerTools(server);
        server.start();  // 阻塞
    }
}
```

**验收标准**：
- 启动后监听stdin/stdout
- 初始化请求返回capabilities

**测试方法**：E2E测试

---

### F2：query_knowledge_hub工具（P0）

**目标**：暴露RAG查询

**修改文件**：
- `mcp/tools/QueryTool.java`

**Tool定义**：
```java
ToolSpecification.builder()
    .name("query_knowledge_hub")
    .description("混合检索知识库并生成答案")
    .parameters(JsonSchema.object()
        .property("query", JsonSchema.string())
        .property("collection", JsonSchema.string())
        .required("query")
    )
    .build()
```

**验收标准**：MCP Client调用返回答案

**测试方法**：E2E测试

---

### F3：list_collections工具（P0）

**目标**：列出集合

**实现**：查询Milvus所有Collection

**验收标准**：返回集合列表

---

### F4：get_document_summary工具（P0）

**目标**：获取文档摘要

**输入**：source_path  
**输出**：元数据（标题、摘要、chunk数）

**验收标准**：返回正确元数据

---

### F5：MCP异常处理（P1 - 可选）

**目标**：规范化错误响应

---

## 阶段G：元数据与管理（6个任务 = 5核心 + 1可选）

### G1：DocumentService实现（P0）

**目标**：文档生命周期管理

**修改文件**：
- `service/DocumentService.java`

**功能**：
- `listDocuments(collection)`
- `getDocumentDetail(docId)`
- `deleteDocument(sourcePath)`
- `getCollectionStats()`

**验收标准**：
- list返回文档及统计
- delete联动清理

**测试方法**：集成测试

---

### G2：ImageStorage实现（P0）

**目标**：图片文件管理

**修改文件**：
- `service/ImageStorageService.java`
- `repository/ImageIndexRepository.java`

**功能**：
- `save(imageData, metadata)`
- `find(imageId)`
- `deleteByDoc(docHash)`

---

### G3：IngestionHistoryRepository（P0）

**目标**：摄取历史管理

**修改文件**：
- `repository/IngestionHistoryRepository.java`

**实现**：Spring Data JPA接口

---

### G4：批量删除功能（P0）

**目标**：按collection删除

**实现**：
```java
@Transactional
public void deleteCollection(String collection) {
    vectorStore.deleteByMetadata("collection", collection);
    imageStorageService.deleteByCollection(collection);
    ingestionHistoryRepo.deleteByCollection(collection);
}
```

---

### G5：文档统计API（P0）

**目标**：暴露统计信息

**API**：
- `GET /api/stats/overview`
- `GET /api/stats/collections`

---

### G6：搜索历史记录（P1 - 可选）

**目标**：记录查询历史

---

## 阶段H：Dashboard（6个任务 = 4核心 + 2可选）

### H1：REST API设计（P0）

**目标**：暴露管理接口

**API列表**：
```
POST   /api/ingest
GET    /api/documents
GET    /api/documents/{id}
DELETE /api/documents/{id}
POST   /api/query
GET    /api/collections
GET    /api/stats/overview
```

**验收标准**：Swagger文档生成

---

### H2：前端页面骨架（P0）

**技术选型**：
- Vue 3 / React（SPA）
- 或Thymeleaf（SSE）

**页面**：
- 系统总览
- 文档浏览器
- Ingestion管理
- Query测试

---

### H3：文档浏览器（P0）

**功能**：
- 列表：source、collection、chunk数
- 详情：chunks、metadata

---

### H4：Ingestion管理（P0）

**功能**：
- 文件上传
- 进度条
- 错误提示

---

### H5：Query测试页面（P1 - 可选）

---

### H6：系统总览页面（P1 - 可选）

---

## 阶段I：评估与优化（5个任务 = 4核心 + 1可选）

### I1：黄金测试集构建（P0）

**目标**：构建评估数据

**修改文件**：
- `resources/fixtures/golden_test_set.json`

**格式**：
```json
{
  "testCases": [
    {
      "query": "如何配置Milvus？",
      "expectedChunkIds": ["doc1_0001"],
      "expectedSources": ["milvus_guide.pdf"]
    }
  ]
}
```

**验收标准**：至少20条

---

### I2：评估指标实现（P0）

**目标**：计算Hit Rate、MRR、NDCG

**修改文件**：
- `evaluation/MetricsCalculator.java`

---

### I3：EvaluationService实现（P0）

**目标**：批量评估

**修改文件**：
- `service/EvaluationService.java`

---

### I4：回归测试基线（P0）

**目标**：建立质量基线

**修改文件**：
- `test/java/com/ragserver/e2e/RegressionTest.java`

**实现**：
```java
@Test
void testRetrievalQuality() {
    EvaluationReport report = evaluationService.evaluate(
        "classpath:fixtures/golden_test_set.json"
    );
    assertThat(report.getHitRate5()).isGreaterThanOrEqualTo(0.85);
}
```

---

### I5：性能优化（P1 - 可选）

**优化点**：
- Embedding缓存
- 连接池
- 异步处理

---

## 阶段J：端到端验收（4个任务 = 3核心 + 1可选）

### J1：完整摄取测试（P0）

**测试场景**：
- 摄取10个PDF（共100页）
- 验证Milvus chunk数量
- 验证元数据完整性

---

### J2：完整RAG查询测试（P0）

**测试场景**：
- 预置知识库
- 执行20个测试问题
- 验证Hit@5 > 0.85

---

### J3：MCP兼容性测试（P0）

**测试场景**：
- 模拟Claude Desktop调用
- 验证三个工具响应

---

### J4：文档与示例（P1 - 可选）

**修改文件**：
- `README.md`
- `docs/DEPLOYMENT.md`
- `docs/API.md`

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
