package com.mio.ai.superagent.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mio.ai.superagent.mapper.ChatConversationDOMapper;
import com.mio.ai.superagent.model.entity.ChatConversationDO;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    @GetMapping("/summary")
    public String generateTitle(@RequestParam Long agentId, @RequestParam String conversationId, @RequestParam String content) {
        String prompt = """
            请你给下面这段对话，生成一个简短标题，要求：
            1. 一句话
            2. 不超过15个字
            3. 直白、概括核心内容
            
            对话内容：
            %s
            """.formatted(content);

        // 调用 AI 生成标题
        String title = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // 保存标题到本地
        UpdateWrapper<ChatConversationDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda()
                .eq(ChatConversationDO::getAgentId, agentId)
                .eq(ChatConversationDO::getConversationId, conversationId)
                .set(ChatConversationDO::getTitle, title);
        chatConversationDOMapper.update(updateWrapper);

        return title;
    }
}