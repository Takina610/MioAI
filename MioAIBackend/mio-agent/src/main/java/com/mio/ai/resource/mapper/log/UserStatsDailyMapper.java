package com.mio.ai.resource.mapper.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.resource.model.entity.UserStatsDaily;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 用户每日用量累计 Mapper：写入侧只有原子 upsert（唯一键 user_id+stat_date 上累加）。
 */
@Mapper
public interface UserStatsDailyMapper extends BaseMapper<UserStatsDaily> {

    /** 当日累计原子累加：并发轮次/成员回调同时写不丢数 */
    @Insert("INSERT INTO user_stats_daily "
            + "(user_id, stat_date, input_tokens, output_tokens, call_count, chat_duration_ms) "
            + "VALUES (#{userId}, #{statDate}, #{inputTokens}, #{outputTokens}, #{callCount}, #{chatDurationMs}) "
            + "ON DUPLICATE KEY UPDATE "
            + "input_tokens = input_tokens + VALUES(input_tokens), "
            + "output_tokens = output_tokens + VALUES(output_tokens), "
            + "call_count = call_count + VALUES(call_count), "
            + "chat_duration_ms = chat_duration_ms + VALUES(chat_duration_ms)")
    void accumulate(@Param("userId") Long userId,
                    @Param("statDate") Date statDate,
                    @Param("inputTokens") long inputTokens,
                    @Param("outputTokens") long outputTokens,
                    @Param("callCount") int callCount,
                    @Param("chatDurationMs") long chatDurationMs);
}
