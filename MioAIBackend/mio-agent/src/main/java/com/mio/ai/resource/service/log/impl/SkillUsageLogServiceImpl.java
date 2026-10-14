package com.mio.ai.resource.service.log.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.resource.mapper.log.SkillUsageLogMapper;
import com.mio.ai.resource.model.entity.SkillUsageLog;
import com.mio.ai.resource.service.log.SkillUsageLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 技能使用日志服务实现类
 */
@Slf4j
@Service
public class SkillUsageLogServiceImpl extends ServiceImpl<SkillUsageLogMapper, SkillUsageLog>
        implements SkillUsageLogService {

    @Override
    public void record(Long userId, Long agentId, Long conversationId, Long skillId, String skillName) {
        if (userId == null || skillId == null) {
            return;
        }
        try {
            SkillUsageLog row = new SkillUsageLog();
            row.setUserId(userId);
            row.setAgentId(agentId);
            row.setConversationId(conversationId);
            row.setSkillId(skillId);
            row.setSkillName(skillName);
            row.setCreateTime(new Date());
            this.save(row);
        } catch (Exception e) {
            log.error("技能使用日志落库失败 skillId={}", skillId, e);
        }
    }
}
