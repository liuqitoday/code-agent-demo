# Code Agent Demo

一个基于 Spring AI 的简易代码生成 Agent 示例项目，用于学习 Agent 的核心原理。

## 功能特性

- **自然语言驱动**：通过自然语言描述生成代码
- **Tool Calling**：支持 LLM 工具调用，自动创建文件
- **文件操作**：创建、读取文件和目录
- **对话记忆**：使用 Spring AI ChatMemory 支持多轮对话上下文
- **会话隔离**：支持 conversation ID 实现多会话隔离
- **交互式命令行**：基于 Spring Shell 的友好交互界面

## 技术栈

- Java 17
- Spring Boot 3.5.9
- Spring AI 1.1.2
- Spring Shell 3.4.1

## 快速开始

### 1. 配置 API Key

设置 OpenAI API Key 环境变量：

```bash
export OPENAI_API_KEY=your-api-key-here

# 如果使用代理或兼容 OpenAI 的 API（如 Azure、阿里云等）
export OPENAI_BASE_URL=https://your-api-endpoint
export OPENAI_MODEL=gpt-4
```

### 2. 运行项目

```bash
# 编译项目
mvn compile

# 运行 Agent
mvn spring-boot:run
```

### 3. 使用 Agent

启动后进入交互式命令行，可以使用以下命令：

```bash
# 生成代码并保存到文件
shell:> generate "创建一个 Java 的 UserService 类，包含登录和注册方法"

# 只展示代码不保存
shell:> generate -s false "写一个 Python 快速排序算法"

# 问答对话
shell:> ask "如何在 Java 中实现单例模式？"

# 查看使用帮助
shell:> guide

# 查看当前配置
shell:> config

# 清空对话历史
shell:> clear

# 退出程序
shell:> exit
```

## 项目结构

```
src/main/java/com/liuqitech/codeagent/
├── agent/                      # Agent 核心模块
│   ├── CodeAgent.java          # Agent 主类（核心逻辑）
│   └── AgentResponse.java      # Agent 响应封装
│
├── tool/                       # 工具模块
│   └── CodeAgentTools.java     # Agent 工具集（文件操作）
│
├── shell/                      # 命令行模块
│   └── AgentCommands.java      # Shell 命令定义
│
├── config/                     # 配置模块
│   ├── AiConfig.java           # AI 配置（ChatMemory、RestClient）
│   ├── AgentProperties.java    # Agent 属性配置
│   └── LoggingInterceptor.java # HTTP 日志拦截器
│
└── CodeAgentApplication.java   # 应用入口
```

> 注：对话记忆使用 Spring AI 内置的 `MessageWindowChatMemory`，通过 `MessageChatMemoryAdvisor` 自动管理。

## 配置说明

在 `application.yml` 中可以配置：

```yaml
# OpenAI 配置
spring.ai.openai:
  api-key: ${OPENAI_API_KEY}
  base-url: ${OPENAI_BASE_URL:https://api.openai.com}
  chat.options:
    model: gpt-4
    temperature: 0.7

# Agent 配置
agent:
  workspace: ./workspace    # 代码生成目录
  max-history: 20           # 最大对话历史（ChatMemory 窗口大小）
  default-language: java    # 默认编程语言
```

> 日志配置统一在 `logback-spring.xml` 中管理。

## 核心原理

详细的 Agent 原理和设计说明请参阅 [设计文档](docs/agent-design.md)。

### Agent 工作流程

```
用户输入 → Agent 构建 Prompt → 调用 LLM → Tool Calling → 执行工具 → 返回结果
```

### Tool Calling 机制

Agent 通过 Spring AI 的 `@Tool` 注解向 LLM 暴露可用工具：

```java
@Tool(description = "创建一个新文件并写入指定内容")
public String createFile(
    @ToolParam(description = "文件路径") String filePath,
    @ToolParam(description = "文件内容") String content
) {
    // 执行文件创建
}
```

LLM 在理解用户请求后，会自动决定是否需要调用这些工具。

## 示例

生成一个简单的 REST Controller：

```
shell:> generate "创建一个 Spring Boot 的 UserController，提供用户的增删改查 REST API"
```

Agent 会：
1. 理解需求
2. 生成符合规范的代码
3. 调用 `createFile` 工具保存代码
4. 返回执行结果

## License

MIT
