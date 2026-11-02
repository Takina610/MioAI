package com.mio.ai.bot.agent;

import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.service.AgentMessageService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 事件通道回放缓冲溢出回归测试。
 * <p>20000 条事件上限：超过后必须停用回放（否则病态巨轮会让服务端内存无限增长），
 * 且 {@code isReplayDisabled()} 要如实反映，前端据此回退到落库还原。
 */
class BotEventChannelReplayBufferTest {

    /** 只记录、不落库的桩服务 */
    private static AgentMessageService stubService() {
        return new AgentMessageService() {
            @Override
            public void append(AgentMessageDO message) {
            }

            @Override
            public void updateDisplay(long id, String blocksJson, String planJson) {
            }

            @Override
            public void deleteById(long id) {
            }

            @Override
            public List<AgentMessageDO> listByConversation(String conversationId) {
                return List.of();
            }

            @Override
            public void deleteByConversation(String conversationId) {
            }

            @Override
            public void deleteAfterSeq(String conversationId, long keepThroughSeq) {
            }

            @Override
            public void deleteAfterSeqWithArchive(String conversationId, long keepThroughSeq, long groupKey) {
            }

            @Override
            public java.util.Map<Long, List<com.mio.ai.bot.model.entity.AgentMessageVersionDO>> versionsByGroup(
                    String conversationId) {
                return java.util.Map.of();
            }
        };
    }

    @Test
    void replayStaysEnabledBelowLimit() {
        BotEventChannel channel = new BotEventChannel(stubService(), "chat-under", null, 1L);
        for (int i = 0; i < 500; i++) {
            channel.answerDelta("x");
        }
        Assertions.assertFalse(channel.isReplayDisabled(), "500 条远低于上限，回放应仍可用");
    }

    /**
     * 溢出后必须停用回放。20000 条真实事件循环较慢但可接受（仅内存操作）。
     */
    @Test
    void replayIsDisabledAfterExceedingLimit() {
        BotEventChannel channel = new BotEventChannel(stubService(), "chat-overflow", null, 1L);
        // 上限 20000：发满后第 20001 条触发清空并停用
        for (int i = 0; i < 20_005; i++) {
            channel.answerDelta("y");
        }
        Assertions.assertTrue(channel.isReplayDisabled(), "超过 20000 条后回放必须停用");
    }

    /** 心跳不入缓冲：只有心跳的通道不应因事件数增长而停用回放 */
    @Test
    void heartbeatsDoNotConsumeReplayBuffer() {
        BotEventChannel channel = new BotEventChannel(stubService(), "chat-heartbeat", null, 1L);
        for (int i = 0; i < 25_000; i++) {
            channel.heartbeat();
        }
        Assertions.assertFalse(channel.isReplayDisabled(), "心跳不占回放缓冲");
    }
}
