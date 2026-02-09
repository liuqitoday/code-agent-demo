package com.liuqitech.codeagent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 轮次计数基于线程自动管理，无需外部重置
 */
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 基于线程的轮次计数器，每个线程（即每次用户请求）独立计数
     */
    private static final ThreadLocal<AtomicInteger> roundCounter =
            ThreadLocal.withInitial(() -> new AtomicInteger(0));

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                       ClientHttpRequestExecution execution) throws IOException {

        int currentRound = roundCounter.get().incrementAndGet();

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

    // ==================== 控制台输出（用户可见）====================

    private void printRequestToConsole(String requestBody, int round) {
        JsonNode root = parseJson(requestBody);
        if (root == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\n┌─────────────────────────────────────────────────────────────\n");
        sb.append("│ 📤 [Round ").append(round).append("] 发送请求给 LLM\n");
        sb.append("├─────────────────────────────────────────────────────────────\n");

        JsonNode messages = root.get("messages");
        if (messages != null && messages.isArray()) {
            // 第一轮显示 System Prompt
            if (round == 1) {
                String systemPrompt = findLastContentByRole(messages, "system");
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    sb.append("│ \n");
                    sb.append("│ 📋 System Prompt:\n");
                    appendMultilineContent(sb, systemPrompt, 300);
                }
            }

            // 用户消息
            String userMessage = findLastContentByRole(messages, "user");
            if (userMessage != null && !userMessage.isEmpty()) {
                sb.append("│ \n");
                sb.append("│ 👤 用户消息:\n");
                appendMultilineContent(sb, userMessage, 500);
            }

            // 工具结果
            String toolResult = findLastContentByRole(messages, "tool");
            if (toolResult != null && !toolResult.isEmpty()) {
                sb.append("│ \n");
                sb.append("│ 🔧 工具执行结果:\n");
                appendMultilineContent(sb, toolResult, 300);
            }
        }

        // 第一轮显示可用工具
        if (round == 1) {
            List<String> tools = extractToolNames(root);
            if (!tools.isEmpty()) {
                sb.append("│ \n");
                sb.append("│ 🛠️ 可用工具: ").append(String.join(", ", tools)).append("\n");
            }
        }

        sb.append("│ \n");
        sb.append("└─────────────────────────────────────────────────────────────\n");

        System.out.print(sb);
        System.out.flush();
    }

    private void appendMultilineContent(StringBuilder sb, String content, int maxLength) {
        if (content == null) return;

        boolean truncated = false;
        if (content.length() > maxLength) {
            content = content.substring(0, maxLength);
            truncated = true;
        }

        String[] lines = content.split("\n");
        for (String line : lines) {
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

    private void printResponseToConsole(String responseBody, long duration, int round) {
        JsonNode root = parseJson(responseBody);

        StringBuilder sb = new StringBuilder();
        sb.append("\n┌─────────────────────────────────────────────────────────────\n");
        sb.append("│ 📥 [Round ").append(round).append("] 收到 LLM 响应 (耗时: ").append(duration).append("ms)\n");
        sb.append("├─────────────────────────────────────────────────────────────\n");

        if (root != null) {
            // 检查是否有工具调用
            List<String> toolCalls = extractToolCalls(root);
            if (!toolCalls.isEmpty()) {
                sb.append("│ 🔧 LLM 决定调用工具:\n");
                for (String toolCall : toolCalls) {
                    sb.append("│    → ").append(toolCall).append("\n");
                }
                sb.append("│ \n");
                sb.append("│ [等待工具执行结果，然后继续下一轮对话...]\n");
            } else {
                String content = extractResponseContent(root);
                if (content != null && !content.isEmpty()) {
                    sb.append("│ 💬 LLM 最终回答:\n");
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

            // 思考过程
            String reasoning = extractReasoningContent(root);
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
        }

        sb.append("└─────────────────────────────────────────────────────────────\n");

        System.out.print(sb);
        System.out.flush();
    }

    // ==================== Jackson JSON 解析方法 ====================

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.debug("JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 在 messages 数组中找到指定角色的最后一条消息的 content
     */
    private String findLastContentByRole(JsonNode messages, String role) {
        String result = null;
        for (JsonNode msg : messages) {
            JsonNode roleNode = msg.get("role");
            if (roleNode != null && role.equals(roleNode.asText())) {
                JsonNode contentNode = msg.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    result = contentNode.asText();
                }
            }
        }
        return result;
    }

    /**
     * 从 tools 数组中提取工具名称列表
     */
    private List<String> extractToolNames(JsonNode root) {
        List<String> tools = new ArrayList<>();
        JsonNode toolsNode = root.get("tools");
        if (toolsNode != null && toolsNode.isArray()) {
            for (JsonNode tool : toolsNode) {
                JsonNode function = tool.get("function");
                if (function != null) {
                    JsonNode nameNode = function.get("name");
                    if (nameNode != null) {
                        tools.add(nameNode.asText());
                    }
                }
            }
        }
        return tools;
    }

    /**
     * 从响应中提取工具调用信息
     */
    private List<String> extractToolCalls(JsonNode root) {
        List<String> calls = new ArrayList<>();
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return calls;
        }

        JsonNode message = choices.get(0).get("message");
        if (message == null) return calls;

        JsonNode toolCallsNode = message.get("tool_calls");
        if (toolCallsNode == null || !toolCallsNode.isArray()) {
            return calls;
        }

        for (JsonNode tc : toolCallsNode) {
            JsonNode function = tc.get("function");
            if (function != null) {
                String name = function.has("name") ? function.get("name").asText() : "unknown";
                String args = "";
                if (function.has("arguments")) {
                    args = function.get("arguments").asText();
                    if (args.length() > 80) {
                        args = args.substring(0, 80) + "...";
                    }
                }
                calls.add(name + "(" + args + ")");
            }
        }
        return calls;
    }

    /**
     * 提取响应中 assistant 消息的 content
     */
    private String extractResponseContent(JsonNode root) {
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).get("message");
        if (message == null) return null;

        JsonNode contentNode = message.get("content");
        if (contentNode == null || contentNode.isNull()) return null;
        return contentNode.asText();
    }

    /**
     * 提取思考过程（reasoning_content）
     */
    private String extractReasoningContent(JsonNode root) {
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).get("message");
        if (message == null) return null;

        JsonNode reasoning = message.get("reasoning_content");
        if (reasoning == null || reasoning.isNull()) return null;
        return reasoning.asText();
    }

    // ==================== 日志文件记录（详细信息）====================

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
            log.info("║ {}", prettyPrint(requestBody));
        } else {
            log.info("║ Request Body: (empty)");
        }

        log.info("╚════════════════════════════════════════════════════════════════");
    }

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
            log.info("║ {}", prettyPrint(responseBody));
        } else {
            log.info("║ Response Body: (empty)");
        }

        log.info("╚════════════════════════════════════════════════════════════════");
        log.info("");
    }

    /**
     * 使用 Jackson 格式化 JSON，失败时返回原始字符串
     */
    private String prettyPrint(String json) {
        try {
            Object obj = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * 缓冲的 HTTP 响应包装器
     */
    private static class BufferedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse response;
        private final byte[] body;

        public BufferedClientHttpResponse(ClientHttpResponse response) throws IOException {
            this.response = response;
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
            return new java.io.ByteArrayInputStream(body);
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return response.getHeaders();
        }

        public String getBodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
