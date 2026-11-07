# RAG-SERVER 项目进度文档

**最后更新**: 2026-11-07  
**当前阶段**: Docker 环境配置完成

---

## 📊 当前完成进度

### ✅ 已完成的工作

#### 1. 文档完善（2026-11-07）
- ✅ 更新 `CLAUDE.md` - 添加完整的开发指南、架构设计、常用命令
- ✅ 更新 `README.md` - 优化项目介绍、快速开始、技术栈表格
- ✅ 更新 `.gitignore` - 添加环境变量文件排除规则
- ✅ 提交记录：`57abb33 docs: 完善项目文档`

#### 2. Docker 环境配置（2026-11-07）
- ✅ 创建 `docker-compose.yml` - 完整的服务编排配置
- ✅ 配置 Milvus 2.5.3（向量数据库）
  - 原生支持混合检索 + 内置 BM25
  - 解决端口冲突问题（移除自定义配置，使用默认配置）
  - 修复 Etcd 网络配置（advertise-client-urls）
- ✅ 配置 MySQL 8.0（关系型数据库）
  - 创建初始化脚本（4 个表）
  - 优化性能配置（1GB buffer pool, 500 max connections）
- ✅ 配置 Redis 7.2（缓存）
  - 修复配置文件语法错误
  - 配置持久化（AOF + RDB）
  - LRU 淘汰策略，512MB 内存限制
- ✅ 添加 Attu 管理界面（Milvus 可视化）
- ✅ 创建配置文件：
  - `config/milvus.yaml`
  - `config/mysql/init.sql`
  - `config/mysql/my.cnf`
  - `config/redis/redis.conf`
- ✅ 创建 `docs/DOCKER_GUIDE.md` - 完整的 Docker 部署指南
- ✅ 所有服务启动成功并运行正常
- ✅ 提交记录：`672461f feat: 配置 Docker 开发环境`

#### 3. 服务验证
- ✅ Milvus 健康检查通过（localhost:19530）
- ✅ MySQL 可连接（localhost:3306, raguser/ragpassword）
- ✅ Redis 可连接（localhost:6379）
- ✅ Attu 可访问（localhost:8000）
- ✅ MinIO Console 可访问（localhost:9001, minioadmin/minioadmin）

---

## 🚧 待完成的工作

### 阶段 1: Spring Boot 配置更新（高优先级）

#### 1.1 更新 pom.xml 依赖

**要做什么**：
```xml
<!-- 添加 MySQL 驱动 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- 添加 Spring Data Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- 添加 Redis 连接池 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>

<!-- 移除 H2 数据库（如果存在） -->
<!-- 删除 com.h2database:h2 依赖 -->
```

**为什么**：
- Spring Boot 需要 MySQL 驱动才能连接 MySQL 数据库
- 需要 Redis 依赖才能使用 Redis 缓存
- 连接池提高 Redis 连接性能
- 移除 H2 避免数据库冲突

**验证**：
```bash
mvn dependency:tree | grep mysql
mvn dependency:tree | grep redis
```

---

#### 1.2 更新 application.yaml 配置

**要做什么**：

**A. 修改数据源配置**
```yaml
spring:
  datasource:
    # 改为 MySQL（原来可能是 H2）
    url: jdbc:mysql://localhost:3306/rag_server?useUnicode=true&characterEncoding=utf8mb4&useSSL=false&serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: raguser
    password: ragpassword
    
    # HikariCP 连接池配置
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**B. 修改 JPA 配置**
```yaml
spring:
  jpa:
    database-platform: org.hibernate.dialect.MySQLDialect
    hibernate:
      ddl-auto: validate  # 生产环境用 validate，不自动修改表
    show-sql: false  # 生产环境关闭 SQL 日志
```

**C. 添加 Redis 配置**
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      # password: # 如果设置了密码
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms
      timeout: 3000ms
  
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1小时过期
      cache-null-values: false
```

**D. 确认 Milvus 配置**
```yaml
milvus:
  uri: http://localhost:19530
  collection-name: rag_knowledge_hub
  auto-create-collection: true
```

