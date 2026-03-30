package com.mio.ai.superagent.tools.CSAppTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/30 9:03
 * @description: 地图战术分析工具
 */

@Component
public class MapTacticTool {

    @Tool(
            name = "CS地图战术分析",
            description = "查询任意CS职业地图T/CT攻防打法、默认站位、常规控图思路、转点战术、长枪/ECO局专用策略"
    )
    public String analyzeMapTactic(String mapName, String side, String gamePhase) {
        return "根据地图、阵营、对局阶段生成专业攻防战术拆解与执行方案";
    }
}
