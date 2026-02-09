# LLM 交互日志详解

## 概述

本项目已配置详细的 LLM 交互日志，可以完整记录与大模型的所有通信过程。

## 新增功能

### 1. HTTP 请求/响应拦截器

新增了 `LoggingInterceptor` 类，会自动拦截并记录所有与 LLM 的 HTTP 通信。

**位置**: `src/main/java/com/liuqitech/codeagent/config/LoggingInterceptor.java`

**功能**:
- ✅ 记录完整的 HTTP 请求（URL、方法、Headers、请求体）
- ✅ 记录完整的 HTTP 响应（状态码、Headers、响应体）
- ✅ 自动格式化 JSON 内容，便于阅读
- ✅ 隐藏敏感信息（如 API Key）
- ✅ 记录请求耗时

### 2. 日志输出格式

日志分为两层：**控制台**（用户可见的摘要）和**日志文件**（完整 HTTP 详情）。

#### 控制台输出（Round-based 摘要）

每次 LLM 交互以 Round 为单位展示关键信息：

```
┌─────────────────────────────────────────────────────────────
│ 📤 [Round 1] 发送请求给 LLM
├─────────────────────────────────────────────────────────────
│
│ 📋 System Prompt:
│    你是一个专业的代码生成助手...
│
│ 👤 用户消息:
│    创建一个 Hello World 的 Java 类
│
│ 🛠️ 可用工具: createFile, readFile, editFile, editFileAll, listDirectory, createDirectory
│
└─────────────────────────────────────────────────────────────

┌─────────────────────────────────────────────────────────────
│ 📥 [Round 1] 收到 LLM 响应 (耗时: 2345ms)
├─────────────────────────────────────────────────────────────
│ 🔧 LLM 决定调用工具:
│    → createFile({"filePath":"HelloWorld.java","content":"..."})
│
│ [等待工具执行结果，然后继续下一轮对话...]
└─────────────────────────────────────────────────────────────

┌─────────────────────────────────────────────────────────────
│ 📤 [Round 2] 发送请求给 LLM
├─────────────────────────────────────────────────────────────
│
│ 🔧 工具执行结果:
│    成功创建文件: /path/to/workspace/HelloWorld.java
│
└─────────────────────────────────────────────────────────────

┌─────────────────────────────────────────────────────────────
│ 📥 [Round 2] 收到 LLM 响应 (耗时: 1234ms)
├─────────────────────────────────────────────────────────────
│ 💬 LLM 最终回答:
│    已成功创建文件 HelloWorld.java...
└─────────────────────────────────────────────────────────────
```

> Round 计数器在每次新的用户请求时自动重置为 1。
> 并行工具调用时，所有工具结果会带编号显示。

#### 日志文件（完整 HTTP 详情）

详细的 HTTP 通信记录在 `logs/code-agent.log` 中：

```
╔════════════════════════════════════════════════════════════════
║ 📤 [Round 1] HTTP 请求 - 发送给 LLM
╠════════════════════════════════════════════════════════════════
║ Method: POST
║ URI: https://api.openai.com/v1/chat/completions
╠════════════════════════════════════════════════════════════════
║ Headers:
║   Authorization: Bearer ****** (已隐藏)
║   Content-Type: [application/json]
╠════════════════════════════════════════════════════════════════
║ Request Body:
╠════════════════════════════════════════════════════════════════
║ { "model": "gpt-4", "messages": [...], "tools": [...] }
╚════════════════════════════════════════════════════════════════
```

## 如何查看日志

### 方法 1: 控制台输出

运行应用时，日志会直接输出到控制台：

```bash
mvn spring-boot:run
```

### 方法 2: 日志文件

日志配置在 `logback-spring.xml` 中，默认输出到 `logs/code-agent.log`：

```xml
<!-- logback-spring.xml -->
<property name="LOG_FILE" value="logs/code-agent.log"/>

<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_FILE}</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <fileNamePattern>logs/code-agent-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
        <maxFileSize>10MB</maxFileSize>
        <maxHistory>30</maxHistory>
    </rollingPolicy>
</appender>
```

查看日志文件：

```bash
# 实时查看
tail -f logs/code-agent.log

# 或者直接打开
cat logs/code-agent.log
```

## 日志级别说明

日志配置统一在 `logback-spring.xml` 中管理：

