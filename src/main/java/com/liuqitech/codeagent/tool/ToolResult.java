package com.liuqitech.codeagent.tool;

/**
 * 工具执行结果
 */
public class ToolResult {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 结果消息
     */
    private String message;
    
    /**
     * 结果数据
     */
    private Object data;
    
    private ToolResult(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    
    public static ToolResult success(String message) {
        return new ToolResult(true, message, null);
    }
    
    public static ToolResult success(String message, Object data) {
        return new ToolResult(true, message, data);
    }
    
    public static ToolResult error(String message) {
        return new ToolResult(false, message, null);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Object getData() {
        return data;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getDataAs(Class<T> type) {
        return (T) data;
    }
}
