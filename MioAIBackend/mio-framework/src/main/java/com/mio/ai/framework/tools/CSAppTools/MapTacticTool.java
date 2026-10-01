package com.mio.ai.framework.tools.CSAppTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/31 15:05
 * @description: 地图战术分析工具
 */
@Component
public class MapTacticTool {

    @Tool(
            name = "map_tactic",
            description = "查询任意CS职业地图T/CT攻防打法、默认站位、常规控图思路、转点战术、长枪/ECO局专用策略"
    )
    public String analyzeMapTactic(String mapName, String side, String gamePhase) {
        return "根据地图、阵营、对局阶段生成专业攻防战术拆解与执行方案";
    }
}