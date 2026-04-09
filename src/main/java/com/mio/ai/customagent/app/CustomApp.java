package com.mio.ai.customagent.app;

import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.customagent.model.entity.McpTool;
import com.mio.ai.customagent.model.vo.AgentVO;
import com.mio.ai.customagent.service.AgentService;
import com.mio.ai.customagent.service.McpClientManagerService;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/8 9:10
 * @description:
 */

@Component
@Slf4j
public class CustomApp {

    @Resource(name = "customChatClient")
    ChatClient chatClient;

    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Autowired
    McpClientManagerService mcpClientManagerService;

    @Autowired
    AgentService agentService;

    public Flux<String> doChat(ChatVO chatVO) {
        AgentVO agent = agentService.getAgentById(chatVO.getAgentId());
        if (null == agent) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体不存在");
        }

        List<McpTool> mcpTools = mcpClientManagerService.getAgentMcpTools(chatVO.getAgentId());
        ToolCallback[] toolCallbacks = mcpClientManagerService.initMcpToolCallbacks(mcpTools);

        chatHistoryRepository.save(chatVO);
        
        var promptSpec = chatClient.prompt()
                .user(chatVO.getMessage())
                .system(agent.getSystemPrompt())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatVO.getChatId()));
        
        if (toolCallbacks.length > 0) {
            promptSpec.toolCallbacks(toolCallbacks);
        }
        
        return promptSpec.stream().content();
    }
}
