package com.mio.ai.common.utils;

import com.mio.ai.common.constant.SystemConstant;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.user.model.vo.LoginUserVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/30 9:39
 * @description:
 */

@Component
public class RedisComponent {

    @Resource(name = "redisUtil")
    RedisUtil redisUtil;

    /**
     * 保存用户信息
     */
    public void saveTokenUserInfo(LoginUserVO loginUserVO) {
        String val = JacksonUtil.writeValueAsString(loginUserVO);
        redisUtil.set(SystemConstant.REDIS_KEY_TOKEN + loginUserVO.getToken(),
                val,
                SystemConstant.REDIS_KEY_EXPIRES_DAY * 2);

        redisUtil.set(SystemConstant.REDIS_KEY_TOKEN_USERID + loginUserVO.getId(),
                loginUserVO.getToken(),
                SystemConstant.REDIS_KEY_EXPIRES_DAY * 2);
    }

    /**
     * 通过 token 获取用户信息
     * @param token 用户token
     * @return 用户信息
     */
    public LoginUserVO getUserInfoByToken(String token) {
        String val = redisUtil.get(SystemConstant.REDIS_KEY_TOKEN + token);
        return val == null ? null : JacksonUtil.readValue(val, LoginUserVO.class);
    }

    /**
     *
     * @param token
     * @return
     */
    public Long getUserId(String token) {
        LoginUserVO currentUser = getUserInfoByToken(token);
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser.getId();
    }

    /**
     * 通过向量文档ID获取文档名称
     * @param vectorDocId 向量文档ID，格式如 "doc_15_3"
     * @return 文档名称
     */
    public String getDocumentNameByVectorId(String vectorDocId) {
        if (vectorDocId == null || vectorDocId.isEmpty()) {
            return null;
        }
        String redisKey = "rag:" + vectorDocId;
        return redisUtil.hGet(redisKey, "fileName");
    }
}
