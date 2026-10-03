package com.mio.ai.bot.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.bot.mapper.ChatConversationDOMapper;
import com.mio.ai.bot.model.dto.ChatMessageRequest;
import com.mio.ai.bot.model.entity.ChatConversationDO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/4 19:28
 * @description:
 */

@RestController
public class SummaryController {

    @Resource(name = "summaryChatClient")
    ChatClient chatClient;

    @Autowired
    ChatConversationDOMapper chatConversationDOMapper;

    /**
     * 输入一段对话文本，返回一句总结标题
     */
    @RequestMapping("/summary")
    public BaseResponse<String> generateTitle(@Valid @RequestBody ChatMessageRequest chatMessageRequest) {
        String prompt = """
            请你给下面这段对话，生成一个简短标题，要求：
            1. 一句话
            2. 不超过15个字
            3. 直白、概括核心内容
            
            对话内容：
            %s
            """.formatted(chatMessageRequest.getContent());

        // 调用 AI 生成标题
        String title = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // 保存标题到本地
        UpdateWrapper<ChatConversationDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda()
                .eq(ChatConversationDO::getAgentId, chatMessageRequest.getAgentId())
                .eq(ChatConversationDO::getConversationId, chatMessageRequest.getConversationId())
                .set(ChatConversationDO::getTitle, title);
        chatConversationDOMapper.update(updateWrapper);

        return ResultUtils.success(title);
    }
}