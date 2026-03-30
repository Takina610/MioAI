package com.mio.ai.superagent.tools.CSAppTools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: Takina
 * @date: 2026/3/30 9:09
 * @description: 集中的工具注册类
 */

@Configuration
public class CSAppToolRegistration {
    @Bean
    public ToolCallback[] allTools(EventReviewTool eventReviewTool,
                                   GrenadeGuideTool grenadeGuideTool,
                                   MapTacticTool mapTacticTool,
                                   PlayerStatsTool playerStatsTool,
                                   TeamAnalyzeTool teamAnalyzeTool) {
        return ToolCallbacks.from(
                eventReviewTool,
                grenadeGuideTool,
                mapTacticTool,
                playerStatsTool,
                teamAnalyzeTool
        );
    }
}
