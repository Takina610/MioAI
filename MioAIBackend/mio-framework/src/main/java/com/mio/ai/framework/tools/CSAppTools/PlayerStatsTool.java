package com.mio.ai.framework.tools.CSAppTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/31 15:07
 * @description: 选手数据分析
 */
@Component
public class PlayerStatsTool {

    @Tool(
            name = "player_stats",
            description = "查询HLTV Top20职业选手全年数据：Rating2.0、ADR、K/D、爆头率、残局胜率、擅长枪械与打法风格分析"
    )
    public String queryPlayerData(String playerName) {
        return "返回选手完整职业数据面板与赛场风格深度分析";
    }
}
