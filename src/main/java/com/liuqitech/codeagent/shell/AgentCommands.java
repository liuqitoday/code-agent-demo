package com.liuqitech.codeagent.shell;

import com.liuqitech.codeagent.agent.AgentResponse;
import com.liuqitech.codeagent.agent.CodeAgent;
import com.liuqitech.codeagent.config.AgentProperties;
import org.jline.terminal.Terminal;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.PrintWriter;

/**
 * Agent Shell 命令
 * 定义命令行交互界面的所有可用命令
 * 
 * 注意：由于 Spring AI 流式模式 + Tool Calling 存在兼容性问题（toolName 为 null），
 * 当需要工具调用时使用阻塞模式。
 */
@ShellComponent
public class AgentCommands {
    
    private final CodeAgent codeAgent;
    private final AgentProperties agentProperties;
    private final Terminal terminal;
    
    public AgentCommands(CodeAgent codeAgent, AgentProperties agentProperties, Terminal terminal) {
        this.codeAgent = codeAgent;
        this.agentProperties = agentProperties;
        this.terminal = terminal;
    }
    
    /**
     * 生成代码命令
     * 由于需要工具调用（创建文件），使用阻塞模式确保稳定性
     */
    @ShellMethod(value = "根据描述生成代码", key = {"generate", "gen", "g"})
    public void generate(
            @ShellOption(
                value = {"-d", "--description"},
                help = "代码需求描述",
                defaultValue = ShellOption.NULL
            ) String description,
            @ShellOption(
                value = {"-f", "--folder"},
                help = "指定子文件夹（相对于工作空间），不存在会自动创建",
                defaultValue = ""
            ) String folder,
            @ShellOption(
                value = {"-s", "--save"},
                help = "是否保存到文件",
                defaultValue = "true"
            ) boolean save
    ) {
        PrintWriter writer = terminal.writer();
        
        if (description == null || description.isBlank()) {
            writer.println("[提示] 请提供代码描述，例如: generate \"创建一个 Java 的用户服务类\"");
            writer.println("   可选参数: -f test (在 workspace/test 目录下创建)");
            writer.flush();
            return;
        }
        
        // 构建提示词，包含文件夹信息
        StringBuilder promptBuilder = new StringBuilder(description);
        
        if (save) {
            promptBuilder.append("\n\n请将生成的代码保存到文件中。");
            if (folder != null && !folder.isBlank()) {
                promptBuilder.append("\n重要：请将所有文件保存在 '")
                    .append(folder)
                    .append("' 目录下。如果需要创建子目录结构，请基于这个目录。");
                writer.println("\n[目标文件夹]: " + folder);
            }
        } else {
            promptBuilder.append("\n\n只需要展示代码，不需要保存到文件。");
        }
        
        String prompt = promptBuilder.toString();
        
        writer.println("\n[处理中] 正在生成代码，请稍候...\n");
        writer.flush();
        
        // 使用阻塞模式（工具调用更稳定）
        AgentResponse response = codeAgent.execute(prompt);
        writer.println(response.getOutput());
        writer.flush();
    }
    
    /**
     * 交互式对话命令 - 使用流式输出
     * 纯文本对话不涉及工具调用，可以安全使用流式模式
     */
    @ShellMethod(value = "向 Agent 提问（流式输出）", key = {"ask", "a"})
    public void ask(
            @ShellOption(help = "你的问题或请求") String question
    ) {
        PrintWriter writer = terminal.writer();
        
        if (question == null || question.isBlank()) {
            writer.println("[提示] 请输入你的问题");
            writer.flush();
            return;
        }
        
        writer.println("\n[思考中] ...\n");
        writer.flush();
        
        // 尝试流式输出，如果失败则回退到阻塞模式
        try {
            StringBuilder fullResponse = new StringBuilder();
            
            codeAgent.executeStream(question)
                .doOnNext(chunk -> {
                    writer.print(chunk);
                    writer.flush();
                    fullResponse.append(chunk);
                })
                .doOnComplete(() -> {
                    writer.println("\n");
                    writer.flush();
                    codeAgent.getConversationMemory().addAssistantMessage(fullResponse.toString());
                })
                .blockLast();
                
        } catch (Exception e) {
            // 如果流式失败（可能涉及工具调用），回退到阻塞模式
            writer.println("\n[切换模式] 切换到标准模式...\n");
            writer.flush();
            
            AgentResponse response = codeAgent.execute(question);
            writer.println(response.getOutput());
            writer.flush();
        }
    }
    
    /**
     * 快速问答 - 使用阻塞模式，更稳定
     */
    @ShellMethod(value = "向 Agent 提问（稳定模式）", key = {"query", "q"})
    public void query(
            @ShellOption(help = "你的问题或请求") String question
    ) {
        PrintWriter writer = terminal.writer();
        
        if (question == null || question.isBlank()) {
            writer.println("[提示] 请输入你的问题");
            writer.flush();
            return;
        }
        
        writer.println("\n[处理中] 正在处理...\n");
        writer.flush();
        
        AgentResponse response = codeAgent.execute(question);
        writer.println(response.getOutput());
        writer.flush();
    }
    
    /**
     * 清空对话历史
     */
    @ShellMethod(value = "清空对话历史", key = {"clear", "c"})
    public String clear() {
        codeAgent.clearMemory();
        return "[成功] 对话历史已清空";
    }
    
    /**
     * 显示当前配置
     */
    @ShellMethod(value = "显示当前配置信息", key = {"config", "cfg"})
    public String showConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 当前配置:\n");
        sb.append("┌────────────────────────────────────────┐\n");
        sb.append("│ 工作空间: ").append(agentProperties.getWorkspace()).append("\n");
        sb.append("│ 默认语言: ").append(agentProperties.getDefaultLanguage()).append("\n");
        sb.append("│ 最大历史: ").append(agentProperties.getMaxHistory()).append(" 条消息\n");
        sb.append("│ 模型:     ").append(agentProperties.getModel()).append("\n");
        sb.append("│ 当前历史: ").append(codeAgent.getHistorySize()).append(" 条消息\n");
        sb.append("└────────────────────────────────────────┘");
        return sb.toString();
    }
    
    /**
     * 显示帮助信息
     */
    @ShellMethod(value = "显示使用帮助", key = {"guide"})
    public String guide() {
        return """
            Code Agent 使用指南
            ═══════════════════════════════════════════════════════════════
            
            [代码生成] (会调用工具创建文件):
               generate "创建一个 Java 的 UserService 类，包含登录和注册方法"
               gen "写一个 Python 爬虫" -f python-demo
               g "创建 React 组件" --folder frontend/components
            
               [指定子文件夹]:
               generate "创建测试类" -f test        → workspace/test/下创建
               generate "创建工具类" -f utils/common → workspace/utils/common/下创建
            
            [问答对话] (流式输出):
               ask "如何在 Java 中实现线程安全的单例模式？"
               a "解释一下依赖注入的原理"
            
            [稳定查询] (阻塞模式，更稳定):
               query "帮我分析这段代码的问题"
               q "给我一个简单的例子"
            
            [其他命令]:
               clear  - 清空对话历史
               config - 显示当前配置
               help   - 显示所有可用命令
               exit   - 退出程序
            
            [提示]:
               - generate 命令支持 -f 参数指定目标子文件夹
               - 文件夹不存在会自动创建
               - 如果遇到超时，尝试用 query 代替 ask
            ═══════════════════════════════════════════════════════════════
            """;
    }
}
