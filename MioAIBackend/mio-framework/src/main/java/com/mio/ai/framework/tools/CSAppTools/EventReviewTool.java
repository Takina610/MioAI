package com.mio.ai.framework.tools.CSAppTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/31 15:08
 * @description: CS赛事复盘与规则解读
 */
@Component
public class EventReviewTool {

    @Tool(
            name = "cs_event_review",
            description = "解读Major/RMR/IEM/BLAST赛事赛制规则、积分体系、晋级逻辑；支持对局复盘、阵容克制、胜负原因专业分析"
    )
    public String reviewMatchAndEvent(String eventOrMatchInfo) {
        return "完成赛事规则讲解或单场对局深度复盘分析";
    }
}
