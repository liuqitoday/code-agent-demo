package com.liuqitech.codeagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

/**
 * LLM 调用重试监听器
 * 用于记录重试日志和输出用户提示
 */
@Component("llmRetryListener")
public class LlmRetryListener implements RetryListener {

    private static final Logger log = LoggerFactory.getLogger(LlmRetryListener.class);

    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        // 重试开始前调用，返回 true 继续执行
        return true;
    }

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable t) {
        // 重试结束后调用
        if (context.getRetryCount() > 0) {
            if (t == null) {
                log.info("[LLM 重试] 第 {} 次重试成功", context.getRetryCount());
            } else {
                log.error("[LLM 重试] 重试 {} 次后仍然失败", context.getRetryCount());
            }
        }
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable t) {
        int retryCount = context.getRetryCount();
        String errorMsg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();

        log.warn("");
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.warn("[LLM 重试 {}/3] 调用失败", retryCount);
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.warn("错误信息: {}", errorMsg);
        log.warn("正在等待后重试...");
    }
}