**为什么**：
- 指向正确的数据库服务器（MySQL 而不是 H2）
- 配置连接池，避免连接耗尽
- `ddl-auto: validate` 保护数据库，防止 Hibernate 自动删表
- Redis 配置让 Spring Boot 知道如何连接 Redis
- 缓存 TTL 避免数据永不过期占用内存

**文件位置**：
- `src/main/resources/application.yaml`

---

#### 1.3 验证 MySQL 数据库表

**要做什么**：
1. 用 Navicat 连接 MySQL：
   - Host: localhost
   - Port: 3306
   - Username: raguser
   - Password: ragpassword
   - Database: rag_server

2. 检查是否存在 4 个表：
   - `ingestion_history` - 文档摄取历史（file_hash 主键）
   - `image_index` - 图片索引（image_id 主键）
   - `query_history` - 查询历史（query_id 主键）
   - `collection_stats` - Collection 统计（collection_name 主键）

3. 如果表不存在，手动执行初始化脚本：
   ```sql
   -- 在 Navicat 中打开并执行
   D:\Dev\Workspace\Projects\RAG-SERVER\config\mysql\init.sql
   ```

**为什么**：
- 确认 Docker 的 MySQL 初始化脚本已执行
- JPA 实体类需要对应的表才能工作
- `ddl-auto: validate` 会验证表结构，表不存在会启动失败

**验证方法**：
```sql
-- 在 Navicat 中执行
SHOW TABLES;
DESC ingestion_history;
DESC image_index;
DESC query_history;
DESC collection_stats;
```

---

### 阶段 2: Java 配置类更新（高优先级）

#### 2.1 创建 Redis 配置类

**要做什么**：

创建 `src/main/java/com/ragserver/config/RedisConfig.java`：

```java
package com.ragserver.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 使用 Jackson2JsonRedisSerializer 序列化
        ObjectMapper om = new ObjectMapper();
        om.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        Jackson2JsonRedisSerializer<Object> serializer = 
            new Jackson2JsonRedisSerializer<>(om, Object.class);
        
        // Key 使用 String 序列化
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        // 配置序列化
        ObjectMapper om = new ObjectMapper();
        om.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        Jackson2JsonRedisSerializer<Object> serializer = 
            new Jackson2JsonRedisSerializer<>(om, Object.class);
        
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))  // 默认 1 小时过期
            .disableCachingNullValues()  // 不缓存 null 值
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            );
        
        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
    }
}
```

**为什么**：
- **序列化配置**：默认 JDK 序列化不可读、效率低，改用 JSON
- **CacheManager**：统一管理缓存，支持 `@Cacheable` 注解
- **TTL 配置**：避免缓存永不过期，占用 Redis 内存
- **不缓存 null**：避免缓存穿透问题

**如果文件已存在**：
- 检查是否已配置 JSON 序列化
- 检查是否已配置 CacheManager
- 确保 `@EnableCaching` 注解存在

---

#### 2.2 检查 Milvus 配置类

**要做什么**：

检查 `src/main/java/com/ragserver/config/MilvusProperties.java`：

```java
package com.ragserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {
    
    private String uri = "http://localhost:19530";
    
    private String collectionName = "rag_knowledge_hub";
    
    private Boolean autoCreateCollection = true;
    
    // 可以添加其他配置项
}
```

检查 `src/main/java/com/ragserver/config/MilvusConfig.java`：

```java
package com.ragserver.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MilvusConfig {
    
    private final MilvusProperties milvusProperties;
    
    @Bean
    public MilvusServiceClient milvusClient() {
        return new MilvusServiceClient(
            ConnectParam.newBuilder()
                .withUri(milvusProperties.getUri())
                // 如果需要认证
                // .withToken("root:Milvus")
                .build()
        );
    }
}
```

**为什么**：
- **ConfigurationProperties**：将 YAML 配置映射到 Java 对象
- **类型安全**：编译期检查，避免配置错误
- **IDE 支持**：自动补全和提示
- **统一管理**：所有 Milvus 配置在一个地方

