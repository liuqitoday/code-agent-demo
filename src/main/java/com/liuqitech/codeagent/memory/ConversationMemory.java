package com.liuqitech.codeagent.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对话记忆管理
 * 保存会话历史，支持上下文对话
 */
@Component
public class ConversationMemory {
    
    /**
     * 消息历史列表
     */
    private final List<Message> messageHistory = new ArrayList<>();
    
    /**
     * 最大保存消息数量
     */
    private int maxMessages = 20;
    
    public ConversationMemory() {
    }
    
    public ConversationMemory(int maxMessages) {
        this.maxMessages = maxMessages;
    }
    
    /**
     * 添加用户消息
     */
    public void addUserMessage(String content) {
        messageHistory.add(new UserMessage(content));
        trimIfNeeded();
    }
    
    /**
     * 添加助手消息
     */
    public void addAssistantMessage(String content) {
        messageHistory.add(new AssistantMessage(content));
        trimIfNeeded();
    }
    
    /**
     * 获取所有消息历史
     */
    public List<Message> getMessages() {
        return Collections.unmodifiableList(messageHistory);
    }
    
    /**
     * 获取最近的N条消息
     */
    public List<Message> getRecentMessages(int count) {
        int size = messageHistory.size();
        if (count >= size) {
            return getMessages();
        }
        return Collections.unmodifiableList(
            messageHistory.subList(size - count, size)
        );
    }
    
    /**
     * 获取消息数量
     */
    public int size() {
        return messageHistory.size();
    }
    
    /**
     * 清空记忆
     */
    public void clear() {
        messageHistory.clear();
    }
    
    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return messageHistory.isEmpty();
    }
    
    /**
     * 如果超过最大数量则裁剪
     */
    private void trimIfNeeded() {
        while (messageHistory.size() > maxMessages) {
            messageHistory.remove(0);
        }
    }
    
    public int getMaxMessages() {
        return maxMessages;
    }
    
    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
        trimIfNeeded();
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messageHistory) {
            sb.append("\n").append(msg.toString());
        }
        return sb.toString();
    }
}
