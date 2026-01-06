package com.liuqitech.codeagent.agent;

import com.liuqitech.codeagent.config.AgentProperties;
import com.liuqitech.codeagent.config.LoggingInterceptor;
import com.liuqitech.codeagent.memory.ConversationMemory;
import com.liuqitech.codeagent.tool.CodeAgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Code Agent 核心类
 * 负责接收用户请求，协调 LLM 和工具完成代码生成任务
 * 
 * <p>工作原理：
 * <ol>
 *   <li>接收用户的自然语言请求</li>
 *   <li>构建系统提示词，告诉 LLM 它是一个代码生成助手</li>
 *   <li>将用户请求和可用工具信息发送给 LLM</li>
 *   <li>LLM 决定是否需要调用工具（如创建文件）</li>
 *   <li>执行工具调用，返回结果给 LLM</li>
 *   <li>LLM 生成最终响应返回给用户</li>
 * </ol>
 */
@Component
public class CodeAgent {
    
    private static final Logger log = LoggerFactory.getLogger(CodeAgent.class);
    
    /**
     * 系统提示词 - 定义 Agent 的身份和行为
     *
     * 注意：Spring AI 的 Tool Calling 机制已经实现了 ReAct 模式的核心循环：
     * 1. LLM 分析用户请求，决定是否需要调用工具（Reasoning）
     * 2. 如果需要，LLM 返回 tool_calls，Spring AI 自动执行工具（Acting）
     * 3. 工具执行结果自动发送回 LLM（Observation）
     * 4. LLM 根据结果继续思考，可能再次调用工具或返回最终答案
     * 5. 循环直到 LLM 返回普通文本响应
     *
     * 因此，System Prompt 只需要定义 Agent 的身份和行为准则，
     * 不需要显式要求 LLM 输出 [Thought]/[Action]/[Observation] 格式，
     * 因为这些步骤已经由框架自动处理。
     */
    private static final String SYSTEM_PROMPT = """
        你是一个专业的代码生成助手。

        ## 你的能力
        你可以使用以下工具来完成任务：
        - createFile: 创建新的代码文件并写入内容
        - readFile: 读取已有文件的内容
        - listDirectory: 列出目录结构
        - createDirectory: 创建新目录

        ## 工作方式
        1. 仔细分析用户的需求
        2. 如果需要创建文件，直接调用 createFile 工具
        3. 如果需要了解现有代码，先用 readFile 或 listDirectory 查看
        4. 根据工具执行结果，决定下一步操作或给出最终回答

        ## 代码质量要求
        - 生成的代码应该简洁、可读、符合最佳实践
        - 必须包含必要的注释说明
        - Java: 遵循阿里巴巴 Java 开发规范
        - Python: 遵循 PEP 8 规范
        - JavaScript/TypeScript: 遵循 ESLint 推荐配置

        ## 回答要求
        - 完成任务后，简要说明你做了什么
        - 如果创建了文件，告知文件路径
        - 如果遇到问题，清晰地说明原因和建议
        """;
    
    private final ChatClient.Builder chatClientBuilder;
    private final CodeAgentTools codeAgentTools;
    private final AgentProperties agentProperties;
    private final ConversationMemory conversationMemory;
    
    private ChatClient chatClient;
    private AgentContext context;
    
    public CodeAgent(ChatClient.Builder chatClientBuilder,
                     CodeAgentTools codeAgentTools,
                     AgentProperties agentProperties,
                     ConversationMemory conversationMemory) {
        this.chatClientBuilder = chatClientBuilder;
        this.codeAgentTools = codeAgentTools;
        this.agentProperties = agentProperties;
        this.conversationMemory = conversationMemory;
    }
    
    /**
     * 初始化 Agent
     */
    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("🚀 [Agent 初始化] 开始初始化 Code Agent...");
        log.info("========================================");
        
        // 设置工作空间
        codeAgentTools.setWorkspaceRoot(agentProperties.getWorkspace());
        log.info("📁 [配置] 工作空间: {}", agentProperties.getWorkspace());
        