```xml
<!-- logback-spring.xml -->

<!-- 项目日志 - DEBUG 级别 -->
<logger name="com.liuqitech.codeagent" level="DEBUG"/>

<!-- Spring AI 日志 - INFO 级别 -->
<logger name="org.springframework.ai" level="INFO"/>

<!-- 隐藏 Spring AI 重试日志 -->
<logger name="org.springframework.ai.retry" level="ERROR"/>

<!-- Spring 框架日志 - WARN 级别 -->
<logger name="org.springframework" level="WARN"/>

<!-- 隐藏 Netty/Reactor 日志 -->
<logger name="io.netty" level="ERROR"/>
<logger name="reactor.netty" level="ERROR"/>

<!-- 隐藏 Shell/JLine 日志 -->
<logger name="org.springframework.shell" level="WARN"/>
<logger name="org.jline" level="WARN"/>
```

> **注意**：日志配置已从 `application.yml` 移至 `logback-spring.xml`，便于更精细的控制（如按 appender 区分输出）。

### 日志内容包括：

1. **Agent 执行流程**
   - 用户请求接收
   - 对话历史管理
   - Prompt 构建过程
   - LLM 调用状态

2. **HTTP 通信详情**（新增）
   - 完整的请求 JSON（包含 system prompt、messages、tools 等）
   - 完整的响应 JSON（包含 LLM 的回复、tool_calls 等）
   - 请求耗时统计

3. **Tool Calling 流程**
   - LLM 决定调用哪些工具
   - 工具执行过程
   - 工具返回结果

4. **错误处理**
   - 超时重试逻辑
   - 错误详情和堆栈

## 关键日志位置

### 1. LoggingInterceptor.java

控制台摘要输出和日志文件详细记录均在此类中实现：
- `printRequestToConsole()` - Round-based 控制台请求摘要
- `printResponseToConsole()` - Round-based 控制台响应摘要
- `logRequest()` / `logResponse()` - 完整 HTTP 详情写入日志文件

### 2. LlmService.java

LLM 调用和重试逻辑，包含重试日志。

### 3. CodeAgent.java

记录 Agent 执行流程：请求接收、响应处理、耗时统计。

## 调试技巧

### 查看完整的请求内容

在日志文件中搜索 `📤 [Round`，可以看到：
- 发送给 LLM 的完整 JSON
- 包含的 system prompt
- 所有可用的 tools 定义
- 对话历史

### 查看完整的响应内容

在日志文件中搜索 `📥 [Round`，可以看到：
- LLM 的完整回复
- Tool calling 决策
- 生成的内容

### 查看 Tool Calling 流程

在控制台输出中观察 Round 序号：
- Round 1 请求：用户消息 + 可用工具列表
- Round 1 响应：LLM 决定调用的工具
- Round 2 请求：工具执行结果
- Round 2 响应：LLM 最终回答（或继续调用工具）

## 性能影响

- 日志拦截器对性能影响很小（< 10ms）
- 响应体缓存使用内存较少（通常 < 100KB）
- 如果不需要详细日志，可以将日志级别改为 INFO 或 WARN

## 隐私和安全

- ✅ API Key 会自动隐藏（显示为 `******`）
- ⚠️ 用户输入和 LLM 响应会完整记录
- ⚠️ 如果处理敏感数据，建议不要将日志文件提交到版本控制

## 故障排查

### 问题：看不到 HTTP 请求/响应日志

**解决方案**：
1. 确认 `LoggingInterceptor` 已在 `AiConfig` 中注册
2. 确认日志级别为 DEBUG
3. 检查是否有其他日志配置覆盖了设置

### 问题：日志输出太多

**解决方案**：
在 `logback-spring.xml` 中调整日志级别为 INFO：

```xml
<logger name="com.liuqitech.codeagent" level="INFO"/>
```

### 问题：JSON 格式化不正确

**解决方案**：
`LoggingInterceptor` 使用简化的 JSON 格式化器。如果需要更好的格式化，可以：
1. 使用 Jackson 或 Gson 库
2. 或者直接查看原始 JSON（不格式化）

## 示例输出

运行 `agent "创建一个 Hello World 的 Java 类"` 后，控制台会显示：

1. **Round 1 请求** — 用户消息和可用工具
2. **Round 1 响应** — LLM 决定调用 `createFile` 工具
3. **Round 2 请求** — 工具执行结果（文件创建成功）
4. **Round 2 响应** — LLM 最终回答

## 总结

通过这些详细的日志，你可以：
- ✅ 完整了解 LLM 的工作原理
- ✅ 调试 Tool Calling 问题
- ✅ 优化 Prompt 设计
- ✅ 分析性能瓶颈
- ✅ 学习 Spring AI 的内部机制
