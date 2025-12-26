package com.liuqitech.codeagent.agent;

import java.util.ArrayList;
import java.util.List;

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
     * 输出消息
     */
    private String message;
    
    /**
     * 生成的代码内容
     */
    private String generatedCode;
    
    /**
     * 创建的文件列表
     */
    private List<String> createdFiles;
    
    /**
     * 错误信息（如果有）
     */
    private String error;
    
    public AgentResponse() {
        this.createdFiles = new ArrayList<>();
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
    
    public String getGeneratedCode() {
        return generatedCode;
    }
    
    public void setGeneratedCode(String generatedCode) {
        this.generatedCode = generatedCode;
    }
    
    public List<String> getCreatedFiles() {
        return createdFiles;
    }
    
    public void setCreatedFiles(List<String> createdFiles) {
        this.createdFiles = createdFiles;
    }
    
    public void addCreatedFile(String filePath) {
        if (this.createdFiles == null) {
            this.createdFiles = new ArrayList<>();
        }
        this.createdFiles.add(filePath);
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
        StringBuilder sb = new StringBuilder();
        
        if (success) {
            sb.append("[成功] ").append(message != null ? message : "执行成功");
            
            if (createdFiles != null && !createdFiles.isEmpty()) {
            sb.append("\n\n [文件创建]:");
            createdFiles.forEach(file -> sb.append("\n   - ").append(file));
        }
        
        if (generatedCode != null && !generatedCode.isBlank()) {
            sb.append("\n\n [生成代码]:\n").append(generatedCode);
        }
        } else {
            // If not successful, getOutput() should return an empty string or indicate failure
            // as the detailed error is now handled by getErrorOutput().
            // For consistency, we'll return a generic failure message here if success is false.
            sb.append("[执行失败] ").append(error != null ? error : "未知错误");
        }
        
        return sb.toString();
    }
    
    /**
     * 获取失败响应格式化输出
     */
    public String getErrorOutput() {
        return "[执行失败] " + (message != null ? message : "未知错误");
    }
}
