package com.liuqitech.codeagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * AI 配置类
 * 配置 Spring AI 的 ChatMemory 和 RestClient
 *
 * 说明：
 * 1. ChatMemory 用于管理对话历史，使用 MessageWindowChatMemory 实现
 * 2. RestClient.Builder 配置超时和日志拦截器
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    private final AgentProperties agentProperties;

    public AiConfig(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * 配置 ChatMemory Bean
     * 使用 MessageWindowChatMemory 实现滑动窗口式的消息历史管理
     *
     * @return ChatMemory 实例
     */
    @Bean
    public ChatMemory chatMemory() {
        int maxMessages = agentProperties.getMaxHistory();
        log.info("配置 ChatMemory: 使用 MessageWindowChatMemory, 最大消息数={}", maxMessages);

        return MessageWindowChatMemory.builder()
            .maxMessages(maxMessages)
            .build();
    }

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
