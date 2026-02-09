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
 * 唯一与终端交互的层，负责所有控制台输出
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

    @ShellMethod(value = "Agent 模式：支持工具调用，可创建/编辑/读取文件", key = {"agent", "ag", "a"})
    public void agent(
            @ShellOption(
                value = {"-d", "--description"},
                help = "任务描述或问题",
                defaultValue = ShellOption.NULL
            ) String description,
            @ShellOption(
                value = {"-f", "--folder"},
                help = "指定子文件夹（相对于工作空间），不存在会自动创建",
                defaultValue = ""
            ) String folder
    ) {
        PrintWriter writer = terminal.writer();

        if (description == null || description.isBlank()) {
            writer.println("[提示] 请提供任务描述，例如:");
            writer.println("   agent \"创建一个 Java 的用户服务类\"");
            writer.println("   a \"读取并优化 index.html\"");
            writer.println("   可选参数: -f test (在 workspace/test 目录下操作)");
            writer.flush();
            return;
        }

        StringBuilder promptBuilder = new StringBuilder(description);

        if (folder != null && !folder.isBlank()) {
            promptBuilder.append("\n\n目标目录: ").append(folder);
            writer.println("\n[目标文件夹]: " + folder);
        }

        String prompt = promptBuilder.toString();

        writer.println("\n[处理中] Agent 正在工作，请稍候...\n");
        writer.flush();

        AgentResponse response = codeAgent.execute(prompt);
        printResponse(response, writer);
    }

    @ShellMethod(value = "问答模式：流式输出，纯对话不调用工具", key = {"ask", "q"})
    public void ask(
            @ShellOption(help = "你的问题") String question
    ) {
        PrintWriter writer = terminal.writer();

        if (question == null || question.isBlank()) {
            writer.println("[提示] 请输入你的问题，例如:");
            writer.println("   ask \"如何在 Java 中实现单例模式？\"");
            writer.println("   q \"解释一下依赖注入的原理\"");
            writer.flush();
            return;
        }

        writer.println("\n[思考中] ...\n");
        writer.flush();

        try {
            codeAgent.executeStream(question)
                .doOnNext(chunk -> {
                    writer.print(chunk);
                    writer.flush();
                })
                .doOnComplete(() -> {
                    writer.println("\n");
                    writer.flush();
                })
                .blockLast();

        } catch (Exception e) {
            writer.println("\n[切换模式] 流式输出异常，切换到标准模式...\n");
            writer.flush();

            AgentResponse response = codeAgent.execute(question);
            printResponse(response, writer);
        }
    }

    @ShellMethod(value = "清空对话历史", key = {"clear", "c"})
    public String clear() {
        codeAgent.clearMemory();
        return "[成功] 对话历史已清空，新会话 ID: " + codeAgent.getConversationId();
    }

    @ShellMethod(value = "显示当前配置信息", key = {"config", "cfg"})
    public String showConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("当前配置:\n");
        sb.append("┌────────────────────────────────────────┐\n");
        sb.append("│ 工作空间: ").append(agentProperties.getWorkspace()).append("\n");
        sb.append("│ 默认语言: ").append(agentProperties.getDefaultLanguage()).append("\n");
        sb.append("│ 最大历史: ").append(agentProperties.getMaxHistory()).append(" 条消息\n");
        sb.append("│ 模型:     ").append(agentProperties.getModel()).append("\n");
        sb.append("│ 会话 ID:  ").append(codeAgent.getConversationId()).append("\n");
        sb.append("│ 当前历史: ").append(codeAgent.getHistorySize()).append(" 条消息\n");
        sb.append("└────────────────────────────────────────┘");
        return sb.toString();
    }

    @ShellMethod(value = "显示使用帮助", key = {"guide"})
    public String guide() {
        return """
            Code Agent 使用指南
            ═══════════════════════════════════════════════════════════════

            [Agent 模式] (支持工具调用，可操作文件):
               agent "创建一个 Java 的 UserService 类，包含登录和注册方法"
               ag "读取 index.html 并优化性能"
               a "写一个 Python 爬虫" -f python-demo
               a "创建 React 组件" --folder frontend/components

            [问答模式] (流式输出，纯对话):
               ask "如何在 Java 中实现线程安全的单例模式？"
               q "解释一下依赖注入的原理"

            [其他命令]:
               clear  - 清空对话历史（开始新会话）
               config - 显示当前配置
               help   - 显示所有可用命令
               exit   - 退出程序

            [提示]:
               - agent 命令支持 -f 参数指定目标子文件夹
               - 文件夹不存在会自动创建
               - 需要操作文件时用 agent，纯提问用 ask
            ═══════════════════════════════════════════════════════════════
            """;
    }

    /**
     * 统一处理 AgentResponse 的控制台输出
     */
    private void printResponse(AgentResponse response, PrintWriter writer) {
        if (response.isSuccess()) {
            // 显示思考过程
            String reasoning = response.getReasoningContent();
            if (reasoning != null && !reasoning.isEmpty()) {
                writer.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                writer.println("[思考过程]");
                writer.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                writer.println(reasoning.trim());
                writer.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }

            // 显示响应内容
            String message = response.getMessage();
            if (message != null && !message.isEmpty()) {
                writer.println("\n" + message);
            } else if (reasoning == null || reasoning.isEmpty()) {
                writer.println("(无响应内容)");
            }

            // 显示耗时
            long durationMs = response.getDurationMs();
            String timeStr;
            if (durationMs < 1000) {
                timeStr = durationMs + "ms";
            } else {
                timeStr = String.format("%.1fs", durationMs / 1000.0);
            }
            writer.println("\n完成 (耗时: " + timeStr + ")\n");
        } else {
            writer.println("\n[错误] " + response.getError());
        }
        writer.flush();
    }
}
