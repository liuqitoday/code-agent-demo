package com.liuqitech.codeagent.util;

import com.liuqitech.codeagent.service.LlmService.ErrorType;

/**
 * 统一错误消息工具类
 * 将 ErrorType 映射为用户友好的中文错误信息
 */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    /**
     * 根据错误类型构建用户友好的错误信息
     *
     * @param type     错误类型
     * @param original 原始错误信息
     * @return 格式化的用户友好错误信息
     */
    public static String buildUserFriendlyMessage(ErrorType type, String original) {
        return switch (type) {
            case TIMEOUT -> "调用超时：LLM 服务响应时间过长，已重试多次仍然失败。\n" +
                    "   可能原因：\n" +
                    "   • 网络连接不稳定\n" +
                    "   • API 服务器负载过高\n" +
                    "   • 请求内容过于复杂\n" +
                    "   建议：\n" +
                    "   • 稍后再试\n" +
                    "   • 简化您的请求\n" +
                    "   • 检查网络连接";
            case AUTH -> "认证失败：API Key 无效或已过期。\n" +
                    "   请检查 application.yml 中的 api-key 配置。";
            case RATE_LIMIT -> "请求过于频繁：已超出 API 调用限制。\n" +
                    "   请稍等片刻后再试。";
            case SERVER_ERROR -> "服务器错误：LLM 服务暂时不可用。\n" +
                    "   这不是您的问题，请稍后再试。";
            case CONNECTION -> "连接失败：无法连接到 LLM 服务。\n" +
                    "   请检查网络连接和 API 地址配置。";
            case UNKNOWN -> "处理请求时发生错误：" + original;
        };
    }
}
