package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户每日用量累计实体（user_stats_daily）：贡献图/连续天数/单日峰值/聊天时长的数据来源。
 * 以 (user_id, stat_date) 唯一，轮次结束与群成员用量落库时增量累加。
 */
@Data
@TableName("user_stats_daily")
public class UserStatsDaily implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 统计日期
     */
    private Date statDate;

    /**
     * 输入Token累计
     */
    private Long inputTokens;

    /**
     * 输出Token累计
     */
    private Long outputTokens;

    /**
     * 对话轮次累计
     */
    private Integer callCount;

    /**
     * 聊天耗时累计（毫秒）
     */
    private Long chatDurationMs;
}
