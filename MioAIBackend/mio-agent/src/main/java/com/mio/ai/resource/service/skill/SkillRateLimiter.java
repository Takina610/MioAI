package com.mio.ai.resource.service.skill;

import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author: Takina
 * @date: 2026/10/11
 * @description: 技能模块共享限流器：按用户+场景做小时窗口计数。
 *               zip 安装/GitHub 导入/skills.sh 搜索都是高成本操作（网络拉取、解压、落库），
 *               不限流会被滥用打爆 CPU、外部接口与数据库。
 */
@Slf4j
@Component
public class SkillRateLimiter {

    private static final DateTimeFormatter RATE_WINDOW = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final StringRedisTemplate stringRedisTemplate;

    public SkillRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 校验并计数；超出 limit 抛业务异常，Redis 异常时放行并告警（不因限流组件故障阻断主流程）
     */
    public void check(String scene, Long userId, int limit) {
        String window = LocalDateTime.now().format(RATE_WINDOW);
        String key = "mio:skill-rate:" + scene + ":" + userId + ":" + window;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, Duration.ofHours(2));
            }
            if (count != null && count > limit) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "操作过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("技能限流检查异常，放行本次请求: scene={}, {}", scene, e.getMessage());
        }
    }
}
