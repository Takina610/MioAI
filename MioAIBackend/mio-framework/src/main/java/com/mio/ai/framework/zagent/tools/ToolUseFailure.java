package com.mio.ai.framework.zagent.tools;

/**
 * 工具的业务性失败（zcode ToolHandlerFailure 的对位移植）。
 * <p>不是异常路径：消息会以 &lt;tool_use_error&gt; 包裹回给模型，
 * 循环继续（模型自纠），任务不中断。
 */
public final class ToolUseFailure extends RuntimeException {

    private final int errorCode;

    public ToolUseFailure(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int errorCode() {
        return errorCode;
    }

    /** zcode 错误信封 */
    public String toModelContent() {
        return "<tool_use_error>" + getMessage() + "</tool_use_error>";
    }
}