        // 初始化上下文
        context = new AgentContext(
            agentProperties.getWorkspace(),
            agentProperties.getDefaultLanguage()
        );
        log.info("📝 [配置] 默认语言: {}", agentProperties.getDefaultLanguage());
        
        // 设置记忆大小
        conversationMemory.setMaxMessages(agentProperties.getMaxHistory());
        log.info("🧠 [配置] 最大历史记录: {} 条", agentProperties.getMaxHistory());
        
        // 构建 ChatClient，配置系统提示词和工具
        log.info("🔧 [初始化] 注册工具: createFile, readFile, listDirectory, createDirectory");
        log.info("📋 [初始化] 加载系统提示词 (System Prompt)...");
        
        this.chatClient = chatClientBuilder
            .defaultSystem(SYSTEM_PROMPT)
            .defaultTools(codeAgentTools)  // 注册工具，支持 Tool Calling
            .build();
        
        log.info("========================================");
        log.info("[Agent 初始化] Code Agent 初始化完成!");
        log.info("========================================");
    }
    
    /**
     * 执行用户请求
     *
     * @param userRequest 用户的自然语言请求
     * @return 执行结果
     */
    public AgentResponse execute(String userRequest) {
        // 重置轮次计数器
        LoggingInterceptor.resetRoundCounter();

        // 详细日志 - 记录到文件
        log.info("");
        log.info("========================================");
        log.info("[Step 1] 收到用户请求");
        log.info("========================================");
        log.info("用户输入: {}", userRequest);

        try {
            // Step 2: 记录用户消息到记忆
            log.info("");
            log.info("[Step 2] 保存用户消息到对话记忆");
            conversationMemory.addUserMessage(userRequest);
            log.info("当前对话历史: {} 条消息", conversationMemory.size());

            // Step 3: 构建请求并调用 LLM
            log.info("");
            log.info("[Step 3] 构建 Prompt 并调用 LLM");
            log.info("├── 系统提示词: 已加载 (定义 Agent 身份和行为)");
            log.info("├── 可用工具: createFile, readFile, listDirectory, createDirectory");
            log.info("└── 用户请求: {}", userRequest.substring(0, Math.min(50, userRequest.length())) + "...");

            // Debug: 记录完整的上下文信息
            log.debug("");
            log.debug("[DEBUG] 完整 Context 信息:");
            log.debug("----------------------------------------");
            log.debug("[System Prompt]:\n{}", SYSTEM_PROMPT);

            if (conversationMemory.size() > 0) {
                log.debug("\n[History ({} messages)]:", conversationMemory.size());
                log.debug(conversationMemory.toString());
            }

            log.debug("\n[User Input]:\n{}", userRequest);
            log.debug("----------------------------------------");

            log.info("");
            log.info("[Step 4] 等待 LLM 响应...");
            log.info("（如果 LLM 决定调用工具，将自动执行 Tool Calling）");

            long startTime = System.currentTimeMillis();
            org.springframework.ai.chat.model.ChatResponse chatResponse = null;
            Exception lastException = null;

            // 重试逻辑：最多重试3次
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    // 调用 LLM，Spring AI 会自动处理 Tool Calling
                    chatResponse = chatClient.prompt()
                        .user(userRequest)
                        .call()
                        .chatResponse();

                    // 成功，跳出重试循环
                    break;

                } catch (Exception e) {
                    lastException = e;
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

                    // 判断是否为超时相关错误
                    boolean isTimeout = isTimeoutException(e);

                    if (isTimeout && attempt < maxRetries) {
                        // 超时错误，尝试重试
                        log.warn("");
                        log.warn("[重试 {}/{}] 调用超时，正在等待后重试...", attempt, maxRetries);
                        log.warn("   错误信息: {}", errorMsg);

                        // 控制台通知用户
                        System.out.println("\n⏳ [重试 " + attempt + "/" + maxRetries + "] 请求超时，正在重试...");

                        // 等待一段时间后重试（指数退避）
                        long waitTime = attempt * 2000L; // 2秒, 4秒, 6秒
                        log.info("   等待 {} 秒后重试...", waitTime / 1000);
                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw e;
                        }
                    } else {
                        // 非超时错误或已达最大重试次数，直接抛出
                        throw e;
                    }
                }
            }

            // 如果所有重试都失败
            if (chatResponse == null && lastException != null) {
                throw lastException;
            }

            long duration = System.currentTimeMillis() - startTime;

            // 提取响应内容和思考过程
            String response = chatResponse.getResult().getOutput().getText();
            String reasoningContent = null;

            // 尝试多种方式获取reasoning_content
            // 方式1: 从AssistantMessage的metadata中获取
            var output = chatResponse.getResult().getOutput();
            var metadata = output.getMetadata();
            if (metadata != null) {
                // 尝试直接获取
                if (metadata.containsKey("reasoning_content")) {
                    reasoningContent = (String) metadata.get("reasoning_content");
                }
                // 尝试从其他可能的字段获取
                if (reasoningContent == null && metadata.containsKey("reasoningContent")) {
                    reasoningContent = (String) metadata.get("reasoningContent");
                }
            }

            // 方式2: 从ChatResponse的metadata中获取
            if (reasoningContent == null) {
                var responseMetadata = chatResponse.getMetadata();
                if (responseMetadata != null) {
                    Object reasoningObj = responseMetadata.get("reasoning_content");
                    if (reasoningObj != null) {
                        reasoningContent = reasoningObj.toString();
                    }
                }
            }

            // 方式3: 尝试从Generation的properties中获取
            if (reasoningContent == null) {
                var generation = chatResponse.getResult();
                try {
                    // 使用反射获取可能存在的字段
                    var generationClass = generation.getClass();
                    var fields = generationClass.getDeclaredFields();
                    for (var field : fields) {
                        field.setAccessible(true);
                        if (field.getName().contains("reasoning") || field.getName().contains("Reasoning")) {
                            Object value = field.get(generation);
                            if (value != null) {
                                reasoningContent = value.toString();
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("无法通过反射获取reasoning_content: {}", e.getMessage());
                }
            }

            // 详细日志 - 记录到文件
            log.info("");
            log.info("[Step 5] LLM 响应完成");
            log.info("├── 耗时: {} 毫秒", duration);
            log.info("└── 响应长度: {} 字符", response != null ? response.length() : 0);
            if (reasoningContent != null && !reasoningContent.isEmpty()) {
                log.info("└── 思考过程长度: {} 字符", reasoningContent.length());
            } else {
                log.debug("未找到reasoning_content字段");
            }

            // Debug: 记录完整的 LLM 输出
            log.debug("");
            log.debug("[DEBUG] LLM 输出 (Response):");
            log.debug("----------------------------------------");
            if (reasoningContent != null && !reasoningContent.isEmpty()) {
                log.debug("[Reasoning Content]:\n{}", reasoningContent);
                log.debug("----------------------------------------");
            }
            log.debug("{}", response);
            log.debug("----------------------------------------");

            // Step 6: 保存助手回复
            log.info("");
            log.info("[Step 6] 保存助手响应到对话记忆");
            conversationMemory.addAssistantMessage(response);

            log.info("");
            log.info("========================================");
            log.info("[完成] 请求处理成功!");
            log.info("========================================");

            // 控制台输出 - 用户可见（包含耗时信息）
            printFormattedResponse(response, reasoningContent, duration);

            // 构建成功响应
            return AgentResponse.success(response);

        } catch (Exception e) {
            log.error("");
            log.error("========================================");
            log.error("[错误] 执行请求失败");
            log.error("========================================");

            // 构建用户友好的错误信息
            String userMessage = buildUserFriendlyErrorMessage(e);
            log.error("错误详情: {}", userMessage);
            log.debug("异常堆栈:", e);

            // 控制台输出错误
            System.out.println("\n❌ [错误] " + userMessage);

            return AgentResponse.error(userMessage);
        }
    }
    
    /**
     * 判断是否为超时相关异常
     */
    private boolean isTimeoutException(Exception e) {
        String message = e.toString().toLowerCase();
        return message.contains("timeout") 
            || message.contains("timed out")
            || message.contains("read timed out")
            || message.contains("connect timed out")
            || message.contains("sockettimeoutexception")
            || message.contains("resourceaccessexception");
    }
    
    /**
     * 构建用户友好的错误信息
     */
    private String buildUserFriendlyErrorMessage(Exception e) {
        String original = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        
        if (isTimeoutException(e)) {
            return "调用超时：LLM 服务响应时间过长，已重试多次仍然失败。\n" +
                   "   可能原因：\n" +
                   "   • 网络连接不稳定\n" +
                   "   • API 服务器负载过高\n" +
                   "   • 请求内容过于复杂\n" +
                   "   建议：\n" +
                   "   • 稍后再试\n" +
                   "   • 简化您的请求\n" +
                   "   • 检查网络连接";
        }
        
        if (original.contains("401") || original.contains("Unauthorized")) {
            return "认证失败：API Key 无效或已过期。\n" +
                   "   请检查 application.yml 中的 api-key 配置。";
        }
        
        if (original.contains("429") || original.contains("rate limit")) {
            return "请求过于频繁：已超出 API 调用限制。\n" +
                   "   请稍等片刻后再试。";
        }
        
        if (original.contains("500") || original.contains("Internal Server Error")) {
            return "服务器错误：LLM 服务暂时不可用。\n" +
                   "   这不是您的问题，请稍后再试。";
        }
        
        if (original.contains("Connection refused") || original.contains("UnknownHostException")) {
            return "连接失败：无法连接到 LLM 服务。\n" +
                   "   请检查网络连接和 API 地址配置。";
        }
        
        // 默认错误信息
        return "处理请求时发生错误：" + original;
    }
    
    /**
     * 流式执行用户请求（返回 Flux）
     * 
     * @param userRequest 用户的自然语言请求
     * @return 流式响应
     */
    public reactor.core.publisher.Flux<String> executeStream(String userRequest) {
        log.info("");
        log.info("========================================");
        log.info("📨 [流式请求] 收到用户请求");
        log.info("========================================");
        log.info("用户输入: {}", userRequest);
        
        conversationMemory.addUserMessage(userRequest);
        log.info("🧠 已保存到对话记忆, 当前 {} 条消息", conversationMemory.size());
        
        log.info("⏳ 开始流式调用 LLM...");
        
        return chatClient.prompt()
            .user(userRequest)
            .stream()
            .content()
            .doOnNext(chunk -> log.debug("📤 收到片段: {} 字符", chunk.length()))
            .doOnComplete(() -> {
                log.info("");
                log.info("✅ [流式请求] 响应完成");
            })
            .doOnError(error -> {
                log.error("❌ [流式请求] 出错: {}", error.getMessage());
            });
    }
    
    /**
     * 清空对话历史
     */
    public void clearMemory() {
        conversationMemory.clear();
        log.info("🧹 对话历史已清空");
    }
    
    /**
     * 获取当前上下文
     */
    public AgentContext getContext() {
        return context;
    }
    
    /**
     * 获取对话历史大小
     */
    public int getHistorySize() {
        return conversationMemory.size();
    }
    
    /**
     * 获取对话记忆（供流式输出后保存响应）
     */
    public ConversationMemory getConversationMemory() {
        return conversationMemory;
    }

    /**
     * 格式化输出响应内容到控制台
     *
     * @param response 响应内容
     * @param reasoningContent 思考过程内容（来自API的reasoning_content字段，如DeepSeek等模型支持）
     * @param durationMs 请求耗时（毫秒）
     */
    private void printFormattedResponse(String response, String reasoningContent, long durationMs) {
        // 输出reasoning_content（如果有，某些模型如DeepSeek会返回思考过程）
        if (reasoningContent != null && !reasoningContent.isEmpty()) {
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("💭 [思考过程]");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println(reasoningContent.trim());
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        }

        // 输出主要响应内容
        if (response != null && !response.isEmpty()) {
            System.out.println("\n" + response);
        } else if (reasoningContent == null || reasoningContent.isEmpty()) {
            System.out.println("(无响应内容)");
        }

        // 输出耗时信息
        String timeStr;
        if (durationMs < 1000) {
            timeStr = durationMs + "ms";
        } else {
            timeStr = String.format("%.1fs", durationMs / 1000.0);
        }
        System.out.println("\n✅ 完成 (耗时: " + timeStr + ")\n");
    }
}
