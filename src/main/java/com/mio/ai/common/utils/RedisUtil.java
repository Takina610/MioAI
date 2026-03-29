package com.mio.ai.common.utils;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author: Takina
 * @date: 2026/3/28 16:15
 * @description:
 */

@Component("redisUtil")
public class RedisUtil {
    private static final Logger log = LoggerFactory.getLogger(RedisUtil.class);

    /**
     * RedisTemplate :  先将被存储的数据转换成 字节数组（不可读），再存储到 redis中，读取的时候按照字节数组读取
     * StringRedisTemplate ： 直接存放的就是 string (可读)
     * 项目背景：String, String
     */
    @Resource
    StringRedisTemplate stringRedisTemplate;

    // * --------------- String ----------------------

    /**
     * 设置键值
     * @param key 键
     * @param val 值
     * @return 是否设置成功
     */
    public boolean set(String key, String val) {
        // Step 校验 key
        if (!StringUtils.hasText(key)) return false;
        try {
            stringRedisTemplate.opsForValue()
                    .set(key, val);
            return true;
        } catch (Exception e) {
            log.error("RedisUtil error, set({}, {})", key, val, e);
            return false;
        }
    }

    /**
     * 设置键值
     * @param key 键
     * @param val 值
     * @param time 过期时间
     * @return 是否设置成功
     */
    public boolean set(String key, String val, Long time) {
        // Step 校验 key
        if (!StringUtils.hasText(key)) return false;
        try {
            stringRedisTemplate.opsForValue()
                    .set(key, val, time, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("RedisUtil error, set({}, {}, {})", key, val, time, e);
            return false;
        }
    }

    /**
     * 获取键对应的值
     * @param key 键
     * @return 返回对应的值
     */
    public String get(String key) {
        try {
            return StringUtils.hasText(key)
                    ? stringRedisTemplate.opsForValue().get(key)
                    : null;
        } catch (Exception e) {
            log.error("RedisUtil error, get({})", key, e);
            return null;
        }
    }

    /**
     * 删除键
     * @param key 键数组
     * @return 成功删除的个数
     */
    public Long delete(String... key) {
        try {
            if (key != null && key.length > 0) {
                if (key.length == 1) {
                    stringRedisTemplate.delete(key[0]);
                    return 1L;
                }
                return stringRedisTemplate.delete(
                        (Collection<String>) CollectionUtils.arrayToList(key)
                );
            }
            return 0L;
        } catch (Exception e) {
            log.error("RedisUtil error, delete({})", key, e);
            return 0L;
        }
    }

    /**
     * 判断键是否存在
     * @param key 键
     * @return 是否存在
     */
    public Boolean exists(String key) {
        try {
            return StringUtils.hasText(key)
                    ? stringRedisTemplate.hasKey(key)
                    : Boolean.FALSE;
        } catch (Exception e) {
            log.error("RedisUtil error, exists({})", key, e);
            return Boolean.FALSE;
        }
    }

    // * --------------- List ----------------------

    /**
     * 设置一批键值到List
     * @param key 键
     * @param val 值
     * @return 是否设置成功
     */
    public boolean lPushAll(String key, List<String> val) {
        // Step 校验 key
        if (!StringUtils.hasText(key)) return false;
        try {
            stringRedisTemplate.opsForList()
                    .leftPushAll(key, val);
            return true;
        } catch (Exception e) {
            log.error("RedisUtil error, lPushAll({}, {})", key, val, e);
            return false;
        }
    }

    /**
     * 设置一批键值到List，含 TTL
     * @param key 键
     * @param val 值
     * @param time TTL
     * @return 是否设置成功
     */
    public boolean lPushAll(String key, List<String> val, Long time) {
        // Step 校验 key
        if (!StringUtils.hasText(key)) return false;
        try {
            stringRedisTemplate.opsForList()
                    .leftPushAll(key, val);
            stringRedisTemplate.expire(key, time, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("RedisUtil error, lPushAll({}, {}, {})", key, val, time, e);
            return false;
        }
    }

    public List<String> getList(String key) {
        try {
            return StringUtils.hasText(key)
                    ? stringRedisTemplate.opsForList().range(key, 0, -1)
                    : null;
        } catch (Exception e) {
            log.error("RedisUtil error, getList({})", key, e);
            return null;
        }
    }

    public boolean deleteEle(String key, String element) {
        // Step 校验 key
        if (!StringUtils.hasText(key)) return false;
        try {
            stringRedisTemplate.opsForList().remove(key, 0, element);
            return true;
        } catch (Exception e) {
            log.error("RedisUtil error, deleteEle({})", key, e);
            return false;
        }
    }
}

