package com.liuqitech.codeagent.tool;

import com.liuqitech.codeagent.config.AgentProperties;
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
 */
@Component
public class CodeAgentTools {

    private static final Logger log = LoggerFactory.getLogger(CodeAgentTools.class);

    private final String workspaceRoot;
    private final Path workspaceRootPath;

    public CodeAgentTools(AgentProperties agentProperties) {
        this.workspaceRoot = agentProperties.getWorkspace();
        this.workspaceRootPath = Paths.get(workspaceRoot).normalize();
    }

    // ==================== 路径验证工具方法 ====================

    private Path resolvePath(String relativePath) {
        return Paths.get(workspaceRoot, relativePath).normalize();
    }

    private boolean isPathInsideWorkspace(Path path) {
        return path.normalize().startsWith(workspaceRootPath);
    }

    /**
     * 解析路径并验证安全性，不安全时返回错误信息
     *
     * @param relativePath 相对路径
     * @param operation    操作描述（用于错误信息）
     * @return 如果安全返回 null，否则返回错误信息
     */
    private String resolveAndValidate(Path fullPath, String operation) {
        if (!isPathInsideWorkspace(fullPath)) {
            log.warn("[Observation] 安全检查失败: 路径在工作空间外");
            return "错误: 只能" + operation + "工作空间内的路径";
        }
        return null;
    }

    /**
     * 读取文件内容并检查文件存在性
     *
     * @param fullPath 完整路径
     * @param filePath 相对路径（用于错误信息）
     * @return 文件内容，文件不存在时返回 null
     */
    private String readFileContent(Path fullPath, String filePath) throws IOException {
        if (!Files.exists(fullPath)) {
            return null;
        }
        return Files.readString(fullPath);
    }

    // ==================== 工具方法 ====================

    @Tool(description = "创建一个新文件并写入指定内容。如果文件已存在则会覆盖。路径是相对于工作空间的相对路径。")
    public String createFile(
            @ToolParam(description = "文件的相对路径，例如: src/main/java/com/example/Hello.java") String filePath,
            @ToolParam(description = "要写入文件的完整内容") String content
    ) {
        log.info("[Action] LLM 调用工具: createFile, filePath={}, content={} 字符",
                filePath, content != null ? content.length() : 0);

        try {
            Path fullPath = resolvePath(filePath);

            String securityError = resolveAndValidate(fullPath, "写入");
            if (securityError != null) {
                return securityError;
            }

            Files.createDirectories(fullPath.getParent());
            Files.writeString(fullPath, content != null ? content : "");

            log.info("[Observation] 文件创建成功: {}", fullPath.toAbsolutePath());
            return "成功创建文件: " + fullPath.toAbsolutePath();

        } catch (IOException e) {
            log.error("[Observation] 创建文件失败: {}", e.getMessage());
            return "创建文件失败: " + e.getMessage();
        }
    }