**如果文件已存在**：
- 确保读取的是 `milvus.uri`（而不是硬编码）
- 确保 `@ConfigurationProperties` 注解正确

---

### 阶段 3: 添加缓存逻辑（中优先级）

#### 3.1 在 DashScopeEmbeddingClient 中添加缓存

**要做什么**：

修改 `src/main/java/com/ragserver/ai/dashscope/DashScopeEmbeddingClient.java`：

**方案 A：使用 @Cacheable 注解（推荐）**

```java
package com.ragserver.ai.dashscope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashScopeEmbeddingClient {
    
    // ... 其他代码 ...
    
    /**
     * 获取文本的 Embedding（带缓存）
     * 
     * @param text 文本内容
     * @return Embedding 向量
     */
    @Cacheable(value = "embeddings", key = "#text.hashCode()")
    public List<Double> embed(String text) {
        log.info("Calling DashScope API for embedding (cache miss)");
        
        // 实际调用 DashScope API
        // 结果会自动缓存到 Redis
        return callDashScopeApi(text);
    }
    
    // ... 其他代码 ...
}
```

**方案 B：手动缓存（更灵活）**

```java
package com.ragserver.ai.dashscope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashScopeEmbeddingClient {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // ... 其他代码 ...
    
    /**
     * 获取文本的 Embedding（手动缓存）
     * 
     * @param text 文本内容
     * @return Embedding 向量
     */
    public List<Double> embed(String text) {
        // 1. 生成缓存 Key（使用文本的 SHA256 哈希）
        String cacheKey = "embedding:" + DigestUtils.sha256Hex(text);
        
        // 2. 尝试从 Redis 获取
        @SuppressWarnings("unchecked")
        List<Double> cached = (List<Double>) redisTemplate
            .opsForValue().get(cacheKey);
        
        if (cached != null) {
            log.debug("Cache hit for embedding, key: {}", cacheKey);
            return cached;
        }
        
        // 3. 缓存未命中，调用 API
        log.info("Cache miss, calling DashScope API for embedding");
        List<Double> embedding = callDashScopeApi(text);
        
        // 4. 存入 Redis，1小时过期
        redisTemplate.opsForValue()
            .set(cacheKey, embedding, Duration.ofHours(1));
        
        return embedding;
    }
    
    // ... 其他代码 ...
}
```

**为什么**：
- **节省成本**：相同文本的 Embedding 不会变，缓存可节省 90%+ API 调用
- **提升性能**：API 延迟 100-500ms，Redis 延迟 1-5ms，性能提升 100 倍
- **降低依赖**：API 不可用时，缓存数据仍可用
- **使用 SHA256**：hashCode() 可能冲突，SHA256 更安全

**选择建议**：
- 简单场景用方案 A（@Cacheable）
- 需要精细控制用方案 B（手动缓存）

---

### 阶段 4: 测试应用启动（高优先级）

#### 4.1 启动应用

**要做什么**：

```bash
# 方式 1：使用 Maven
mvn spring-boot:run

# 方式 2：使用 IDE
# 在 IDE 中运行 RagServerApplication.java 的 main 方法
```

**观察启动日志**，确认以下信息：

**✅ 成功的标志**：
```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
Initialized JPA EntityManagerFactory for persistence unit 'default'
Lettuce connection created: PooledConnection[xxx]
Started RagServerApplication in 8.234 seconds
```

**❌ 失败的标志**：
```
Failed to configure a DataSource
Communications link failure
Could not connect to Redis
Schema-validation: wrong column type
```

**为什么**：
- 验证所有配置是否正确
- 及早发现问题，及早修复
- 确保基础环境 OK 后再开发业务代码

---

#### 4.2 验证连接

**要做什么**：

**A. 验证 MySQL 连接**
```bash
# 查看日志中是否有
# "HikariPool-1 - Start completed"

# 或者访问健康检查端点
curl http://localhost:8080/actuator/health
```

