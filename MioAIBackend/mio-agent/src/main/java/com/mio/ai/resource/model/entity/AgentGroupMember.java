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
 * 群聊智能体成员（agent.type=2 的群与其成员 type=1 智能体的关联）。
 * 成员只能是用户自己的自定义智能体，创建时校验；随群或成员删除物理清理。
 */
@Data
@TableName("agent_group_member")
public class AgentGroupMember implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 群聊智能体ID（agent.type=2） */
    private Long groupAgentId;

    /** 成员智能体ID（agent.type=1） */
    private Long memberAgentId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
