package com.mio.ai.framework.zagent.subagent;

/**
 * 群成员委派的展示回调：把成员的执行以"平等消息"呈现到会话流（产品侧实现，
 * 框架只回调时机）。成员不作为子代理工具行展示——启动/回答流/结束各自成事件。
 */
public interface MemberPresenter {

    /** 成员开始执行（前端出"正在输入"态；在资源装配前调用，指示即时可见） */
    void started(String agentId, long memberId, String name, String icon);

    /** 成员回答增量（成员正文直接外流给用户） */
    void delta(String agentId, String delta);

    /** 成员结束（failed=true 表示执行失败） */
    void finished(String agentId, boolean failed);
}
