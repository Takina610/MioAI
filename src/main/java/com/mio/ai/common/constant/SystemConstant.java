package com.mio.ai.common.constant;

/**
 * @author: Takina
 * @date: 2026/3/28 16:20
 * @description:
 */
public interface SystemConstant {
    /**
     * 盐值
     */
    public static final String SALT = "MioAI";

    /**
     * 用户 token 前缀
     */
    public static final String REDIS_KEY_TOKEN = "MioAI:Token:";

    /**
     * 用户 id 前缀
     */
    public static final String REDIS_KEY_TOKEN_USERID= "MioAI:Token:UserId:";

    /**
     * 存活 1min
     */
    public static final Long REDIS_KEY_TIME_1MIN = 60L;

    /**
     * 存货 1d
     */
    public static final Long REDIS_KEY_EXPIRES_DAY = REDIS_KEY_TIME_1MIN * 60 * 24;
}
