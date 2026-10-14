package com.mio.ai.bot.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 对话入口限流（SkillRateLimiter 同款模式）：
 * 分钟窗口计数防脚本刷聊（游客无登录态，成本敞口最大）；
 * Redis 异常时放行并告警——限流组件故障不阻断对话主流程。
 */
@Slf4j
@Component
public class ChatRateLimiter {

    private static final DateTimeFormatter MINUTE_WINDOW = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final StringRedisTemplate stringRedisTemplate;

    public ChatRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 校验并计数一分钟窗口；超出 limit 抛业务异常（调用方转为 SSE 友好提示）
     *
     * @param ownerKey 用户标识（"u:&lt;id&gt;"）或游客标识（"guest:&lt;ip&gt;"）
     */
    public void checkTurn(String ownerKey, int limitPerMinute) {
        String window = LocalDateTime.now().format(MINUTE_WINDOW);
        String key = "mio:chat-rate:turn:" + ownerKey + ":" + window;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, Duration.ofMinutes(2));
            }
            if (count != null && count > limitPerMinute) {
                throw new com.mio.ai.common.exception.BusinessException(
                        com.mio.ai.common.exception.ErrorCode.PARAMS_ERROR, "发送太频繁，请稍后再试");
            }
        } catch (com.mio.ai.common.exception.BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("对话限流检查异常，放行本次请求: {}, {}", ownerKey, e.getMessage());
        }
    }

    /**
     * 游客累计轮数配额：同一游客标识生命周期内最多 limitTotal 轮，超出必须登录。
     * 允许时才消耗配额（计数 +1）；Redis 异常放行不阻断主流程
     *
     * @return true = 本次轮次允许
     */
    public boolean tryGuestTurn(String ownerKey, int limitTotal) {
        String key = "mio:chat-guest-turns:" + ownerKey;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, Duration.ofDays(30));
            }
            return count == null || count <= limitTotal;
        } catch (Exception e) {
            log.warn("游客配额检查异常，放行本次请求: {}, {}", ownerKey, e.getMessage());
            return true;
        }
    }
}
