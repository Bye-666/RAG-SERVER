package com.ragserver.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * AI配置类
 *
 * <p>配置AI相关的Bean，包括：</p>
 * <ul>
 *   <li>RestTemplate：HTTP客户端</li>
 *   <li>超时设置</li>
 *   <li>连接池配置</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Configuration
public class AiConfig {

    private final DashScopeProperties dashScopeProperties;

    public AiConfig(DashScopeProperties dashScopeProperties) {
        this.dashScopeProperties = dashScopeProperties;
    }

    /**
     * 配置RestTemplate
     *
     * <p>用于DashScope API调用，配置了超时和连接参数。</p>
     *
     * <p>超时设置：</p>
     * <ul>
     *   <li>连接超时：10秒</li>
     *   <li>读取超时：从DashScopeProperties读取（默认60秒）</li>
     * </ul>
     *
     * @return RestTemplate实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // 从配置读取超时时间
        int readTimeoutMs = dashScopeProperties.getTimeoutMs();
        int connectTimeoutMs = 10000;

        // 直接创建并配置 factory，避免使用过时的 API
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        return builder
                .requestFactory(() -> factory)
                .build();
    }
}
