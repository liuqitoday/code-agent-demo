package com.liuqitech.codeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 配置属性
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    
    /**
     * 工作空间目录
     */
    private String workspace = "./workspace";
    
    /**
     * 最大历史记录数
     */
    private int maxHistory = 20;
    
    /**
     * 默认编程语言
     */
    private String defaultLanguage = "java";
    
    /**
     * 模型名称
     */
    private String model = "gpt-4";
    
    /**
     * 温度参数 (0-1)
     */
    private double temperature = 0.7;
    
    public String getWorkspace() {
        return workspace;
    }
    
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }
    
    public int getMaxHistory() {
        return maxHistory;
    }
    
    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
    }
    
    public String getDefaultLanguage() {
        return defaultLanguage;
    }
    
    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}
