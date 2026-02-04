package com.liuqitech.codeagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * HTTP 请求日志拦截器
 * 用于记录所有发送给 LLM 的请求和接收到的响应
 *
 * 这个拦截器会：
 * 1. 在控制台显示与 LLM 交互的关键信息（便于学习理解）
 * 2. 在日志文件中记录完整的请求/响应详情（便于调试）
 */
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    // 用于跟踪当前是第几轮对话（线程安全）
    private static final AtomicInteger roundCounter = new AtomicInteger(0);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                       ClientHttpRequestExecution execution) throws IOException {

        int currentRound = roundCounter.incrementAndGet();

        // ========== 记录请求 ==========
        String requestBody = new String(body, StandardCharsets.UTF_8);
        logRequest(request, requestBody, currentRound);
        printRequestToConsole(requestBody, currentRound);

        // 执行实际的 HTTP 请求
        long startTime = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long duration = System.currentTimeMillis() - startTime;

        // 包装响应以支持多次读取响应体
        BufferedClientHttpResponse bufferedResponse = new BufferedClientHttpResponse(response);

        // ========== 记录响应 ==========
        String responseBody = bufferedResponse.getBodyAsString();
        logResponse(bufferedResponse, responseBody, duration, currentRound);
        printResponseToConsole(responseBody, duration, currentRound);

        return bufferedResponse;
    }

    /**
     * 重置轮次计数器（在新的用户请求开始时调用）
     */
    public static void resetRoundCounter() {
        roundCounter.set(0);
    }

    // ==================== 控制台输出（用户可见）====================

    /**
     * 在控制台显示请求摘要
     */
    private void printRequestToConsole(String requestBody, int round) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n┌─────────────────────────────────────────────────────────────\n");
        sb.append("│ 📤 [Round ").append(round).append("] 发送请求给 LLM\n");
        sb.append("├─────────────────────────────────────────────────────────────\n");

        // 提取并显示 System Prompt（仅第一轮显示）
        if (round == 1) {
            String systemPrompt = extractSystemPrompt(requestBody);
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                sb.append("│ \n");
                sb.append("│ 📋 System Prompt:\n");
                appendMultilineContent(sb, systemPrompt, 300);
            }
        }

        // 提取并显示用户消息
        String userMessage = extractLastUserMessage(requestBody);
        if (userMessage != null && !userMessage.isEmpty()) {
            sb.append("│ \n");
            sb.append("│ 👤 用户消息:\n");
            appendMultilineContent(sb, userMessage, 500);
        }

        // 提取并显示工具结果（如果有）
        String toolResult = extractToolResult(requestBody);
        if (toolResult != null && !toolResult.isEmpty()) {
            sb.append("│ \n");
            sb.append("│ 🔧 工具执行结果:\n");
            appendMultilineContent(sb, toolResult, 300);
        }

        // 显示可用工具（仅第一轮显示）
        if (round == 1) {
            List<String> tools = extractToolNames(requestBody);
            if (!tools.isEmpty()) {
                sb.append("│ \n");
                sb.append("│ 🛠️ 可用工具: ").append(String.join(", ", tools)).append("\n");
            }
        }

        sb.append("│ \n");
        sb.append("└─────────────────────────────────────────────────────────────\n");

        System.out.print(sb.toString());
        System.out.flush();
    }

    /**
     * 追加多行内容到 StringBuilder，带缩进和长度限制
     */
    private void appendMultilineContent(StringBuilder sb, String content, int maxLength) {
        if (content == null) return;

        // 截断过长的内容
        boolean truncated = false;
        if (content.length() > maxLength) {
            content = content.substring(0, maxLength);
            truncated = true;
        }

        String[] lines = content.split("\n");
        for (String line : lines) {
            // 每行也限制长度
            if (line.length() > 70) {
                sb.append("│    ").append(line, 0, 70).append("\n");
                sb.append("│    ").append(line.substring(70, Math.min(line.length(), 140)));
                if (line.length() > 140) sb.append("...");
                sb.append("\n");
            } else {
                sb.append("│    ").append(line).append("\n");
            }
        }
        if (truncated) {
            sb.append("│    ... (内容已截断)\n");
        }
    }

    /**
     * 在控制台显示响应摘要
     */
    private void printResponseToConsole(String responseBody, long duration, int round) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n┌─────────────────────────────────────────────────────────────\n");
        sb.append("│ 📥 [Round ").append(round).append("] 收到 LLM 响应 (耗时: ").append(duration).append("ms)\n");
        sb.append("├─────────────────────────────────────────────────────────────\n");

        // 检查是否有工具调用
        List<String> toolCalls = extractToolCalls(responseBody);
        if (!toolCalls.isEmpty()) {
            sb.append("│ 🔧 LLM 决定调用工具:\n");
            for (String toolCall : toolCalls) {
                sb.append("│    → ").append(toolCall).append("\n");
            }
            sb.append("│ \n");
            sb.append("│ [等待工具执行结果，然后继续下一轮对话...]\n");
        } else {
            // 提取最终回答
            String content = extractContent(responseBody);
            if (content != null && !content.isEmpty()) {
                sb.append("│ 💬 LLM 最终回答:\n");
                // 显示回答的前几行
                String[] lines = content.split("\n");
                int maxLines = Math.min(lines.length, 5);
                for (int i = 0; i < maxLines; i++) {
                    String line = lines[i];
                    if (line.length() > 70) {
                        line = line.substring(0, 70) + "...";
                    }
                    sb.append("│    ").append(line).append("\n");
                }
                if (lines.length > maxLines) {
                    sb.append("│    ... (共 ").append(lines.length).append(" 行)\n");
                }
            }
        }

        // 显示思考过程（如果有）
        String reasoning = extractReasoningContent(responseBody);
        if (reasoning != null && !reasoning.isEmpty()) {
            sb.append("│ \n");
            sb.append("│ 💭 思考过程: ");
            if (reasoning.length() > 100) {
                sb.append(reasoning, 0, 100).append("...");
            } else {
                sb.append(reasoning);
            }
            sb.append("\n");
        }

        sb.append("└─────────────────────────────────────────────────────────────\n");

        System.out.print(sb.toString());
        System.out.flush();
    }

    // ==================== JSON 解析辅助方法 ====================

    /**
     * 提取最后一条用户消息
     * JSON 格式: {"content":"xxx","role":"user"}
     */
    private String extractLastUserMessage(String json) {
        return extractContentByRole(json, "user");
    }

    /**
     * 提取工具执行结果
     * JSON 格式: {"content":"xxx","role":"tool",...}
     */
    private String extractToolResult(String json) {
        return extractContentByRole(json, "tool");
    }

    /**
     * 提取 System Prompt
     * JSON 格式: {"content":"xxx","role":"system"}
     */
    private String extractSystemPrompt(String json) {
        return extractContentByRole(json, "system");
    }

    /**
     * 根据角色提取内容（使用简单字符串处理，避免正则栈溢出）
     */
    private String extractContentByRole(String json, String role) {
        String rolePattern = "\"role\":\"" + role + "\"";
        String rolePatternWithSpace = "\"role\": \"" + role + "\"";

        int roleIndex = json.lastIndexOf(rolePattern);
        if (roleIndex == -1) {
            roleIndex = json.lastIndexOf(rolePatternWithSpace);
        }
        if (roleIndex == -1) {
            return null;
        }

        // 向前查找对应的 content
        // 找到这个 role 之前最近的 "content":"
        String contentKey = "\"content\":\"";
        String contentKeyWithSpace = "\"content\": \"";

        int contentIndex = json.lastIndexOf(contentKey, roleIndex);
        if (contentIndex == -1) {
            contentIndex = json.lastIndexOf(contentKeyWithSpace, roleIndex);
            if (contentIndex != -1) {
                contentIndex += contentKeyWithSpace.length();
            }
        } else {
            contentIndex += contentKey.length();
        }

        if (contentIndex == -1 || contentIndex >= roleIndex) {
            return null;
        }

        // 从 contentIndex 开始，找到结束引号（处理转义）
        return extractQuotedString(json, contentIndex);
    }

    /**
     * 从指定位置提取引号内的字符串（处理转义字符）
     */
    private String extractQuotedString(String json, int startIndex) {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;

        for (int i = startIndex; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                result.append(c);
                escaped = false;
            } else if (c == '\\') {
                result.append(c);
                escaped = true;
            } else if (c == '"') {
                // 找到结束引号
                break;
            } else {
                result.append(c);
            }
        }

        return unescapeJson(result.toString());
    }

    /**
     * 提取可用工具名称列表（使用简单字符串处理）
     */
    private List<String> extractToolNames(String json) {
        List<String> tools = new ArrayList<>();
        String searchKey = "\"name\":";
        int index = 0;

        while ((index = json.indexOf(searchKey, index)) != -1) {
            // 检查是否在 function 块内
            int funcIndex = json.lastIndexOf("\"function\"", index);
            if (funcIndex != -1 && funcIndex > json.lastIndexOf("}", index)) {
                // 找到 name 后面的值
                int valueStart = json.indexOf("\"", index + searchKey.length());
                if (valueStart != -1) {
                    int valueEnd = json.indexOf("\"", valueStart + 1);
                    if (valueEnd != -1) {
                        String toolName = json.substring(valueStart + 1, valueEnd);
                        if (!tools.contains(toolName) && !toolName.isEmpty()) {
                            tools.add(toolName);
                        }
                    }
                }
            }
            index += searchKey.length();
        }
        return tools;
    }

    /**
     * 提取工具调用信息（使用简单字符串处理）
     */
    private List<String> extractToolCalls(String json) {
        List<String> calls = new ArrayList<>();

        // 查找 tool_calls 数组
        int toolCallsIndex = json.indexOf("\"tool_calls\"");
        if (toolCallsIndex == -1) {
            return calls;
        }

        // 在 tool_calls 之后查找 function
        String searchKey = "\"function\"";
        int index = toolCallsIndex;

        while ((index = json.indexOf(searchKey, index)) != -1) {
            // 提取函数名
            int nameIndex = json.indexOf("\"name\"", index);
            if (nameIndex != -1 && nameIndex < index + 200) {
                int nameStart = json.indexOf("\"", nameIndex + 6);
                int nameEnd = nameStart != -1 ? json.indexOf("\"", nameStart + 1) : -1;

                if (nameStart != -1 && nameEnd != -1) {
                    String funcName = json.substring(nameStart + 1, nameEnd);

                    // 提取参数
                    int argsIndex = json.indexOf("\"arguments\"", nameEnd);
                    String args = "";
                    if (argsIndex != -1 && argsIndex < nameEnd + 100) {
                        int argsStart = json.indexOf("\"", argsIndex + 11);
                        if (argsStart != -1) {
                            args = extractQuotedString(json, argsStart + 1);
                            if (args != null && args.length() > 80) {
                                args = args.substring(0, 80) + "...";
                            }
                        }
                    }

                    calls.add(funcName + "(" + (args != null ? args : "") + ")");
                }
            }
            index += searchKey.length();
        }
        return calls;
    }

    /**
     * 提取响应内容（在 choices 中的 assistant 消息）
     */
    private String extractContent(String json) {
        // 查找 choices 数组中的 content
        int choicesIndex = json.indexOf("\"choices\"");
        if (choicesIndex == -1) {
            return null;
        }

        // 在 choices 之后查找 message 中的 content
        int messageIndex = json.indexOf("\"message\"", choicesIndex);
        if (messageIndex == -1) {
            return null;
        }

        int contentIndex = json.indexOf("\"content\"", messageIndex);
        if (contentIndex == -1) {
            return null;
        }

        // 找到 content 的值
        int valueStart = json.indexOf(":", contentIndex + 9);
        if (valueStart == -1) {
            return null;
        }

        // 跳过空格找到引号
        int quoteStart = -1;
        for (int i = valueStart + 1; i < json.length() && i < valueStart + 10; i++) {
            if (json.charAt(i) == '"') {
                quoteStart = i;
                break;
            } else if (json.charAt(i) == 'n') {
                // null 值
                return null;
            }
        }

        if (quoteStart == -1) {
            return null;
        }

        return extractQuotedString(json, quoteStart + 1);
    }

    /**
     * 提取思考过程
     */
    private String extractReasoningContent(String json) {
        String key = "\"reasoning_content\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) {
            return null;
        }

        int valueStart = json.indexOf("\"", keyIndex + key.length() + 1);
        if (valueStart == -1) {
            return null;
        }

        return extractQuotedString(json, valueStart + 1);
    }

    /**
     * 反转义 JSON 字符串
     */
    private String unescapeJson(String str) {
        if (str == null) return null;
        return str.replace("\\n", "\n")
                  .replace("\\t", "\t")
                  .replace("\\\"", "\"")
                  .replace("\\\\", "\\");
    }

    // ==================== 日志文件记录（详细信息）====================

    /**
     * 记录 HTTP 请求详情到日志文件
     */
    private void logRequest(HttpRequest request, String requestBody, int round) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════");
        log.info("║ 📤 [Round {}] HTTP 请求 - 发送给 LLM", round);
        log.info("╠════════════════════════════════════════════════════════════════");
        log.info("║ Method: {}", request.getMethod());
        log.info("║ URI: {}", request.getURI());
        log.info("╠════════════════════════════════════════════════════════════════");
        log.info("║ Headers:");
        request.getHeaders().forEach((name, values) -> {
            if (name.equalsIgnoreCase("Authorization")) {
                log.info("║   {}: Bearer ****** (已隐藏)", name);
            } else {
                log.info("║   {}: {}", name, values);
            }
        });
        log.info("╠════════════════════════════════════════════════════════════════");

        if (!requestBody.isEmpty()) {
            log.info("║ Request Body:");
            log.info("╠════════════════════════════════════════════════════════════════");
            if (isJson(requestBody)) {
                log.info("║ {}", formatJson(requestBody));
            } else {
                log.info("║ {}", requestBody);
            }
        } else {
            log.info("║ Request Body: (empty)");
        }

        log.info("╚════════════════════════════════════════════════════════════════");
    }

    /**
     * 记录 HTTP 响应详情到日志文件
     */
    private void logResponse(BufferedClientHttpResponse response, String responseBody, long duration, int round) throws IOException {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════");
        log.info("║ 📥 [Round {}] HTTP 响应 - 从 LLM 接收", round);
        log.info("╠════════════════════════════════════════════════════════════════");
        log.info("║ Status Code: {} {}", response.getStatusCode().value(), response.getStatusText());
        log.info("║ Duration: {} ms", duration);
        log.info("╠════════════════════════════════════════════════════════════════");
        log.info("║ Headers:");
        response.getHeaders().forEach((name, values) -> {
            log.info("║   {}: {}", name, values);
        });
        log.info("╠════════════════════════════════════════════════════════════════");

        if (!responseBody.isEmpty()) {
            log.info("║ Response Body:");
            log.info("╠════════════════════════════════════════════════════════════════");
            if (isJson(responseBody)) {
                log.info("║ {}", formatJson(responseBody));
            } else {
                log.info("║ {}", responseBody);
            }
        } else {
            log.info("║ Response Body: (empty)");
        }

        log.info("╚════════════════════════════════════════════════════════════════");
        log.info("");
    }

    /**
     * 判断字符串是否为 JSON
     */
    private boolean isJson(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        String trimmed = str.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
            || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    /**
     * 简单的 JSON 格式化（用于日志输出）
     * 注意：这是一个简化版本，仅用于日志美化
     */
    private String formatJson(String json) {
        try {
            // 简单的缩进处理
            StringBuilder formatted = new StringBuilder();
            int indent = 0;
            boolean inString = false;
            boolean escape = false;

            for (char c : json.toCharArray()) {
                if (escape) {
                    formatted.append(c);
                    escape = false;
                    continue;
                }

                if (c == '\\') {
                    formatted.append(c);
                    escape = true;
                    continue;
                }

                if (c == '"') {
                    inString = !inString;
                    formatted.append(c);
                    continue;
                }

                if (inString) {
                    formatted.append(c);
                    continue;
                }

                switch (c) {
                    case '{':
                    case '[':
                        formatted.append(c);
                        formatted.append('\n');
                        indent++;
                        formatted.append("║ ").append("  ".repeat(indent));
                        break;
                    case '}':
                    case ']':
                        formatted.append('\n');
                        indent--;
                        formatted.append("║ ").append("  ".repeat(indent));
                        formatted.append(c);
                        break;
                    case ',':
                        formatted.append(c);
                        formatted.append('\n');
                        formatted.append("║ ").append("  ".repeat(indent));
                        break;
                    case ':':
                        formatted.append(c);
                        formatted.append(' ');
                        break;
                    case ' ':
                    case '\n':
                    case '\r':
                    case '\t':
                        // 跳过空白字符
                        break;
                    default:
                        formatted.append(c);
                }
            }

            return formatted.toString();
        } catch (Exception e) {
            // 如果格式化失败，返回原始字符串
            return json;
        }
    }

    /**
     * 缓冲的 HTTP 响应包装器
     * 允许多次读取响应体（用于日志记录后仍能被 Spring AI 读取）
     */
    private static class BufferedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse response;
        private byte[] body;

        public BufferedClientHttpResponse(ClientHttpResponse response) throws IOException {
            this.response = response;
            // 立即读取并缓存响应体
            this.body = new BufferedReader(
                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"))
                .getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return response.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return response.getStatusText();
        }

        @Override
        public void close() {
            response.close();
        }

        @Override
        public java.io.InputStream getBody() throws IOException {
            // 返回缓存的响应体
            return new java.io.ByteArrayInputStream(body);
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return response.getHeaders();
        }

        /**
         * 获取缓存的响应体字符串（用于日志记录）
         */
        public String getBodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
