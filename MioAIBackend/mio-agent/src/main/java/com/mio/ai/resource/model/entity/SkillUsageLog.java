package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 技能使用日志实体（skill_usage_log）：模型用 Read 读取 skills/&lt;id&gt;/ 下的文件即视为使用该技能，
 * 每轮每技能只记一条（与前端技能行展示的判定同源）。
 */
@Data
@TableName("skill_usage_log")
public class SkillUsageLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 智能体ID（群聊成员使用时为成员智能体ID）
     */
    private Long agentId;

    /**
     * 会话ID（数字型会话才有）
     */
    private Long conversationId;

    /**
     * 技能ID
     */
    private Long skillId;

    /**
     * 技能名称（冗余，防技能删除后无法展示）
     */
    private String skillName;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
