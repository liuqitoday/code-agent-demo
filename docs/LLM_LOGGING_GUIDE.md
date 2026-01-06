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

日志使用美化的边框格式，易于识别：

```
╔════════════════════════════════════════════════════════════════
║ 📤 [HTTP 请求] 发送给 LLM
╠════════════════════════════════════════════════════════════════
║ Method: POST
║ URI: https://ai.zzhdsgsss.xyz/v1/chat/completions
╠════════════════════════════════════════════════════════════════
║ Headers:
║   Authorization: Bearer ****** (已隐藏)
║   Content-Type: [application/json]
╠════════════════════════════════════════════════════════════════
║ Request Body:
╠════════════════════════════════════════════════════════════════
║ {
║   "model": "zai-org/GLM-4.5",
║   "messages": [
║     {
║       "role": "system",
║       "content": "你是一个专业的代码生成助手..."
║     },
║     {
║       "role": "user",
║       "content": "创建一个 Hello World 的 Java 类"
║     }
║   ],
║   "temperature": 0.7,
║   "tools": [...]
║ }
╚════════════════════════════════════════════════════════════════

╔════════════════════════════════════════════════════════════════
║ 📥 [HTTP 响应] 从 LLM 接收
╠════════════════════════════════════════════════════════════════
║ Status Code: 200 OK
║ Duration: 2345 ms
╠════════════════════════════════════════════════════════════════
║ Headers:
║   Content-Type: [application/json]
╠════════════════════════════════════════════════════════════════
║ Response Body:
╠════════════════════════════════════════════════════════════════
║ {
║   "id": "chatcmpl-xxx",
║   "object": "chat.completion",
║   "choices": [
║     {
║       "message": {
║         "role": "assistant",
║         "content": "[Thought]: 用户需要一个简单的 Java Hello World 程序...",
║         "tool_calls": [
║           {
║             "function": {
║               "name": "createFile",
║               "arguments": "{\"filePath\":\"HelloWorld.java\",\"content\":\"...\"}"
║             }
║           }
║         ]
║       }
║     }
║   ]
║ }
╚════════════════════════════════════════════════════════════════
```

## 如何查看日志

### 方法 1: 控制台输出

运行应用时，日志会直接输出到控制台：

```bash
mvn spring-boot:run
```

### 方法 2: 保存到文件

如果想保存日志到文件，可以重定向输出：

```bash
mvn spring-boot:run > logs/app.log 2>&1
```

或者在 `application.yml` 中配置日志文件：

```yaml
logging:
  file:
    name: logs/code-agent.log
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

## 日志级别说明

当前配置的日志级别（`application.yml`）：

```yaml
logging:
  level:
    # 项目日志 - DEBUG 级别
    com.liuqitech.codeagent: DEBUG

    # Spring AI 核心日志 - DEBUG 级别
    org.springframework.ai.chat.client: DEBUG
    org.springframework.ai.chat.model: DEBUG
    org.springframework.ai.chat.client.advisor: DEBUG
    org.springframework.ai.model.function: DEBUG

    # OpenAI API 日志 - DEBUG 级别
    org.springframework.ai.openai: DEBUG
    org.springframework.ai.openai.api: DEBUG

    # HTTP 请求日志 - DEBUG 级别
    org.springframework.web.client.RestClient: DEBUG
    org.springframework.http.client: DEBUG
```

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

### 1. CodeAgent.java (第 165-177 行)

记录完整的上下文信息：
- System Prompt
- 对话历史
- 用户输入

### 2. LoggingInterceptor.java

记录所有 HTTP 请求和响应的完整内容。

## 调试技巧

### 查看完整的请求内容

在日志中搜索 `📤 [HTTP 请求]`，可以看到：
- 发送给 LLM 的完整 JSON
- 包含的 system prompt
- 所有可用的 tools 定义
- 对话历史

### 查看完整的响应内容

在日志中搜索 `📥 [HTTP 响应]`，可以看到：
- LLM 的完整回复
- Tool calling 决策
- 生成的内容

### 查看 Tool Calling 流程

在日志中搜索 `[Step 4]`，可以看到：
- LLM 是否决定调用工具
- 调用了哪些工具
- 工具的参数和返回值

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
调整日志级别为 INFO：

```yaml
logging:
  level:
    com.liuqitech.codeagent: INFO
```

### 问题：JSON 格式化不正确

**解决方案**：
`LoggingInterceptor` 使用简化的 JSON 格式化器。如果需要更好的格式化，可以：
1. 使用 Jackson 或 Gson 库
2. 或者直接查看原始 JSON（不格式化）

## 示例输出

运行 `generate 创建一个 Hello World 的 Java 类` 后，你会看到：

1. **Agent 接收请求**
```
[Step 1] 收到用户请求
用户输入: 创建一个 Hello World 的 Java 类
```

2. **HTTP 请求发送**
```
📤 [HTTP 请求] 发送给 LLM
Method: POST
URI: https://ai.zzhdsgsss.xyz/v1/chat/completions
Request Body: { "model": "zai-org/GLM-4.5", ... }
```

3. **HTTP 响应接收**
```
📥 [HTTP 响应] 从 LLM 接收
Status Code: 200 OK
Duration: 2345 ms
Response Body: { "choices": [...], "tool_calls": [...] }
```

4. **Tool 执行**
```
[Tool] createFile 被调用
参数: filePath=HelloWorld.java, content=...
```

5. **最终响应**
```
[完成] 请求处理成功!
```

## 总结

通过这些详细的日志，你可以：
- ✅ 完整了解 LLM 的工作原理
- ✅ 调试 Tool Calling 问题
- ✅ 优化 Prompt 设计
- ✅ 分析性能瓶颈
- ✅ 学习 Spring AI 的内部机制