    @Tool(description = "读取指定文件的内容。路径是相对于工作空间的相对路径。")
    public String readFile(
            @ToolParam(description = "要读取的文件的相对路径") String filePath
    ) {
        log.info("[Action] LLM 调用工具: readFile, filePath={}", filePath);

        try {
            Path fullPath = resolvePath(filePath);

            String securityError = resolveAndValidate(fullPath, "读取");
            if (securityError != null) {
                return securityError;
            }

            String content = readFileContent(fullPath, filePath);
            if (content == null) {
                log.warn("[Observation] 文件不存在: {}", fullPath);
                return "错误: 文件不存在: " + filePath;
            }

            log.info("[Observation] 读取成功: {} ({} 字符)", fullPath, content.length());
            return content;

        } catch (IOException e) {
            log.error("[Observation] 读取文件失败: {}", e.getMessage());
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool(description = "列出指定目录下的所有文件和子目录。路径是相对于工作空间的相对路径，使用 '.' 表示工作空间根目录。")
    public String listDirectory(
            @ToolParam(description = "要列出的目录的相对路径，使用 '.' 表示根目录") String dirPath
    ) {
        log.info("[Action] LLM 调用工具: listDirectory, dirPath={}", dirPath);

        try {
            Path fullPath = resolvePath(dirPath);

            String securityError = resolveAndValidate(fullPath, "访问");
            if (securityError != null) {
                return securityError;
            }

            if (!Files.exists(fullPath)) {
                log.warn("[Observation] 目录不存在: {}", fullPath);
                return "错误: 目录不存在: " + dirPath;
            }

            if (!Files.isDirectory(fullPath)) {
                log.warn("[Observation] 路径不是目录: {}", fullPath);
                return "错误: 路径不是目录: " + dirPath;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("目录内容 [").append(dirPath).append("]:\n");

            try (var stream = Files.list(fullPath)) {
                stream.forEach(path -> {
                    String name = path.getFileName().toString();
                    if (Files.isDirectory(path)) {
                        sb.append("  [DIR] ").append(name).append("/\n");
                    } else {
                        sb.append("  [FILE] ").append(name).append("\n");
                    }
                });
            }

            log.info("[Observation] 列出目录成功: {}", fullPath);
            return sb.toString();

        } catch (IOException e) {
            log.error("[Observation] 列出目录失败: {}", e.getMessage());
            return "列出目录失败: " + e.getMessage();
        }
    }

    @Tool(description = "创建一个新目录。如果父目录不存在会自动创建。路径是相对于工作空间的相对路径。")
    public String createDirectory(
            @ToolParam(description = "要创建的目录的相对路径") String dirPath
    ) {
        log.info("[Action] LLM 调用工具: createDirectory, dirPath={}", dirPath);

        try {
            Path fullPath = resolvePath(dirPath);

            String securityError = resolveAndValidate(fullPath, "创建");
            if (securityError != null) {
                return securityError;
            }

            Files.createDirectories(fullPath);
            log.info("[Observation] 目录创建成功: {}", fullPath.toAbsolutePath());
            return "成功创建目录: " + fullPath.toAbsolutePath();

        } catch (IOException e) {
            log.error("[Observation] 创建目录失败: {}", e.getMessage());
            return "创建目录失败: " + e.getMessage();
        }
    }

    @Tool(description = "编辑已存在的文件，通过搜索替换的方式修改内容。" +
            "重要: oldContent 必须精确匹配文件中的内容（包括空格、换行、缩进），且必须在文件中唯一出现。" +
            "如果 oldContent 在文件中出现多次，编辑将被拒绝，需要包含更多上下文代码使其唯一。" +
            "如果要删除某段内容，将 newContent 设为空字符串。" +
            "如果需要批量替换所有匹配项，请使用 editFileAll 工具。")
    public String editFile(
            @ToolParam(description = "要编辑的文件的相对路径") String filePath,
            @ToolParam(description = "要被替换的原始内容，必须精确匹配且在文件中唯一") String oldContent,
            @ToolParam(description = "替换后的新内容，如果要删除则传空字符串") String newContent
    ) {
        log.info("[Action] LLM 调用工具: editFile, filePath={}, oldContent={} 字符, newContent={} 字符",
                filePath, oldContent != null ? oldContent.length() : 0, newContent != null ? newContent.length() : 0);

        if (oldContent == null || oldContent.isEmpty()) {
            log.warn("[Observation] 参数错误: oldContent 不能为空");
            return "错误: oldContent 不能为空，必须指定要替换的内容";
        }

        try {
            Path fullPath = resolvePath(filePath);

            String securityError = resolveAndValidate(fullPath, "编辑");
            if (securityError != null) {
                return securityError;
            }

            String currentContent = readFileContent(fullPath, filePath);
            if (currentContent == null) {
                log.warn("[Observation] 文件不存在: {}", fullPath);
                return "错误: 文件不存在: " + filePath + "。请先使用 createFile 创建文件，或检查路径是否正确。";
            }

            if (!currentContent.contains(oldContent)) {
                log.warn("[Observation] 未找到要替换的内容");
                return "错误: 未在文件中找到要替换的内容。可能原因:\n" +
                        "  1. 内容不完全匹配（注意空格、换行、缩进）\n" +
                        "  2. 文件已被修改，内容不是最新的\n" +
                        "  建议: 先使用 readFile 读取文件最新内容，确保 oldContent 精确匹配。";
            }

            int matchCount = countOccurrences(currentContent, oldContent);
            if (matchCount > 1) {
                log.warn("[Observation] oldContent 不唯一，发现 {} 处匹配，拒绝执行", matchCount);
                return "错误: oldContent 在文件中出现了 " + matchCount + " 次，不是唯一的。\n" +
                        "  为避免替换错误的位置，请在 oldContent 中包含更多上下文代码（如前后几行），使其在文件中唯一。\n" +
                        "  如果确实需要替换所有匹配项，请使用 editFileAll 工具。";
            }

            String updatedContent = currentContent.replace(oldContent, newContent != null ? newContent : "");
            Files.writeString(fullPath, updatedContent);

            log.info("[Observation] 文件编辑成功: {}, 替换: {} 字符 → {} 字符",
                    fullPath.toAbsolutePath(), oldContent.length(), newContent != null ? newContent.length() : 0);
            return "成功编辑文件: " + fullPath.toAbsolutePath() +
                    "\n  替换: " + oldContent.length() + " 字符 → " + (newContent != null ? newContent.length() : 0) + " 字符";

        } catch (IOException e) {
            log.error("[Observation] 编辑文件失败: {}", e.getMessage());
            return "编辑文件失败: " + e.getMessage();
        }
    }

    @Tool(description = "编辑文件并替换所有匹配的内容。与 editFile 类似，但会替换文件中所有匹配的内容而不是只替换第一处。" +
            "适用于需要批量替换的场景，如重命名变量、更新导入语句等。")
    public String editFileAll(
            @ToolParam(description = "要编辑的文件的相对路径") String filePath,
            @ToolParam(description = "要被替换的原始内容，必须精确匹配") String oldContent,
            @ToolParam(description = "替换后的新内容") String newContent
    ) {
        log.info("[Action] LLM 调用工具: editFileAll, filePath={}, oldContent={} 字符, newContent={} 字符",
                filePath, oldContent != null ? oldContent.length() : 0, newContent != null ? newContent.length() : 0);

        if (oldContent == null || oldContent.isEmpty()) {
            log.warn("[Observation] 参数错误: oldContent 不能为空");
            return "错误: oldContent 不能为空，必须指定要替换的内容";
        }

        try {
            Path fullPath = resolvePath(filePath);

            String securityError = resolveAndValidate(fullPath, "编辑");
            if (securityError != null) {
                return securityError;
            }

            String currentContent = readFileContent(fullPath, filePath);
            if (currentContent == null) {
                log.warn("[Observation] 文件不存在: {}", fullPath);
                return "错误: 文件不存在: " + filePath;
            }

            if (!currentContent.contains(oldContent)) {
                log.warn("[Observation] 未找到要替换的内容");
                return "错误: 未在文件中找到要替换的内容。请使用 readFile 确认文件内容。";
            }

            int matchCount = countOccurrences(currentContent, oldContent);
            String updatedContent = currentContent.replace(oldContent, newContent != null ? newContent : "");
            Files.writeString(fullPath, updatedContent);

            log.info("[Observation] 文件编辑成功: {}, 替换了 {} 处匹配", fullPath.toAbsolutePath(), matchCount);
            return "成功编辑文件: " + fullPath.toAbsolutePath() +
                    "\n  共替换 " + matchCount + " 处匹配";

        } catch (IOException e) {
            log.error("[Observation] 编辑文件失败: {}", e.getMessage());
            return "编辑文件失败: " + e.getMessage();
        }
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
