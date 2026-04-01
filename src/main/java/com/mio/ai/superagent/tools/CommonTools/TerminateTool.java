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
            当请求已完成或助手无法继续执行任务时，终止交互。
            当你完成所有任务后，调用此工具结束工作。
            """)
    public String doTerminate() {
        return "任务结束";
    }
}

