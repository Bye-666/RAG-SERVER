package com.ragserver.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 配置类
 *
 * <p>配置 Redis 缓存和序列化策略。</p>
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>配置 RedisTemplate：支持泛型对象的读写</li>
 *   <li>配置 CacheManager：统一管理 Spring Cache（主缓存方案）</li>
 *   <li>JSON 序列化：使用 Jackson2 代替默认的 JDK 序列化</li>
 *   <li>TTL 配置：默认 1 小时过期</li>
 * </ul>
 *
 * <h3>为什么使用 JSON 序列化</h3>
 * <ul>
 *   <li>可读性：Redis 中可以直接看到 JSON 数据，便于调试</li>
 *   <li>跨语言：其他语言也能读取 Redis 数据</li>
 *   <li>性能：比 JDK 序列化更快、体积更小</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 配置 RedisTemplate
     *
     * <p>用于手动操作 Redis 数据。</p>
     *
     * <h3>序列化配置</h3>
     * <ul>
     *   <li>Key：String 序列化（字符串键）</li>
     *   <li>Value：JSON 序列化（对象值）</li>
     *   <li>HashKey：String 序列化</li>
     *   <li>HashValue：JSON 序列化</li>
     * </ul>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * @Autowired
     * private RedisTemplate<String, Object> redisTemplate;
     *
     * // 存储数据
     * redisTemplate.opsForValue().set("key", value, Duration.ofHours(1));
     *
     * // 读取数据
     * Object value = redisTemplate.opsForValue().get("key");
     * }</pre>
     *
     * @param connectionFactory Redis 连接工厂
     * @return 配置好的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 配置 Jackson2 序列化器
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        Jackson2JsonRedisSerializer<Object> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        // Key 使用 String 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 使用 JSON 序列化
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置 Redis CacheManager（主缓存方案）
     *
     * <p>统一管理 Spring Cache，支持 {@code @Cacheable}、{@code @CachePut}、{@code @CacheEvict} 注解。</p>
     *
     * <p>使用 {@code @Primary} 注解标记为主 CacheManager，优先级高于 CacheConfig 中的 Caffeine。</p>
     *
     * <h3>缓存配置</h3>
     * <ul>
     *   <li>TTL：1 小时过期</li>
     *   <li>不缓存 null 值：避免缓存穿透</li>
     *   <li>JSON 序列化：可读性好</li>
     *   <li>分布式缓存：多实例共享</li>
     * </ul>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * @Service
     * public class EmbeddingService {
     *
     *     @Cacheable(value = "embeddings", key = "#text")
     *     public List<Double> embed(String text) {
     *         // 调用 API
     *         // 结果会自动缓存到 Redis
     *     }
     * }
     * }</pre>
     *
     * @param factory Redis 连接工厂
     * @return 配置好的 CacheManager
     */
    @Bean
    @Primary  // 标记为主 CacheManager，优先级高于 CacheConfig 中的 Caffeine
    public CacheManager redisCacheManager(RedisConnectionFactory factory) {
        // 配置 JSON 序列化
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        Jackson2JsonRedisSerializer<Object> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        // 配置缓存策略
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            // 缓存 1 小时过期
            .entryTtl(Duration.ofHours(1))
            // 不缓存 null 值（避免缓存穿透）
            .disableCachingNullValues()
            // 使用 JSON 序列化 Value
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
    }
}
