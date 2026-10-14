package com.mio.ai.resource.service.log.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.resource.mapper.log.UserStatsDailyMapper;
import com.mio.ai.resource.model.entity.UserStatsDaily;
import com.mio.ai.resource.service.log.UserStatsDailyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 用户每日用量累计服务实现类
 */
@Slf4j
@Service
public class UserStatsDailyServiceImpl extends ServiceImpl<UserStatsDailyMapper, UserStatsDaily>
        implements UserStatsDailyService {

    @Override
    public void accumulate(Long userId, long inputTokens, long outputTokens, int calls, long durationMs) {
        if (userId == null || (inputTokens <= 0 && outputTokens <= 0 && calls <= 0 && durationMs <= 0)) {
            return;
        }
        try {
            Date today = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
            baseMapper.accumulate(userId, today, Math.max(0, inputTokens), Math.max(0, outputTokens),
                    Math.max(0, calls), Math.max(0, durationMs));
        } catch (Exception e) {
            // 统计落库失败不影响对话主流程
            log.error("用户每日用量累计失败 userId={}", userId, e);
        }
    }

    @Override
    public List<UserStatsDaily> listByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<UserStatsDaily>()
                .eq(UserStatsDaily::getUserId, userId)
                .orderByAsc(UserStatsDaily::getStatDate));
    }
}
