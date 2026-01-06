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
    
    public CodeAgentTools() {
    }
    
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }
    
    /**
     * 创建文件并写入内容
     *
     * @param filePath 文件的相对路径（相对于工作空间）
     * @param content 要写入的文件内容
     * @return 操作结果信息
     */
    @Tool(description = "创建一个新文件并写入指定内容。如果文件已存在则会覆盖。路径是相对于工作空间的相对路径。")
    public String createFile(
            @ToolParam(description = "文件的相对路径，例如: src/main/java/com/example/Hello.java") String filePath,
            @ToolParam(description = "要写入文件的完整内容") String content
    ) {
        // 控制台输出 - 简洁版
        System.out.println("\n🔧 [Tool] createFile → " + filePath + " (" + (content != null ? content.length() : 0) + " 字符)");

        // 详细日志 - 记录到文件
        log.info("");
        log.info("========================================");
        log.info("[Action] LLM 调用工具: createFile");
        log.info("========================================");
        log.info("参数:");
        log.info("  filePath: {}", filePath);
        log.info("  content: {} 字符", content != null ? content.length() : 0);

        String result;
        try {
            Path fullPath = Paths.get(workspaceRoot, filePath).normalize();

            // 确保不会写到工作空间外
            if (!fullPath.startsWith(Paths.get(workspaceRoot).normalize())) {
                log.warn("[Observation] 安全检查失败: 路径在工作空间外");
                result = "错误: 文件路径必须在工作空间内";
                System.out.println("   ❌ " + result);
            } else {
                // 创建父目录
                Files.createDirectories(fullPath.getParent());

                // 写入文件
                Files.writeString(fullPath, content);

                log.info("[Observation] 文件创建成功: {}", fullPath.toAbsolutePath());
                log.info("========================================");

                result = "成功创建文件: " + fullPath.toAbsolutePath();
                System.out.println("   ✅ 已创建: " + fullPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("[Observation] 创建文件失败: {}", e.getMessage());
            result = "创建文件失败: " + e.getMessage();
            System.out.println("   ❌ " + result);
        }

        return result;
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
        // 控制台输出 - 简洁版
        System.out.println("\n🔧 [Tool] readFile → " + filePath);

        // 详细日志 - 记录到文件
        log.info("");
        log.info("========================================");
        log.info("[Action] LLM 调用工具: readFile");
        log.info("========================================");
        log.info("参数: filePath = {}", filePath);

        String result;
        try {
            Path fullPath = Paths.get(workspaceRoot, filePath).normalize();

            // 确保不会读取工作空间外的文件
            if (!fullPath.startsWith(Paths.get(workspaceRoot).normalize())) {
                log.warn("[Observation] 安全检查失败: 路径在工作空间外");
                result = "错误: 只能读取工作空间内的文件";
                System.out.println("   ❌ " + result);
            } else if (!Files.exists(fullPath)) {
                log.warn("[Observation] 文件不存在: {}", fullPath);
                result = "错误: 文件不存在: " + filePath;
                System.out.println("   ❌ " + result);
            } else {
                String content = Files.readString(fullPath);
                log.info("[Observation] 读取成功: {} ({} 字符)", fullPath, content.length());
                log.info("========================================");
                result = content;
                System.out.println("   ✅ 已读取 (" + content.length() + " 字符)");
            }
        } catch (IOException e) {
            log.error("[Observation] 读取文件失败: {}", e.getMessage());
            result = "读取文件失败: " + e.getMessage();
            System.out.println("   ❌ " + result);
        }

        return result;
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
        // 控制台输出 - 简洁版
        System.out.println("\n🔧 [Tool] listDirectory → " + dirPath);

        // 详细日志 - 记录到文件
        log.info("");
        log.info("========================================");
        log.info("[Action] LLM 调用工具: listDirectory");
        log.info("========================================");
        log.info("参数: dirPath = {}", dirPath);

        String result;
        try {
            Path fullPath = Paths.get(workspaceRoot, dirPath).normalize();

            // 确保不会访问工作空间外
            if (!fullPath.startsWith(Paths.get(workspaceRoot).normalize())) {
                log.warn("[Observation] 安全检查失败: 路径在工作空间外");
                result = "错误: 只能访问工作空间内的目录";
                System.out.println("   ❌ " + result);
            } else if (!Files.exists(fullPath)) {
                log.warn("[Observation] 目录不存在: {}", fullPath);
                result = "错误: 目录不存在: " + dirPath;
                System.out.println("   ❌ " + result);
            } else if (!Files.isDirectory(fullPath)) {
                log.warn("[Observation] 路径不是目录: {}", fullPath);
                result = "错误: 路径不是目录: " + dirPath;
                System.out.println("   ❌ " + result);
            } else {
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
                result = sb.toString();
                System.out.println("   ✅ 已列出 (" + count[0] + " 项)");
            }
        } catch (IOException e) {
            log.error("[Observation] 列出目录失败: {}", e.getMessage());
            result = "列出目录失败: " + e.getMessage();
            System.out.println("   ❌ " + result);
        }

        return result;
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
        // 控制台输出 - 简洁版
        System.out.println("\n🔧 [Tool] createDirectory → " + dirPath);

        // 详细日志 - 记录到文件
        log.info("");
        log.info("========================================");
        log.info("[Action] LLM 调用工具: createDirectory");
        log.info("========================================");
        log.info("参数: dirPath = {}", dirPath);

        String result;
        try {
            Path fullPath = Paths.get(workspaceRoot, dirPath).normalize();

            // 确保不会在工作空间外创建目录
            if (!fullPath.startsWith(Paths.get(workspaceRoot).normalize())) {
                log.warn("[Observation] 安全检查失败: 路径在工作空间外");
                result = "错误: 只能在工作空间内创建目录";
                System.out.println("   ❌ " + result);
            } else {
                Files.createDirectories(fullPath);
                log.info("[Observation] 目录创建成功: {}", fullPath.toAbsolutePath());
                log.info("========================================");
                result = "成功创建目录: " + fullPath.toAbsolutePath();
                System.out.println("   ✅ 已创建: " + fullPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("[Observation] 创建目录失败: {}", e.getMessage());
            result = "创建目录失败: " + e.getMessage();
            System.out.println("   ❌ " + result);
        }

        return result;
    }
}
