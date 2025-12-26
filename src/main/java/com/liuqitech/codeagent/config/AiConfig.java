package com.liuqitech.codeagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * AI 配置类
 * 配置 Spring AI 使用的 RestClient 的超时设置
 *
 * 说明：
 * 1. Spring AI 1.1.x 不支持在 application.yml 中直接配置超时
 * 2. 通过提供自定义的 RestClient.Builder Bean，Spring AI 会自动使用它
 * 3. 这种方式只影响 Spring AI 的 HTTP 客户端，不影响其他 RestClient
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * 为 Spring AI 提供自定义的 RestClient.Builder
     * Spring AI 的自动配置会注入这个 Builder 来创建 OpenAiApi
     *
     * @return 配置了超时和日志拦截器的 RestClient.Builder
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        log.info("配置 Spring AI RestClient 超时: 连接超时=90秒, 读取超时=240秒");

        // 创建超时配置
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(Duration.ofSeconds(90))    // 连接超时
            .withReadTimeout(Duration.ofSeconds(240));     // 读取超时

        // 自动检测并创建合适的 ClientHttpRequestFactory
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder
            .detect()
            .build(settings);

        log.info("配置 HTTP 请求日志拦截器，用于记录与 LLM 的完整交互过程");

        return RestClient.builder()
            .requestFactory(requestFactory)
            .requestInterceptor(new LoggingInterceptor());  // 添加日志拦截器
    }
}
