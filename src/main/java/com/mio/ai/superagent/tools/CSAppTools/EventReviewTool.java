package com.mio.ai.superagent.tools.CSAppTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/30 9:05
 * @description: 赛事规则 & 复盘分析
 */

@Component
public class EventReviewTool {

    @Tool(
            name = "CS赛事复盘与规则解读",
            description = "解读Major/RMR/IEM/BLAST赛事赛制规则、积分体系、晋级逻辑；支持对局复盘、阵容克制、胜负原因专业分析"
    )
    public String reviewMatchAndEvent(String eventOrMatchInfo) {
        return "完成赛事规则讲解或单场对局深度复盘分析";
    }
}
