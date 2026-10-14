package com.mio.ai.resource.service.log;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.entity.UserActivityLog;

/**
 * 用户执行日志服务接口：控制台写操作 + 登录/注册等语义动作的完整执行履历
 */
public interface UserActivityLogService extends IService<UserActivityLog> {

    /**
     * 异步落库（专用单线程池，队列满丢弃最旧——审计日志绝不阻塞/拖垮业务请求）
     */
    void recordAsync(UserActivityLog row);
}
