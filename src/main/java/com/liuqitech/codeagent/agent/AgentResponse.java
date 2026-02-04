package com.liuqitech.codeagent.agent;

/**
 * Agent 响应类
 * 封装 Agent 执行结果
 */
public class AgentResponse {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 输出消息（成功时的响应内容）
     */
    private String message;

    /**
     * 错误信息（失败时的错误描述）
     */
    private String error;

    public AgentResponse() {
    }

    public static AgentResponse success(String message) {
        AgentResponse response = new AgentResponse();
        response.success = true;
        response.message = message;
        return response;
    }

    public static AgentResponse error(String error) {
        AgentResponse response = new AgentResponse();
        response.success = false;
        response.error = error;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * 格式化输出结果
     */
    public String getOutput() {
        if (success) {
            return message != null ? message : "";
        } else {
            return "[执行失败] " + (error != null ? error : "未知错误");
        }
    }

    /**
     * 获取失败响应格式化输出
     */
    public String getErrorOutput() {
        return "[执行失败] " + (error != null ? error : "未知错误");
    }
}
