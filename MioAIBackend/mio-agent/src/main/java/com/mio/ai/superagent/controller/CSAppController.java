package com.mio.ai.superagent.controller;

import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.superagent.app.CSApp;
import com.mio.ai.superagent.model.vo.ChatVO;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
/**
 * @author: Takina
 * @date: 2026/3/29 20:54
 * @description:
 */

@Validated
@RequestMapping("/cs")
@RestController
public class CSAppController {

    @Resource
    private CSApp csApp;

    @Autowired
    RedisComponent redisComponent;

    /**
     * SSE 流式调用
     *
     * @return
     */
    @GetMapping("/chat")
    @LogInfo
    public SseEmitter doChat(@RequestParam @NotBlank @Size(max = 64) String chatId,
                             @RequestParam @NotNull Long agentId,
                             @RequestParam @NotBlank @Size(max = 20000) String content,
                             @RequestParam @NotBlank String token) {
        // 创建 ChatVO
        ChatVO chatVO = new ChatVO();
        chatVO.setChatId(chatId);
        chatVO.setMessage(content);
        chatVO.setAgentId(agentId);
        chatVO.setUserId(redisComponent.getUserId(token));

        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(45000L); // 1.5 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        csApp.doChat(chatVO)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, () -> {
                    try {
                        sseEmitter.send("[DONE]");
                    } catch (IOException e) {
                        // ignore
                    }
                    sseEmitter.complete();
                });
        // 返回
        return sseEmitter;
    }
}
