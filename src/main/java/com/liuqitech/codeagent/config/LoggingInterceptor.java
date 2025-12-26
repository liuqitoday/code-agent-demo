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
import java.util.stream.Collectors;

/**
 * HTTP 请求日志拦截器
 * 用于记录所有发送给 LLM 的请求和接收到的响应
 *
 * 这个拦截器会详细记录：
 * 1. 请求的 URL、方法、Headers
 * 2. 请求体（完整的 JSON）
 * 3. 响应状态码、Headers
 * 4. 响应体（完整的 JSON）
 *
 * 注意：所有日志只记录到文件，不输出到控制台
 */
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                       ClientHttpRequestExecution execution) throws IOException {

        // ========== 记录请求 ==========
        logRequest(request, body);

        // 执行实际的 HTTP 请求
        long startTime = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long duration = System.currentTimeMillis() - startTime;

        // 包装响应以支持多次读取响应体
        BufferedClientHttpResponse bufferedResponse = new BufferedClientHttpResponse(response);

        // ========== 记录响应 ==========
        logResponse(bufferedResponse, duration);

        return bufferedResponse;
    }

    /**
     * 记录 HTTP 请求详情
     */
    private void logRequest(HttpRequest request, byte[] body) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════");
        log.info("║ 📤 [HTTP 请求] 发送给 LLM");
        log.info("╠════════════════════════════════════════════════════════════════");
        log.info("║ Method: {}", request.getMethod());
        log.info("║ URI: {}", request.getURI());
        log.info("╠════════════════════════════════════════════════════════════════");
        log.info("║ Headers:");
        request.getHeaders().forEach((name, values) -> {
            // 隐藏敏感信息（API Key）
            if (name.equalsIgnoreCase("Authorization")) {
                log.info("║   {}: Bearer ****** (已隐藏)", name);
            } else {
                log.info("║   {}: {}", name, values);
            }
        });
        log.info("╠════════════════════════════════════════════════════════════════");

        // 记录请求体
        if (body.length > 0) {
            String requestBody = new String(body, StandardCharsets.UTF_8);
            log.info("║ Request Body:");
            log.info("╠════════════════════════════════════════════════════════════════");

            // 格式化 JSON（如果是 JSON）
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
     * 记录 HTTP 响应详情
     */
    private void logResponse(BufferedClientHttpResponse response, long duration) throws IOException {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════");
        log.info("║ 📥 [HTTP 响应] 从 LLM 接收");
        log.info("╠════════════════════════════════════════════════════════════════");
        log.info("║ Status Code: {} {}", response.getStatusCode().value(), response.getStatusText());
        log.info("║ Duration: {} ms", duration);
        log.info("╠════════════════════════════════════════════════════════════════");
        log.info("║ Headers:");
        response.getHeaders().forEach((name, values) -> {
            log.info("║   {}: {}", name, values);
        });
        log.info("╠════════════════════════════════════════════════════════════════");

        // 从缓存中读取响应体
        String responseBody = response.getBodyAsString();

        if (!responseBody.isEmpty()) {
            log.info("║ Response Body:");
            log.info("╠════════════════════════════════════════════════════════════════");

            // 格式化 JSON（如果是 JSON）
            if (isJson(responseBody)) {
                log.info("║ {}", formatJson(responseBody));

                // 提取并输出reasoning_content到控制台
                extractAndPrintReasoningContent(responseBody);
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
     * 提取并输出reasoning_content到控制台
     */
    private void extractAndPrintReasoningContent(String jsonResponse) {
        try {
            // 简单的JSON解析，查找reasoning_content字段
            int reasoningIndex = jsonResponse.indexOf("\"reasoning_content\"");
            if (reasoningIndex == -1) {
                return; // 没有找到reasoning_content字段
            }

            // 找到值的开始位置
            int valueStart = jsonResponse.indexOf("\"", reasoningIndex + "\"reasoning_content\"".length());
            if (valueStart == -1) {
                return;
            }
            valueStart++; // 跳过引号

            // 找到值的结束位置（需要处理转义字符）
            int valueEnd = valueStart;
            boolean escaped = false;
            while (valueEnd < jsonResponse.length()) {
                char c = jsonResponse.charAt(valueEnd);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break; // 找到结束引号
                }
                valueEnd++;
            }

            if (valueEnd >= jsonResponse.length()) {
                return; // 没有找到结束引号
            }

            // 提取reasoning_content的值
            String reasoningContent = jsonResponse.substring(valueStart, valueEnd);

            // 处理转义字符
            reasoningContent = reasoningContent
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");

            // 如果内容不为空，输出到控制台
            if (!reasoningContent.trim().isEmpty()) {
                System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("💭 [思考过程]");
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println(reasoningContent.trim());
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                log.debug("已提取reasoning_content并输出到控制台");
            }
        } catch (Exception e) {
            log.debug("提取reasoning_content时出错: {}", e.getMessage());
        }
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
