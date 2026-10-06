package com.mio.ai.framework.zagent.tools;

/**
 * 工具结果携带的媒体负载（zcode ReadImageOutput 的对位移植）：
 * base64 图像 + 处理元数据，随工具结果进入会话历史，投影时转为
 * 后置 user 消息的 Media part（zcode tool-result-media-projection）。
 */
public record ToolMedia(
        String mimeType,
        String base64,
        long originalSize,
        long transformedSize,
        boolean resized,
        boolean compressed,
        int originalWidth,
        int originalHeight,
        int width,
        int height) {
}