**B. 验证 Redis 连接**
```java
// 创建测试 Controller
@RestController
@RequiredArgsConstructor
public class TestController {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    @GetMapping("/test/redis")
    public String testRedis() {
        redisTemplate.opsForValue().set("test", "hello", Duration.ofMinutes(1));
        String value = (String) redisTemplate.opsForValue().get("test");
        return "Redis test: " + value;
    }
}
```

访问 `http://localhost:8080/test/redis`，应该返回 "Redis test: hello"

**C. 验证 Milvus 连接**
```java
// 在测试 Controller 中添加
@GetMapping("/test/milvus")
public String testMilvus() {
    try {
        milvusClient.listCollections();
        return "Milvus connection OK";
    } catch (Exception e) {
        return "Milvus connection failed: " + e.getMessage();
    }
}
```

访问 `http://localhost:8080/test/milvus`，应该返回 "Milvus connection OK"

**为什么**：
- 启动成功不代表连接成功
- 需要实际调用 API 验证
- 测试端点可以快速诊断问题

---

### 阶段 5: 代码调整（低优先级）

#### 5.1 适配 Milvus 2.5 内置 BM25

**背景**：
- Milvus 2.5 原生支持 BM25，不需要手动实现稀疏向量编码
- 可以简化 `BM25Encoder.java` 的实现

**要做什么**（可选）：
- 研究 Milvus 2.5 的 BM25 API
- 重构 `MilvusHybridStore.java` 使用原生 API
- 删除或简化自己实现的 BM25 编码器

**为什么**：
- 使用官方实现更稳定、性能更好
- 减少自己维护的代码量
- 享受 Milvus 后续的优化

**注意**：
- 这不是必须的，当前实现也能工作
- 建议在基础功能稳定后再优化

---

## 🔧 潜在问题及解决方案

### 问题 1：JPA 实体与表结构不匹配

**现象**：
```
Schema-validation: wrong column type encountered in column [xxx]
```

**原因**：
- Java 实体类字段类型与数据库表字段类型不一致
- 例如：Java 用 `Integer`，MySQL 用 `BIGINT`

**解决方案**：
1. 检查报错的字段
2. 对比 Entity 类和 `init.sql` 中的类型
3. 修改其中一个使其一致

**常见类型对应**：
- Java `Long` → MySQL `BIGINT`
- Java `Integer` → MySQL `INT`
- Java `String` → MySQL `VARCHAR(n)`
- Java `LocalDateTime` → MySQL `TIMESTAMP`

---

### 问题 2：MySQL 连接失败

**现象**：
```
Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago
```

**原因**：
- MySQL 容器未启动
- 连接字符串错误
- 防火墙阻止连接
- 用户名密码错误

**解决方案**：
```bash
# 1. 检查 MySQL 容器状态
docker-compose ps mysql

# 2. 检查 MySQL 日志
docker-compose logs mysql

# 3. 测试连接
docker-compose exec mysql mysql -uraguser -pragpassword -e "SELECT 1;"

# 4. 检查端口是否开放
netstat -ano | findstr :3306

# 5. 用 Navicat 测试连接
```

---

### 问题 3：Redis 连接失败

**现象**：
```
Cannot get Jedis connection; nested exception is redis.clients.jedis.exceptions.JedisConnectionException
```

**原因**：
- Redis 容器未启动
- 连接配置错误
- 密码不匹配（如果设置了密码）

**解决方案**：
```bash
# 1. 检查 Redis 容器状态
docker-compose ps redis

# 2. 测试连接
docker-compose exec redis redis-cli ping

# 3. 检查端口
netstat -ano | findstr :6379

# 4. 用 Another Redis Desktop Manager 测试连接
```

---

### 问题 4：依赖下载失败

**现象**：
```
Failed to read artifact descriptor for com.mysql:mysql-connector-j
```

**原因**：
- Maven 镜像源问题
- 网络问题
- 依赖版本不存在

