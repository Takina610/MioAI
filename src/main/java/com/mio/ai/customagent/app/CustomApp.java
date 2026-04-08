package com.mio.ai.customagent.app;

import com.mio.ai.superagent.model.vo.ChatVO;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * @author: Takina
 * @date: 2026/4/8 9:10
 * @description:
 */

@Component
public class CustomApp {

    @Resource(name = "customChatClient")
    ChatClient chatClient;

    public Flux<String> doChat(ChatVO chatVO) {

    }
}
