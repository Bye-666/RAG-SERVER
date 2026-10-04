package com.ragserver.config;

import com.ragserver.retrieval.milvus.MilvusHybridStore;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus配置类
 *
 * <p>配置Milvus客户端和相关Bean。</p>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class MilvusConfig {

    private final MilvusProperties milvusProperties;

    public MilvusConfig(MilvusProperties milvusProperties) {
        this.milvusProperties = milvusProperties;
    }

    /**
     * 创建Milvus客户端V2
     *
     * <p>使用MilvusClientV2（新版API，推荐）。</p>
     *
     * @return MilvusClientV2实例
     */
    @Bean
    public MilvusClientV2 milvusClientV2() {
        log.info("初始化Milvus客户端：URI={}, Collection={}",
            milvusProperties.getUri(), milvusProperties.getCollectionName());

        ConnectConfig connectConfig = ConnectConfig.builder()
            .uri(milvusProperties.getUri())
            .build();

        MilvusClientV2 client = new MilvusClientV2(connectConfig);

        log.info("Milvus客户端初始化成功");

        return client;
    }

    /**
     * 创建MilvusHybridStore
     *
     * <p>封装Milvus操作的核心类。</p>
     *
     * @param milvusClient Milvus客户端
     * @return MilvusHybridStore实例
     */
    @Bean
    public MilvusHybridStore milvusHybridStore(MilvusClientV2 milvusClient) {
        MilvusHybridStore store = new MilvusHybridStore(milvusClient, milvusProperties);

        // 如果配置了自动创建Collection，则初始化
        if (milvusProperties.isAutoCreateCollection()) {
            log.info("自动创建Collection已启用，初始化Collection...");
            store.initializeCollection();
        }

        return store;
    }
}
