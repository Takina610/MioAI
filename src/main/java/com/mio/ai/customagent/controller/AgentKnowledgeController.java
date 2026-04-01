package com.mio.ai.customagent.controller;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.agentknowledge.AgentKnowledgeAddRequest;
import com.mio.ai.customagent.service.AgentKnowledgeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-知识库关联接口
 */
@Slf4j
@RestController
@RequestMapping("/agent-knowledge")
public class AgentKnowledgeController {

    @Resource
    private AgentKnowledgeService agentKnowledgeService;

    @PostMapping
    public BaseResponse<Long> addAgentKnowledge(@RequestBody AgentKnowledgeAddRequest request) {
        Long id = agentKnowledgeService.addAgentKnowledge(request);
        return ResultUtils.success(id);
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteAgentKnowledge(@PathVariable Long id) {
        boolean result = agentKnowledgeService.deleteAgentKnowledge(id);
        return ResultUtils.success(result);
    }

    @GetMapping("/agent/{agentId}")
    public BaseResponse<List<Long>> getKbIdsByAgentId(@PathVariable Long agentId) {
        List<Long> kbIds = agentKnowledgeService.getKbIdsByAgentId(agentId);
        return ResultUtils.success(kbIds);
    }
}
