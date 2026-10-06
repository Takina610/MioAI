package com.mio.ai.framework.zagent.tools;

import java.util.List;

/**
 * 工具结果（zcode ReadOutput → ModelMessageContent 的对位移植）：
 * 文本为主，可选携带媒体（Read 图片分支）。文本侧与 zcode 一致使用
 * [Attached &lt;mime&gt;: Read image] 占位——媒体本身由投影层拆出投递。
 */
public record ToolResult(String content, List<ToolMedia> media) {

    public ToolResult {
        media = media == null ? List.of() : List.copyOf(media);
    }

    public static ToolResult of(String content) {
        return new ToolResult(content, List.of());
    }

    public static ToolResult withMedia(String content, ToolMedia media) {
        return new ToolResult(content, List.of(media));
    }

    public boolean hasMedia() {
        return !media.isEmpty();
    }
}
