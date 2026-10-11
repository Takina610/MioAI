package com.mio.ai.resource.service.agent.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.framework.rag.RetrievalConfig;
import com.mio.ai.resource.mapper.agent.AgentKnowledgeMapper;
import com.mio.ai.resource.model.dto.agentknowledge.AgentKnowledgeAddRequest;
import com.mio.ai.resource.model.dto.agentknowledge.AgentKnowledgeUpdateRequest;
import com.mio.ai.resource.model.entity.AgentKnowledge;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.model.vo.knowledge.AgentKnowledgeBindingVO;
import com.mio.ai.resource.service.agent.AgentKnowledgeService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-知识库关联服务实现类
 */
@Service
public class AgentKnowledgeServiceImpl extends ServiceImpl<AgentKnowledgeMapper, AgentKnowledge> implements AgentKnowledgeService {

    @Resource
    @Lazy
    private KnowledgeBaseService knowledgeBaseService;

    @Override
    public List<AgentKnowledgeBindingVO> getBindingDetailsByAgentId(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        List<AgentKnowledge> bindings = this.lambdaQuery()
                .eq(AgentKnowledge::getAgentId, agentId)
                .orderByAsc(AgentKnowledge::getId)
                .list();
        if (bindings.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeBase> kbMap = knowledgeBaseService.listByIds(
                        bindings.stream().map(AgentKnowledge::getKbId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, Function.identity(), (a, b) -> a));
        return bindings.stream().map(binding -> {
            AgentKnowledgeBindingVO vo = new AgentKnowledgeBindingVO();
            vo.setId(binding.getId());
            vo.setAgentId(binding.getAgentId());
            vo.setKbId(binding.getKbId());
            KnowledgeBase kb = kbMap.get(binding.getKbId());
            vo.setKbName(kb != null ? kb.getName() : "未知知识库");
            RetrievalConfig config = RetrievalConfig.fromJson(binding.getRetrievalConfig());
            vo.setTopK(config.getTopK());
            vo.setThreshold(config.getThreshold());
            vo.setMethod(config.getMethod());
            vo.setEnableRerank(config.isEnableRerank());
            vo.setRerankTopN(config.getRerankTopN());
            vo.setEnabled(binding.getEnabled());
            vo.setCreateTime(binding.getCreateTime());
            return vo;
        }).toList();
    }

    @Override
    public boolean updateAgentKnowledge(Long id, AgentKnowledgeUpdateRequest request) {
        if (id == null || request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        AgentKnowledge binding = this.getById(id);
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "绑定关系不存在");
        }
        // 在现有配置基础上做覆盖式更新，未传字段保持原值
        RetrievalConfig config = RetrievalConfig.fromJson(binding.getRetrievalConfig());
        if (request.getTopK() != null) {
            config.setTopK(Math.max(1, Math.min(request.getTopK(), 50)));
        }
        if (request.getThreshold() != null && request.getThreshold() >= 0 && request.getThreshold() <= 1) {
            config.setThreshold(request.getThreshold());
        }
        if (request.getMethod() != null && !request.getMethod().isBlank()) {
            config.setMethod(RetrievalConfig.METHOD_HYBRID.equalsIgnoreCase(request.getMethod().trim())
                    ? RetrievalConfig.METHOD_HYBRID : RetrievalConfig.METHOD_VECTOR);
        }
        if (request.getEnableRerank() != null) {
            config.setEnableRerank(request.getEnableRerank());
        }
        if (request.getRerankTopN() != null) {
            config.setRerankTopN(Math.max(1, Math.min(request.getRerankTopN(), 20)));
        }
        binding.setRetrievalConfig(JSONUtil.toJsonStr(config));
        if (request.getEnabled() != null) {
            binding.setEnabled(request.getEnabled() == 0 ? 0 : 1);
        }
        return this.updateById(binding);
    }

    @Override
    public Long addAgentKnowledge(AgentKnowledgeAddRequest request) {
        if (request.getAgentId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        if (request.getKbId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }
        AgentKnowledge agentKnowledge = new AgentKnowledge();
        agentKnowledge.setAgentId(request.getAgentId());
        agentKnowledge.setKbId(request.getKbId());
        agentKnowledge.setRetrievalConfig(request.getRetrievalConfig());
        agentKnowledge.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        this.save(agentKnowledge);
        return agentKnowledge.getId();
    }

    @Override
    public boolean deleteAgentKnowledge(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "关联ID不能为空");
        }
        return this.removeById(id);
    }

    @Override
    public boolean deleteByAgentIdAndKbId(Long agentId, Long kbId) {
        if (agentId == null || kbId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID和知识库ID不能为空");
        }
        LambdaQueryWrapper<AgentKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledge::getAgentId, agentId)
                .eq(AgentKnowledge::getKbId, kbId);
        return this.remove(wrapper);
    }

    @Override
    public List<Long> getKbIdsByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledge::getAgentId, agentId)
                .eq(AgentKnowledge::getEnabled, 1);
        List<AgentKnowledge> list = this.list(wrapper);
        return list.stream().map(AgentKnowledge::getKbId).toList();
    }

    @Override
    public List<AgentKnowledge> getEnabledBindingsByAgentId(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        LambdaQueryWrapper<AgentKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledge::getAgentId, agentId)
                .eq(AgentKnowledge::getEnabled, 1)
                .orderByAsc(AgentKnowledge::getId);
        return this.list(wrapper);
    }

    @Override
    public boolean deleteByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledge::getAgentId, agentId);
        return this.remove(wrapper);
    }
}
