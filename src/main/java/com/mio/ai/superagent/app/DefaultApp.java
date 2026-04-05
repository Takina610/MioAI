package com.mio.ai.superagent.app;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * @author: Takina
 * @date: 2026/4/4 19:49
 * @description:
 */

@Component
public class DefaultApp {
    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Resource(name = "defaultChatClient")
    private ChatClient chatClient;

    @Autowired
    MessageChatMemoryAdvisor memoryAdvisor;

    public Flux<String> doChat(ChatVO chatVO) {
        var prompt = chatClient.prompt()
                .user(chatVO.getMessage());

        // 只有 userId 存在时，才启用记忆
        if (chatVO.getUserId() != null) {
            chatHistoryRepository.save(chatVO);

            prompt.advisors(memoryAdvisor);
            prompt.advisors(spec ->
                    spec.param(ChatMemory.CONVERSATION_ID, chatVO.getChatId())
            );
        }

        return prompt.stream().content();
    }
}
