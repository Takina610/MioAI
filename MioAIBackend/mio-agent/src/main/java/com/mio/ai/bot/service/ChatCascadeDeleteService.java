package com.mio.ai.bot.service;

import com.mio.ai.bot.repository.ChatHistoryRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

/**
 * 会话完整级联删除：会话行 + 对话记忆 + 消息流水与版本归档 + chat_file 台账置待删 +
 * 沙箱暂存目录（uploads/outputs 的 chatId 目录）异步删除。
 * 供删除单个会话与删除智能体级联清理该智能体全部会话复用；
 * 调用方负责会话归属校验与执行中轮次检查。工具调用日志等审计数据持久保留，不随会话删除。
 */
@Slf4j
@Service
public class ChatCascadeDeleteService {

    @Resource
    private ChatHistoryRepository chatHistoryRepository;

    @Resource
    private ChatMemory chatMemory;

    @Resource
    private AgentMessageService agentMessageService;

    @Resource
    private ChatFileService chatFileService;

    @Resource
    private AttachmentDeleteQueue attachmentDeleteQueue;

    public void deleteConversationCompletely(String chatId) {
        chatHistoryRepository.clearByChatId(chatId);
        chatMemory.clear(chatId);
        agentMessageService.deleteByConversation(chatId);
        // 台账先置待删（持久凭证），再入队删沙箱目录；入队失败由兜底扫描重入队
        chatFileService.markPendingByConversation(chatId);
        try {
            attachmentDeleteQueue.enqueueChatDir(chatId);
        } catch (Exception e) {
            // 不影响删除结果：待删台账行已在，兜底扫描会重入队
            log.warn("会话文件删除入队失败（兜底扫描会重试）, chatId={}: {}", chatId, e.getMessage());
        }
    }
}
