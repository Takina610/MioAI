package com.mio.ai.superagent.controller;

import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.superagent.app.CSApp;
import com.mio.ai.superagent.model.dto.ChatMessageRequest;
import com.mio.ai.superagent.model.vo.ChatVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
/**
 * @author: Takina
 * @date: 2026/3/29 20:54
 * @description:
 */

@RequestMapping("/cs")
@RestController
public class CSAppController {

    @Resource
    private CSApp CSApp;

    @Autowired
    RedisComponent redisComponent;

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param chatMessageRequest
     * @return
     */
    @GetMapping("/chat")
    @LogInfo
    public SseEmitter doChat(@RequestBody ChatMessageRequest chatMessageRequest, HttpServletRequest request) {
        // 创建 ChatVO
        ChatVO chatVO = new ChatVO();
        chatVO.setChatId(chatMessageRequest.getChatId());
        chatVO.setMessage(chatMessageRequest.getContent());
        chatVO.setAgentId(chatMessageRequest.getAgentId());
        chatVO.setUserId(redisComponent.getUserId(request.getHeader("token")));

        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(45000L); // 1.5 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        CSApp.doChat(chatVO)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }
}
