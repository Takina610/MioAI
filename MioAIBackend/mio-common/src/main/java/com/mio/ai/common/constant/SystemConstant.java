package com.mio.ai.common.constant;

import org.springframework.beans.factory.annotation.Value;

/**
 * @author: Takina
 * @date: 2026/3/28 16:20
 * @description:
 */
public class SystemConstant {
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
     * 存活 1d
     */
    public static final Long REDIS_KEY_EXPIRES_DAY = REDIS_KEY_TIME_1MIN * 60 * 24;

    /**
     * 文件保存目录（Agent 工作区）：跟随后端工作目录，避免换机器时旧绝对路径失效
     */
    public static String FILE_SAVE_DIR =
            System.getProperty("user.dir") + java.io.File.separator + "data" + java.io.File.separator + "agent-files";
}
