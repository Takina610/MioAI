package com.mio.ai.framework.tools.CommonTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/31 18:47
 * @description: 终止工具（作用是让自主规划智能体能够合理地中断）
 */

@Component
public class TerminateTool {

    @Tool(description = """
            【严格限制】仅当确认所有用户要求的任务均已完全成功执行后，才可调用此工具终止交互。
            调用前必须自检：
            1. 用户要求的每一个子任务是否都已完成？
            2. 如果有生成文件（如 PDF），文件是否已成功生成并返回了有效链接？
            3. 如果某一步骤失败，是否已尝试重试或明确告知用户失败原因？
            只要还有未完成的子任务、失败的工具调用未处理、或生成的文件未确认成功，就绝对禁止调用此工具。
            """)
    public String doTerminate() {
        return "任务结束";
    }
}

