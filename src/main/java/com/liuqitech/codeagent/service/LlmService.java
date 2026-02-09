package com.liuqitech.codeagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;

/**
 * LLM 调用服务
 * 封装对 LLM 的调用逻辑，支持自动重试
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    /**
     * 错误类型枚举，统一分类 LLM 调用中的各种异常
     */
    public enum ErrorType {
        TIMEOUT,
        AUTH,
        RATE_LIMIT,
        SERVER_ERROR,
        CONNECTION,
        UNKNOWN
    }

    /**
     * 调用 LLM（阻塞模式）
     *
     * 重试策略：最多 3 次重试（含首次共 4 次），初始延迟 2 秒，指数退避（倍率 2），最大延迟 10 秒
     *
     * @param chatClient     聊天客户端
     * @param userRequest    用户请求
     * @param conversationId 会话 ID
     * @return ChatResponse
     */
    @Retryable(
        retryFor = {LlmRetryableException.class, ResourceAccessException.class},
        maxAttempts = 4,
        backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000)
    )
    public ChatResponse call(ChatClient chatClient, String userRequest, String conversationId) {
        try {
            return chatClient.prompt()
                .user(userRequest)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();
        } catch (ResourceAccessException e) {
            log.warn("[LLM 调用失败] 网络/超时异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            if (isRetryableException(e)) {
                log.warn("[LLM 调用失败] 可重试异常: {}", e.getMessage());
                throw new LlmRetryableException(e.getMessage(), e);
            }
            throw e;
        }
    }

    /**
     * 流式调用 LLM，使用 Reactor 的 retryWhen 机制
     *
     * @param chatClient     聊天客户端
     * @param userRequest    用户请求
     * @param conversationId 会话 ID
     * @return 流式响应
     */
    public Flux<String> stream(ChatClient chatClient, String userRequest, String conversationId) {
        return chatClient.prompt()
            .user(userRequest)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .stream()
            .content()
            .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofSeconds(2))
                .maxBackoff(java.time.Duration.ofSeconds(10))
                .filter(this::isRetryableException)
                .doBeforeRetry(signal ->
                    log.warn("[流式重试 {}/3] 错误: {}", signal.totalRetries() + 1, signal.failure().getMessage())
                ));
    }

    /**
     * 阻塞模式重试耗尽后的降级方法
     */
    @Recover
    public ChatResponse recover(LlmRetryableException e, ChatClient chatClient,
                                String userRequest, String conversationId) {
        log.error("[LLM 调用] 重试已耗尽: {}", e.getMessage());
        ErrorType type = classifyException(e.getCause() != null ? e.getCause() : e);
        throw new LlmCallFailedException("LLM 调用失败，已重试多次: " + getRootMessage(e), e, type);
    }

    @Recover
    public ChatResponse recover(ResourceAccessException e, ChatClient chatClient,
                                String userRequest, String conversationId) {
        log.error("[LLM 调用] 重试已耗尽（网络异常）: {}", e.getMessage());
        ErrorType type = classifyException(e);
        throw new LlmCallFailedException("LLM 调用失败，网络连接问题: " + e.getMessage(), e, type);
    }

    /**
     * 根据异常信息分类错误类型
     */
    public static ErrorType classifyException(Throwable e) {
        String message = e.toString().toLowerCase();

        if (message.contains("timeout") || message.contains("timed out")
            || message.contains("sockettimeoutexception") || message.contains("resourceaccessexception")) {
            return ErrorType.TIMEOUT;
        }
        if (message.contains("401") || message.contains("unauthorized")) {
            return ErrorType.AUTH;
        }
        if (message.contains("429") || message.contains("rate limit")) {
            return ErrorType.RATE_LIMIT;
        }
        if (message.contains("500") || message.contains("502")
            || message.contains("503") || message.contains("504")
            || message.contains("internal server error")) {
            return ErrorType.SERVER_ERROR;
        }
        if (message.contains("connection refused") || message.contains("unknownhostexception")) {
            return ErrorType.CONNECTION;
        }
        return ErrorType.UNKNOWN;
    }

    /**
     * 判断异常是否可重试
     */
    private boolean isRetryableException(Throwable e) {
        ErrorType type = classifyException(e);
        return type == ErrorType.TIMEOUT || type == ErrorType.SERVER_ERROR || type == ErrorType.RATE_LIMIT;
    }

    private String getRootMessage(Throwable e) {
        Throwable cause = e.getCause();
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause != null ? cause.getMessage() : e.getMessage();
    }

    /**
     * 可重试的 LLM 异常
     */
    public static class LlmRetryableException extends RuntimeException {
        public LlmRetryableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * LLM 调用最终失败异常（重试耗尽）
     */
    public static class LlmCallFailedException extends RuntimeException {
        private final ErrorType errorType;

        public LlmCallFailedException(String message, Throwable cause, ErrorType errorType) {
            super(message, cause);
            this.errorType = errorType;
        }

        public ErrorType getErrorType() {
            return errorType;
        }
    }
}
