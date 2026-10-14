package com.mio.ai.resource.service.log;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.entity.SkillUsageLog;

/**
 * 技能使用日志服务接口
 */
public interface SkillUsageLogService extends IService<SkillUsageLog> {

    /**
     * 记录一次技能使用（每轮每技能一条，落库失败静默——不影响对话主流程）
     */
    void record(Long userId, Long agentId, Long conversationId, Long skillId, String skillName);
}