**解决方案**：
```bash
# 1. 配置 Maven 镜像（~/.m2/settings.xml）
# 添加阿里云镜像

# 2. 清理缓存重试
mvn clean
mvn dependency:purge-local-repository
mvn install

# 3. 使用 VPN（如果有）
```

---

### 问题 5：Hibernate 自动创建表失败

**现象**：
```
Table 'rag_server.ingestion_history' doesn't exist
```

**原因**：
- `ddl-auto: validate` 不会自动创建表
- MySQL 初始化脚本未执行
- 数据库名称错误

**解决方案**：
```bash
# 1. 检查数据库是否存在
docker-compose exec mysql mysql -uroot -prootpassword -e "SHOW DATABASES;"

# 2. 手动执行初始化脚本
docker-compose exec mysql mysql -uroot -prootpassword rag_server < config/mysql/init.sql

# 3. 或在 Navicat 中执行 init.sql

# 4. 验证表是否创建
docker-compose exec mysql mysql -uraguser -pragpassword -e "USE rag_server; SHOW TABLES;"
```

---

## 📚 参考文档

### 项目文档
- [CLAUDE.md](../CLAUDE.md) - Claude Code 工作指南
- [README.md](../README.md) - 项目说明
- [DEV_SPEC.md](../DEV_SPEC.md) - 开发规范（53 个任务）
- [docs/DOCKER_GUIDE.md](../docs/DOCKER_GUIDE.md) - Docker 完整指南

### 配置文件位置
- `docker-compose.yml` - Docker 编排配置
- `config/milvus.yaml` - Milvus 配置
- `config/mysql/init.sql` - MySQL 初始化脚本
- `config/mysql/my.cnf` - MySQL 优化配置
- `config/redis/redis.conf` - Redis 配置
- `src/main/resources/application.yaml` - Spring Boot 主配置

### Docker 服务访问
- **Milvus**: localhost:19530
- **Attu**: http://localhost:8000
- **MySQL**: localhost:3306 (raguser/ragpassword)
- **Redis**: localhost:6379
- **MinIO Console**: http://localhost:9001 (minioadmin/minioadmin)
- **Prometheus Metrics**: http://localhost:9091/metrics

### 有用的命令
```bash
# Docker 管理
docker-compose ps                    # 查看服务状态
docker-compose logs [service]        # 查看日志
docker-compose restart [service]     # 重启服务
docker-compose down                  # 停止所有服务

# Maven 管理
mvn spring-boot:run                  # 启动应用
mvn clean compile                    # 编译项目
mvn dependency:tree                  # 查看依赖树

# 健康检查
curl http://localhost:9091/healthz  # Milvus 健康检查
curl http://localhost:8080/actuator/health  # 应用健康检查
```

---

## ✅ 验收标准

完成所有待办工作后，应该达到以下状态：

### 基础环境
- [ ] Docker 所有服务健康运行
- [ ] MySQL 数据库有 4 个表
- [ ] Redis 可以读写数据
- [ ] Milvus 可以连接

### 应用配置
- [ ] pom.xml 包含 MySQL、Redis 依赖
- [ ] application.yaml 配置正确（MySQL、Redis、Milvus）
- [ ] RedisConfig 类存在且配置正确
- [ ] MilvusProperties 类读取正确配置

### 应用启动
- [ ] 应用可以成功启动
- [ ] 启动日志无错误
- [ ] 健康检查端点返回 UP
- [ ] 可以连接所有服务（MySQL、Redis、Milvus）

### 缓存功能
- [ ] Redis 缓存配置生效
- [ ] Embedding 调用有缓存
- [ ] 缓存命中可以节省 API 调用

---

## 🎯 下一个 Agent 开始工作的步骤

1. **阅读本文档**，了解当前进度
2. **检查 Docker 服务状态**：`docker-compose ps`
3. **从阶段 1.1 开始**：更新 pom.xml
4. **逐步完成每个阶段**
5. **遇到问题参考**"潜在问题及解决方案"章节
6. **完成后更新本文档**，记录新的进度

---

**祝工作顺利！有问题随时查看文档或询问用户。**
