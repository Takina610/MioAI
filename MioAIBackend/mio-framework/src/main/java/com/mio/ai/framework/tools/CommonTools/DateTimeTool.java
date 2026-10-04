package com.mio.ai.framework.tools.CommonTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author: Takina
 * @date: 2026/10/4
 * @description: 时间工具：模型不掌握当前时间，涉及"今天/明天/下周"等表达前先查询
 */
@Component
public class DateTimeTool {

    private static final String[] WEEK_DAYS = {"一", "二", "三", "四", "五", "六", "日"};

    @Tool(description = "获取当前日期、时间与星期（北京时间）。回答涉及今天/明天/本周等时间信息前先调用")
    public String getCurrentDateTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        String formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return formatted + " 星期" + WEEK_DAYS[now.getDayOfWeek().getValue() - 1] + "（北京时间）";
    }
}
