package com.liuqitech.codeagent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Agent 工具集合
 * 包含代码生成和文件操作相关的工具
 * 这些工具通过 Spring AI 的 @Tool 注解暴露给 LLM 调用
 *
 * 当 LLM 决定调用工具时，Spring AI 会自动执行对应方法
 */
@Component
public class CodeAgentTools {

    private static final Logger log = LoggerFactory.getLogger(CodeAgentTools.class);

    /**
     * 工作空间根目录
     */
    private String workspaceRoot = "./workspace";

    /**
     * 缓存的工作空间根路径（normalized）
     */
    private Path workspaceRootPath;

    public CodeAgentTools() {
        this.workspaceRootPath = Paths.get(workspaceRoot).normalize();
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
        this.workspaceRootPath = Paths.get(workspaceRoot).normalize();
    }

    // ==================== 路径验证工具方法 ====================

    /**
     * 解析相对路径为完整路径并进行规范化
     *
     * @param relativePath 相对于工作空间的路径
     * @return 规范化后的完整路径
     */
    private Path resolvePath(String relativePath) {
        return Paths.get(workspaceRoot, relativePath).normalize();
    }

    /**
     * 检查路径是否在工作空间内（防止目录遍历攻击）
     *
     * @param path 要检查的路径
     * @return 如果路径在工作空间内返回 true
     */
    private boolean isPathInsideWorkspace(Path path) {
        return path.normalize().startsWith(workspaceRootPath);
    }

    /**
     * 验证路径安全性，如果不安全则返回错误信息
     *
     * @param path 要验证的路径
     * @param operation 操作描述（用于错误信息）
     * @return 如果安全返回 null，否则返回错误信息
     */
    private String validatePathSecurity(Path path, String operation) {
        if (!isPathInsideWorkspace(path)) {
            log.warn("[Observation] 安全检查失败: 路径在工作空间外");
            String error = "错误: 只能" + operation + "工作空间内的路径";
            System.out.println("   ❌ " + error);
            return error;
        }
        return null;
    }

    // ==================== 工具方法 ====================

    /**
     * 创建文件并写入内容
     *
     * @param filePath 文件的相对路径（相对于工作空间）
     * @param content  要写入的文件内容
     * @return 操作结果信息
     */
    @Tool(description = "创建一个新文件并写入指定内容。如果文件已存在则会覆盖。路径是相对于工作空间的相对路径。")
    public String createFile(
            @ToolParam(description = "文件的相对路径，例如: src/main/java/com/example/Hello.java") String filePath,
            @ToolParam(description = "要写入文件的完整内容") String content
    ) {
        System.out.println("\n🔧 [Tool] createFile → " + filePath + " (" + (content != null ? content.length() : 0) + " 字符)");

        log.info("");
        log.info("========================================");
        log.info("[Action] LLM 调用工具: createFile");
        log.info("========================================");
        log.info("参数:");
        log.info("  filePath: {}", filePath);
        log.info("  content: {} 字符", content != null ? content.length() : 0);

        try {
            Path fullPath = resolvePath(filePath);

            String securityError = validatePathSecurity(fullPath, "写入");
            if (securityError != null) {
                return securityError;
            }

            // 创建父目录
            Files.createDirectories(fullPath.getParent());
            // 写入文件
            Files.writeString(fullPath, content != null ? content : "");

            log.info("[Observation] 文件创建成功: {}", fullPath.toAbsolutePath());
            log.info("========================================");

            String result = "成功创建文件: " + fullPath.toAbsolutePath();
            System.out.println("   ✅ 已创建: " + fullPath.toAbsolutePath());
            return result;

        } catch (IOException e) {
            log.error("[Observation] 创建文件失败: {}", e.getMessage());
            String result = "创建文件失败: " + e.getMessage();
            System.out.println("   ❌ " + result);
            return result;
        }
    }

