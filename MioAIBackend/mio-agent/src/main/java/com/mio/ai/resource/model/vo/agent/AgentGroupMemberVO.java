package com.mio.ai.resource.model.vo.agent;

import lombok.Data;

import java.io.Serializable;

/**
 * 群聊成员概要（agent.type=2 的列表/详情附带）：前端渲染堆叠群头像与成员气泡头像用
 */
@Data
public class AgentGroupMemberVO implements Serializable {

    private Long id;

    private String name;

    /** bot-icon 配置 JSON */
    private String icon;

    /** 头像URL（icon 缺失时的回退） */
    private String avatar;
}
