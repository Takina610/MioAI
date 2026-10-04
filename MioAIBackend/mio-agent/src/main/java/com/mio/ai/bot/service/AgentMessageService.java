package com.mio.ai.bot.service;

import com.mio.ai.bot.model.entity.AgentMessageDO;

import java.util.List;

/**
 * 智能体消息完整持久化服务（增删查）：
 * <p>assistant 行携带完整 blocks/plan/duration，供前端原样还原 Agent 工作过程。
 */
public interface AgentMessageService {

    /** 追加一条消息（seq 自动分配） */
    void append(AgentMessageDO message);

    /** 按会话取出全部消息（seq 升序） */
    List<AgentMessageDO> listByConversation(String conversationId);

    /** 删除会话的全部消息（会话删除时级联） */
    void deleteByConversation(String conversationId);

    /** 删除 seq 大于 keepThroughSeq 的消息（编辑消息/重新生成的历史截断） */
    void deleteAfterSeq(String conversationId, long keepThroughSeq);
}
