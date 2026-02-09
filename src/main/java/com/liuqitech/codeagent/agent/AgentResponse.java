package com.liuqitech.codeagent.agent;

/**
 * Agent 响应类（不可变）
 * 封装 Agent 执行结果，包含响应内容、思考过程和耗时信息
 */
public class AgentResponse {

    private final boolean success;
    private final String message;
    private final String error;
    private final String reasoningContent;
    private final long durationMs;

    private AgentResponse(boolean success, String message, String error,
                          String reasoningContent, long durationMs) {
        this.success = success;
        this.message = message;
        this.error = error;
        this.reasoningContent = reasoningContent;
        this.durationMs = durationMs;
    }

    public static AgentResponse success(String message, String reasoningContent, long durationMs) {
        return new AgentResponse(true, message, null, reasoningContent, durationMs);
    }

    public static AgentResponse error(String error) {
        return new AgentResponse(false, null, error, null, 0);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getError() {
        return error;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