    /**
     * 读取文件内容
     *
     * @param filePath 文件的相对路径（相对于工作空间）
     * @return 文件内容或错误信息
     */
    @Tool(description = "读取指定文件的内容。路径是相对于工作空间的相对路径。")
    public String readFile(
            @ToolParam(description = "要读取的文件的相对路径") String filePath
    ) {
        System.out.println("\n🔧 [Tool] readFile → " + filePath);

        log.info("");
        log.info("========================================");
        log.info("[Action] LLM 调用工具: readFile");
        log.info("========================================");
        log.info("参数: filePath = {}", filePath);

        try {
            Path fullPath = resolvePath(filePath);

            String securityError = validatePathSecurity(fullPath, "读取");
            if (securityError != null) {
                return securityError;
            }

            if (!Files.exists(fullPath)) {
                log.warn("[Observation] 文件不存在: {}", fullPath);
                String result = "错误: 文件不存在: " + filePath;
                System.out.println("   ❌ " + result);
                return result;
            }

            String content = Files.readString(fullPath);
            log.info("[Observation] 读取成功: {} ({} 字符)", fullPath, content.length());
            log.info("========================================");
            System.out.println("   ✅ 已读取 (" + content.length() + " 字符)");
            return content;

        } catch (IOException e) {
            log.error("[Observation] 读取文件失败: {}", e.getMessage());
            String result = "读取文件失败: " + e.getMessage();
            System.out.println("   ❌ " + result);
            return result;
        }
    }

    /**
     * 列出目录内容
     *
     * @param dirPath 目录的相对路径（相对于工作空间）
     * @return 目录内容列表或错误信息
     */
    @Tool(description = "列出指定目录下的所有文件和子目录。路径是相对于工作空间的相对路径，使用 '.' 表示工作空间根目录。")
    public String listDirectory(
            @ToolParam(description = "要列出的目录的相对路径，使用 '.' 表示根目录") String dirPath
    ) {
        System.out.println("\n🔧 [Tool] listDirectory → " + dirPath);

        log.info("");
        log.info("========================================");
        log.info("[Action] LLM 调用工具: listDirectory");
        log.info("========================================");
        log.info("参数: dirPath = {}", dirPath);

        try {
            Path fullPath = resolvePath(dirPath);

            String securityError = validatePathSecurity(fullPath, "访问");
            if (securityError != null) {
                return securityError;
            }

            if (!Files.exists(fullPath)) {
                log.warn("[Observation] 目录不存在: {}", fullPath);
                String result = "错误: 目录不存在: " + dirPath;
                System.out.println("   ❌ " + result);
                return result;
            }

            if (!Files.isDirectory(fullPath)) {
                log.warn("[Observation] 路径不是目录: {}", fullPath);
                String result = "错误: 路径不是目录: " + dirPath;
                System.out.println("   ❌ " + result);
                return result;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("目录内容 [").append(dirPath).append("]:\n");

            final int[] count = {0};
            try (var stream = Files.list(fullPath)) {
                stream.forEach(path -> {
                    String name = path.getFileName().toString();
                    if (Files.isDirectory(path)) {
                        sb.append("  [DIR] ").append(name).append("/\n");
                    } else {
                        sb.append("  [FILE] ").append(name).append("\n");
                    }
                    count[0]++;
                });
            }

            log.info("[Observation] 列出目录成功: {}", fullPath);
            log.info("========================================");
            System.out.println("   ✅ 已列出 (" + count[0] + " 项)");
            return sb.toString();

        } catch (IOException e) {
            log.error("[Observation] 列出目录失败: {}", e.getMessage());
            String result = "列出目录失败: " + e.getMessage();
            System.out.println("   ❌ " + result);
            return result;
        }
    }

    /**
     * 创建目录
     *
     * @param dirPath 目录的相对路径（相对于工作空间）
     * @return 操作结果信息
     */
    @Tool(description = "创建一个新目录。如果父目录不存在会自动创建。路径是相对于工作空间的相对路径。")
    public String createDirectory(
            @ToolParam(description = "要创建的目录的相对路径") String dirPath
    ) {
        System.out.println("\n🔧 [Tool] createDirectory → " + dirPath);

        log.info("");
        log.info("========================================");
        log.info("[Action] LLM 调用工具: createDirectory");
        log.info("========================================");
        log.info("参数: dirPath = {}", dirPath);

        try {
            Path fullPath = resolvePath(dirPath);

            String securityError = validatePathSecurity(fullPath, "创建");
            if (securityError != null) {
                return securityError;
            }

            Files.createDirectories(fullPath);
            log.info("[Observation] 目录创建成功: {}", fullPath.toAbsolutePath());
            log.info("========================================");

            String result = "成功创建目录: " + fullPath.toAbsolutePath();
            System.out.println("   ✅ 已创建: " + fullPath.toAbsolutePath());
            return result;

        } catch (IOException e) {
            log.error("[Observation] 创建目录失败: {}", e.getMessage());
            String result = "创建目录失败: " + e.getMessage();
            System.out.println("   ❌ " + result);
            return result;
        }
    }
}
