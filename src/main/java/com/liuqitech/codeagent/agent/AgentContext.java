package com.liuqitech.codeagent.agent;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 上下文
 * 保存当前任务执行的状态和配置信息
 */
public class AgentContext {
    
    /**
     * 当前工作目录
     */
    private String workingDirectory;
    
    /**
     * 默认编程语言
     */
    private String defaultLanguage;
    
    /**
     * 上下文变量
     */
    private final Map<String, Object> variables = new HashMap<>();
    
    public AgentContext() {
        this.workingDirectory = System.getProperty("user.dir");
        this.defaultLanguage = "java";
    }
    
    public AgentContext(String workingDirectory, String defaultLanguage) {
        this.workingDirectory = workingDirectory;
        this.defaultLanguage = defaultLanguage;
    }
    
    public String getWorkingDirectory() {
        return workingDirectory;
    }
    
    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }
    
    public String getDefaultLanguage() {
        return defaultLanguage;
    }
    
    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }
    
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) variables.get(key);
    }
    
    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }
    
    public void clearVariables() {
        variables.clear();
    }
}
