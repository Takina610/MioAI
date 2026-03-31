package com.mio.ai.superagent.tools.CommonTools;

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
            Terminate the interaction when the request is met OR if the assistant cannot proceed further with the task.
            "When you have finished all the tasks, call this tool to end the work.
            """)
    public String doTerminate() {
        return "任务结束";
    }
}

