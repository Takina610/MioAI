package com.mio.ai.superagent.controller;

import com.mio.ai.superagent.app.DefaultApp;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.util.SseStreams;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @author: Takina
 * @date: 2026/4/4 19:46
 * @description:
 */

@Validated
@RestController
public class DefaultController {

    @Autowired
    DefaultApp defaultApp;

    @GetMapping("/chat")
    public SseEmitter doChat(@RequestParam @NotBlank @Size(max = 64) String chatId,
                             @RequestParam @NotNull Long agentId,
                             @RequestParam @NotBlank @Size(max = 20000) String content,
                             @RequestParam(required = false) Long userId) {
        ChatVO chatVO = new ChatVO();
        chatVO.setChatId(chatId);
        chatVO.setMessage(content);
        chatVO.setAgentId(agentId);
        chatVO.setUserId(userId);

        // 创建 SseEmitter 并把模型流推送出去（发送失败不取消上游，保证落库）
        SseEmitter sseEmitter = new SseEmitter(SseStreams.CHAT_TIMEOUT_MS);
        SseStreams.pipe(defaultApp.doChat(chatVO), sseEmitter);
        // 返回
        return sseEmitter;
    }
}
