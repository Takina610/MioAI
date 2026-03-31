package com.mio.ai.superagent.tools.CSAppTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/31 15:06
 * @description: 道具指导工具
 */
@Component
public class GrenadeGuideTool {

    @Tool(
            name = "grenade_guide",
            description = "提供各地图关键烟雾、闪光、燃烧弹、手雷的职业点位投掷方法、同步道具套餐、进攻防守道具执行流程"
    )
    public String getGrenadeGuide(String mapName, String positionType) {
        return "返回对应地图关键位置全套职业道具丢法与团队同步执行细节";
    }
}
