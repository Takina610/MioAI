package com.mio.ai.bot.service;

import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.model.entity.AgentMessageVersionDO;

import java.util.List;
import java.util.Map;

/**
 * 智能体消息完整持久化服务（增删查）：
 * <p>assistant 行携带完整 blocks/plan/duration，供前端原样还原 Agent 工作过程。
 * <p>版本组持久化：编辑/重新生成截断历史前把旧轮次对归档进 agent_message_version
 * （按轮次组锚 group_seq 聚合，前端 &lt;n/n&gt; 切换的持久化来源）。
 */
public interface AgentMessageService {

    /** 追加一条消息（seq 自动分配） */
    void append(AgentMessageDO message);

    /** 按会话取出全部消息（seq 升序） */
    List<AgentMessageDO> listByConversation(String conversationId);

    /** 删除会话的全部消息（会话删除时级联，含版本归档） */
    void deleteByConversation(String conversationId);

    /** 删除 seq 大于 keepThroughSeq 的消息（无归档的原始截断） */
    void deleteAfterSeq(String conversationId, long keepThroughSeq);

    /**
     * 截断并归档：删除 seq &gt; keepThroughSeq 的消息前，把批次中首个 (user, assistant)
     * 轮次对写入版本表，归入 groupKey 组。
     */
    void deleteAfterSeqWithArchive(String conversationId, long keepThroughSeq, long groupKey);

    /** 会话的全部历史版本，按组锚分组（组内 version_index 升序） */
    Map<Long, List<AgentMessageVersionDO>> versionsByGroup(String conversationId);
}
