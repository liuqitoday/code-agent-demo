package com.liuqitech.codeagent.agent;

import com.liuqitech.codeagent.config.AgentProperties;
import com.liuqitech.codeagent.service.LlmService;
import com.liuqitech.codeagent.tool.CodeAgentTools;
import com.liuqitech.codeagent.util.ErrorMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.UUID;

/**
 * Code Agent 核心类
 * 负责协调 LLM 调用和返回结果，不负责控制台输出
 */
@Component
public class CodeAgent {

    private static final Logger log = LoggerFactory.getLogger(CodeAgent.class);

    private static final String SYSTEM_PROMPT = """
        你是一个专业的代码生成助手。

        ## 工作方式
        1. 分析用户需求，理解要完成的任务
        2. 创建新文件：使用 createFile 工具
        3. 修改已有文件：先用 readFile 读取内容，再用 editFile 精确替换要修改的部分
        4. 查看目录结构：使用 listDirectory 工具
        5. 批量重命名变量或替换：使用 editFileAll 工具

        ## 编辑文件注意事项
        - editFile 要求 oldContent 在文件中唯一匹配，如果有多处相同内容，需要包含更多上下文使其唯一
        - 修改前务必先 readFile 获取最新内容，确保 oldContent 精确匹配（包括空格、换行、缩进）

        ## 代码质量要求
        - 代码简洁、可读、符合最佳实践
        - 包含必要的注释
        - Java: 遵循阿里巴巴 Java 开发规范
        - Python: 遵循 PEP 8 规范
        - JavaScript/TypeScript: 遵循 ESLint 推荐配置

        ## 回答要求
        - 完成任务后，简要说明做了什么
        - 如果操作了文件，告知文件路径
        - 遇到问题时，说明原因和建议
        """;

    private final ChatClient.Builder chatClientBuilder;
    private final CodeAgentTools codeAgentTools;
    private final AgentProperties agentProperties;
    private final ChatMemory chatMemory;
    private final LlmService llmService;

    private ChatClient chatClient;
    private ChatClient streamClient;
    private String currentConversationId;

    public CodeAgent(ChatClient.Builder chatClientBuilder,
                     CodeAgentTools codeAgentTools,
                     AgentProperties agentProperties,
                     ChatMemory chatMemory,
                     LlmService llmService) {
        this.chatClientBuilder = chatClientBuilder;
        this.codeAgentTools = codeAgentTools;
        this.agentProperties = agentProperties;
        this.chatMemory = chatMemory;
        this.llmService = llmService;
    }

    @PostConstruct
    public void init() {
        this.currentConversationId = generateConversationId();
        log.info("[Agent 初始化] 工作空间: {}, 会话 ID: {}", agentProperties.getWorkspace(), currentConversationId);

        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        // 阻塞模式客户端：注册工具，支持 tool calling
        this.chatClient = chatClientBuilder
            .defaultSystem(SYSTEM_PROMPT)
            .defaultTools(codeAgentTools)
            .defaultAdvisors(memoryAdvisor)
            .build();

        // 流式模式客户端：不注册工具，避免 Spring AI stream + tool calling 的已知问题
        // 参考: https://github.com/spring-projects/spring-ai/issues/5167
        this.streamClient = chatClientBuilder
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(memoryAdvisor)
            .build();

        log.info("[Agent 初始化] 完成");
    }

    /**
     * 执行用户请求（阻塞模式）
     *
     * @param userRequest 用户的自然语言请求
     * @return 携带完整数据的 AgentResponse
     */
    public AgentResponse execute(String userRequest) {
        log.info("[请求] 会话={}, 输入={}", currentConversationId,
                userRequest.substring(0, Math.min(50, userRequest.length())));

        long startTime = System.currentTimeMillis();

        try {
            org.springframework.ai.chat.model.ChatResponse chatResponse =
                llmService.call(chatClient, userRequest, currentConversationId);

            long duration = System.currentTimeMillis() - startTime;

            if (chatResponse == null || chatResponse.getResult() == null
                    || chatResponse.getResult().getOutput() == null) {
                log.warn("[完成] 耗时={}ms, LLM 返回了空响应", duration);
                return AgentResponse.success("(模型未返回内容)", null, duration);
            }

            String response = chatResponse.getResult().getOutput().getText();
            String reasoningContent = extractReasoningContent(chatResponse);

            log.info("[完成] 耗时={}ms, 响应长度={}", duration, response != null ? response.length() : 0);
            return AgentResponse.success(response, reasoningContent, duration);

        } catch (LlmService.LlmCallFailedException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[错误] LLM 调用失败（已重试）, 耗时={}ms", duration, e);

            String userMessage = ErrorMessages.buildUserFriendlyMessage(
                    e.getErrorType(),
                    e.getMessage());
            return AgentResponse.error(userMessage);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[错误] 执行请求失败, 耗时={}ms", duration, e);

            LlmService.ErrorType type = LlmService.classifyException(e);
            String original = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            String userMessage = ErrorMessages.buildUserFriendlyMessage(type, original);
            return AgentResponse.error(userMessage);
        }
    }

    /**
     * 流式执行用户请求
     * 使用不注册工具的 streamClient，避免 Spring AI stream 模式下 tool calling 的已知问题
     *
     * @param userRequest 用户的自然语言请求
     * @return 流式响应
     */
    public reactor.core.publisher.Flux<String> executeStream(String userRequest) {
        log.info("[流式请求] 会话={}, 输入={}", currentConversationId, userRequest);

        return llmService.stream(streamClient, userRequest, currentConversationId)
            .doOnComplete(() -> log.info("[流式请求] 响应完成"))
            .doOnError(error -> log.error("[流式请求] 出错: {}", error.getMessage()));
    }

    public void clearMemory() {
        chatMemory.clear(currentConversationId);
        String oldId = currentConversationId;
        this.currentConversationId = generateConversationId();
        log.info("对话历史已清空, 旧会话ID={}, 新会话ID={}", oldId, currentConversationId);
    }

    public String getConversationId() {
        return currentConversationId;
    }

    public int getHistorySize() {
        return chatMemory.get(currentConversationId).size();
    }

    private String generateConversationId() {
        return "session-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 从 ChatResponse 中提取思考过程内容
     *
     * 注意：reasoning_content 是部分模型的扩展字段，并非 OpenAI 标准 API 的一部分。
     * 支持的模型包括 DeepSeek（reasoning_content）、QwQ 等推理模型。
     * 标准 OpenAI GPT 系列模型不会返回此字段，此方法会安全地返回 null。
     */
    private String extractReasoningContent(org.springframework.ai.chat.model.ChatResponse chatResponse) {
        var output = chatResponse.getResult().getOutput();
        var metadata = output.getMetadata();

        if (metadata != null) {
            if (metadata.containsKey("reasoning_content")) {
                return (String) metadata.get("reasoning_content");
            }
            if (metadata.containsKey("reasoningContent")) {
                return (String) metadata.get("reasoningContent");
            }
        }

        var responseMetadata = chatResponse.getMetadata();
        if (responseMetadata != null) {
            Object reasoningObj = responseMetadata.get("reasoning_content");
            if (reasoningObj != null) {
                return reasoningObj.toString();
            }
        }

        return null;
    }
}
