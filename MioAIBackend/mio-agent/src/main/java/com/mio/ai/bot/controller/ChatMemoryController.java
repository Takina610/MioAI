package com.mio.ai.bot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;import com.mio.ai.bot.model.entity.ChatConversationDO;
import com.mio.ai.bot.repository.ChatHistoryRepository;
import com.mio.ai.user.model.vo.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/31 19:21
 * @description:
 */

@RequestMapping("/memory")
@RestController
public class ChatMemoryController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ChatMemoryController.class);

    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    RedisComponent redisComponent;

    @Autowired
    com.mio.ai.bot.service.AgentMessageService agentMessageService;

    @Autowired
    com.mio.ai.bot.service.ChatFileService chatFileService;

    @Autowired
    com.mio.ai.bot.service.AttachmentDeleteQueue attachmentDeleteQueue;


    /**
     * 获取会话列表
     */
    @GetMapping("/getChatIds/{agentId}")
    @LogInfo
    public BaseResponse<List<ChatConversationDO>> getChatIds(@PathVariable Long agentId, HttpServletRequest request){
        return ResultUtils
                .success(chatHistoryRepository
                        .getChats(redisComponent.getUserId(request.getHeader("token")), agentId));
    }

    /**
     * 分页获取会话列表
     * @param current 当前页
     * @param size 每页大小
     * @param request HTTP请求
     * @return 分页会话列表
     */
    @GetMapping("/getChatIdsPage/{agentId}")
    @LogInfo
    public BaseResponse<Page<ChatConversationDO>> getChatIdsPage(
            @PathVariable Long agentId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            HttpServletRequest request) {
        return ResultUtils
            .success(chatHistoryRepository
            .getChatsPage(redisComponent.getUserId(request.getHeader("token")), agentId, current, size));
    }

    /**
     * 删除会话（仅会话所有者可删）：清会话行、对话记忆、消息流水与版本归档；
     * 沙箱暂存文件（uploads/outputs 的 &lt;chatId&gt; 目录）按 chat_file 台账异步清理。
     * 工具调用日志等审计数据持久保留，不随会话删除。
     * @param chatId
     * @return
     */
    @PostMapping("/deleteChat/{chatId}")
    @LogInfo
    public BaseResponse<?> deleteChat(@PathVariable String chatId, HttpServletRequest request){
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        checkChatOwner(chatId, userId);
        // 执行中的会话禁止删除：与截断同一规则——删后落库行会把已删数据写回
        if (MioBotController.hasActiveTurn(chatId)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "会话正在执行中，请等待本轮完成后再删除");
        }
        try {
            chatHistoryRepository.clearByChatId(chatId);
            chatMemory.clear(chatId);
            agentMessageService.deleteByConversation(chatId);
            // 台账先置待删（持久凭证），再入队删沙箱目录；入队失败由兜底扫描重入队
            chatFileService.markPendingByConversation(chatId);
        } catch (Exception e){
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
        try {
            attachmentDeleteQueue.enqueueChatDir(chatId);
        } catch (Exception e){
            // 不影响删除结果：待删台账行已在，兜底扫描会重入队
            log.warn("会话文件删除入队失败（兜底扫描会重试）, chatId={}: {}", chatId, e.getMessage());
        }
        return ResultUtils.success(true);
    }

    /**
     * 根据会话ID获取会话信息（仅会话所有者可读）
     * @param conversationId 会话ID
     * @return 会话信息
     */
    @GetMapping("/getConversation/{conversationId}")
    @LogInfo
    public BaseResponse<ChatConversationDO> getConversation(@PathVariable String conversationId, HttpServletRequest request) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        checkChatOwner(conversationId, userId);
        ChatConversationDO conversation = chatHistoryRepository.getChatByConversationId(conversationId);
        if (conversation == null) {
            return ResultUtils.success(null);
        }
        return ResultUtils.success(conversation);
    }

    /**
     * 校验会话归属，防止任意用户读取/删除他人会话
     */
    private void checkChatOwner(String chatId, Long userId) {
        ChatConversationDO conversation = chatHistoryRepository.getChatByConversationId(chatId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        if (conversation.getUserId() == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该会话");
        }
    }
}
