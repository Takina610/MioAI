package com.mio.ai.superagent.controller;

import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.customagent.service.log.AgentUsageLogService;
import com.mio.ai.customagent.service.log.ToolCallLogService;
import com.mio.ai.superagent.agent.MioManus;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @author: Takina
 * @date: 2026/3/31 19:17
 * @description:
 */

@Validated
@RequestMapping("/mio")
@RestController
public class MioManusController {
    @Resource
    private ToolCallback[] commonTools;

    @Autowired
    private ChatModel mioManusChatModel;

    @Autowired
    @Qualifier("jdbcChatMemory")
    private ChatMemory jdbcChatMemory;

    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Autowired
    RedisComponent redisComponent;

    @Autowired
    private ToolCallLogService toolCallLogService;

    @Autowired
    private AgentUsageLogService agentUsageLogService;

    /**
     * 流式调用 Manus 超级智能体
     *
     */
    @RequestMapping("/chat")
    public SseEmitter doChatWithManus(@RequestParam @NotBlank @Size(max = 64) String chatId,
                                      @RequestParam @NotNull Long agentId,
                                      @RequestParam @NotBlank @Size(max = 20000) String content,
                                      @RequestParam @NotBlank String token) {
        ChatVO chatVO = new ChatVO();
        chatVO.setChatId(chatId);
        chatVO.setMessage(content);
        chatVO.setAgentId(agentId);
        chatVO.setUserId(redisComponent.getUserId(token));
        chatHistoryRepository.save(chatVO);

        MioManus mioManus = new MioManus(
                commonTools,
                mioManusChatModel,
                jdbcChatMemory,
                agentUsageLogService,
                toolCallLogService,
                chatVO.getAgentId(),
                chatVO.getUserId(),
                parseConversationId(chatVO.getChatId())
        );
        return mioManus.runStream(content, chatVO.getChatId());
    }

    private Long parseConversationId(String chatId) {
        try {
            return Long.valueOf(chatId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
