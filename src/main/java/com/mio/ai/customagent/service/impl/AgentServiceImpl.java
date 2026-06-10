package com.mio.ai.customagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.common.FileType;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.R2Util;
import com.mio.ai.customagent.mapper.AgentKnowledgeMapper;
import com.mio.ai.customagent.mapper.AgentMcpMapper;
import com.mio.ai.customagent.mapper.AgentMapper;
import com.mio.ai.customagent.mapper.KnowledgeBaseMapper;
import com.mio.ai.customagent.mapper.McpToolMapper;
import com.mio.ai.customagent.model.dto.agent.AgentAddRequest;
import com.mio.ai.customagent.model.dto.agent.AgentQueryRequest;
import com.mio.ai.customagent.model.dto.agent.AgentUpdateRequest;
import com.mio.ai.customagent.model.entity.Agent;
import com.mio.ai.customagent.model.entity.AgentKnowledge;
import com.mio.ai.customagent.model.entity.AgentMcp;
import com.mio.ai.customagent.model.entity.KnowledgeBase;
import com.mio.ai.customagent.model.entity.McpTool;
import com.mio.ai.customagent.model.enums.AgentStatusEnum;
import com.mio.ai.customagent.model.enums.AgentTypeEnum;
import com.mio.ai.customagent.model.enums.KnowledgeBaseStatusEnum;
import com.mio.ai.customagent.model.enums.McpToolStatusEnum;
import com.mio.ai.customagent.model.vo.AgentDetailVO;
import com.mio.ai.customagent.model.vo.AgentVO;
import com.mio.ai.customagent.model.vo.KnowledgeBaseVO;
import com.mio.ai.customagent.model.vo.McpToolVO;
import com.mio.ai.customagent.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体服务实现类
 */
@Service
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent> implements AgentService {

    @Autowired
    private R2Util r2Util;

    @Autowired
    private AgentMcpMapper agentMcpMapper;

    @Autowired
    private AgentKnowledgeMapper agentKnowledgeMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private McpToolMapper mcpToolMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addAgent(AgentAddRequest request, Long userId) {
        if (StringUtils.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体名称不能为空");
        }

        Agent agent = new Agent();
        BeanUtil.copyProperties(request, agent);
        agent.setUserId(userId);
        agent.setType(AgentTypeEnum.CUSTOM.getCode());
        agent.setStatus(AgentStatusEnum.DRAFT.getCode());
        agent.setUsageCount(0);
        this.save(agent);

        String avatar = request.getAvatar();
        if (StringUtils.isNotBlank(avatar) && avatar.contains("temp_")) {
            try {
                String newAvatarUrl = r2Util.copyFile(avatar, FileType.AGENT_AVATAR, String.valueOf(agent.getId()));
                agent.setAvatar(newAvatarUrl);
                this.updateById(agent);
            } catch (IOException e) {
                this.removeById(agent.getId());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "处理头像失败: " + e.getMessage());
            }
        }

        return agent.getId();
    }

    @Override
    public boolean updateAgent(AgentUpdateRequest request, Long userId) {
        if (request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        Agent agent = this.getById(request.getId());
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        if (!agent.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改该智能体");
        }
        agent.setUpdateTime(new Date());

        String newAvatar = request.getAvatar();
        String oldAvatar = agent.getAvatar();
        String defaultAvatar = "https://cdn.tak1na.cn/custom_agent.png";

        if (!newAvatar.equals(oldAvatar) && StringUtils.isNotBlank(newAvatar) && newAvatar.contains("temp_")) {
            try {
                // 删除旧头像（如果不是默认头像）
                if (StringUtils.isNotBlank(oldAvatar) && !oldAvatar.equals(defaultAvatar)) {
                    r2Util.deleteFile(oldAvatar);
                }
                
                String finalAvatarUrl = r2Util.copyFile(newAvatar, FileType.AGENT_AVATAR, String.valueOf(agent.getId()));
                request.setAvatar(finalAvatarUrl);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "处理头像失败: " + e.getMessage());
            }
        }

        BeanUtil.copyProperties(request, agent);
        return this.updateById(agent);
    }

    @Override
    public boolean deleteAgent(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        Agent agent = this.getById(id);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        if (!agent.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除该智能体");
        }
        return this.removeById(id);
    }

    @Override
    public AgentVO getAgentById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        Agent agent = this.getById(id);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        return convertToVO(agent);
    }

    @Override
    public Page<AgentVO> queryAgents(AgentQueryRequest request) {
        Page<Agent> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(request.getName()), Agent::getName, request.getName())
                .eq(request.getUserId() != null, Agent::getUserId, request.getUserId())
                .ne(Agent::getType, AgentTypeEnum.GENERAL.getCode())
                .orderByDesc(Agent::getUpdateTime);
        Page<Agent> agentPage = this.page(page, wrapper);
        Page<AgentVO> voPage = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
        voPage.setRecords(agentPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public Page<AgentVO> getPublicAgents(long current, long size) {
        Page<Agent> page = new Page<>(current, size);
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getIsPublic, 1)
                .eq(Agent::getStatus, AgentStatusEnum.PUBLISHED.getCode())
                .orderByDesc(Agent::getCreateTime);
        Page<Agent> agentPage = this.page(page, wrapper);
        Page<AgentVO> voPage = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
        voPage.setRecords(agentPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    private AgentVO convertToVO(Agent agent) {
        AgentVO vo = new AgentVO();
        BeanUtil.copyProperties(agent, vo);
        AgentTypeEnum typeEnum = AgentTypeEnum.getByCode(agent.getType());
        vo.setTypeDesc(typeEnum != null ? typeEnum.getDesc() : "未知");
        AgentStatusEnum statusEnum = AgentStatusEnum.getByCode(agent.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return vo;
    }

    @Override
    public AgentDetailVO getAgentDetailById(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        Agent agent = this.getById(id);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        if (!agent.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看该智能体");
        }

        AgentDetailVO vo = new AgentDetailVO();
        BeanUtil.copyProperties(agent, vo);
        AgentTypeEnum typeEnum = AgentTypeEnum.getByCode(agent.getType());
        vo.setTypeDesc(typeEnum != null ? typeEnum.getDesc() : "未知");
        AgentStatusEnum statusEnum = AgentStatusEnum.getByCode(agent.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");

        vo.setKnowledgeBases(getKnowledgeBasesByAgentId(id));
        vo.setMcpTools(getMcpToolsByAgentId(id));

        return vo;
    }

    private List<KnowledgeBaseVO> getKnowledgeBasesByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentKnowledge> akWrapper = new LambdaQueryWrapper<>();
        akWrapper.eq(AgentKnowledge::getAgentId, agentId)
                .eq(AgentKnowledge::getEnabled, 1);
        List<AgentKnowledge> akList = agentKnowledgeMapper.selectList(akWrapper);

        if (akList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> kbIds = akList.stream().map(AgentKnowledge::getKbId).toList();
        List<KnowledgeBase> kbList = knowledgeBaseMapper.selectBatchIds(kbIds);

        return kbList.stream().map(kb -> {
            KnowledgeBaseVO kbVo = new KnowledgeBaseVO();
            BeanUtil.copyProperties(kb, kbVo);
            KnowledgeBaseStatusEnum statusEnum = KnowledgeBaseStatusEnum.getByCode(kb.getStatus());
            kbVo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
            return kbVo;
        }).toList();
    }

    private List<McpToolVO> getMcpToolsByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentMcp> amWrapper = new LambdaQueryWrapper<>();
        amWrapper.eq(AgentMcp::getAgentId, agentId)
                .eq(AgentMcp::getEnabled, 1);
        List<AgentMcp> amList = agentMcpMapper.selectList(amWrapper);

        if (amList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> mcpIds = amList.stream().map(AgentMcp::getMcpId).toList();
        List<McpTool> mcpList = mcpToolMapper.selectBatchIds(mcpIds);

        return mcpList.stream().map(mcp -> {
            McpToolVO mcpVo = new McpToolVO();
            BeanUtil.copyProperties(mcp, mcpVo);
            McpToolStatusEnum statusEnum = McpToolStatusEnum.getByCode(mcp.getStatus());
            mcpVo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
            return mcpVo;
        }).toList();
    }

    @Override
    public void publishAgent(Long agentId, AgentUpdateRequest request, Long userId) {
        Agent agent = checkAgentOwner(agentId, userId);

        String newAvatar = request.getAvatar();
        String oldAvatar = agent.getAvatar();

        if (newAvatar != null && !Objects.equals(newAvatar, oldAvatar) && newAvatar.contains("temp_")) {
            try {
                String finalAvatarUrl = r2Util.copyFile(newAvatar, FileType.AGENT_AVATAR, String.valueOf(agent.getId()));
                request.setAvatar(finalAvatarUrl);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "处理头像失败: " + e.getMessage());
            }
        }

        if (request.getName() != null) {
            agent.setName(request.getName());
        }
        if (request.getDescription() != null) {
            agent.setDescription(request.getDescription());
        }
        if (request.getAvatar() != null) {
            agent.setAvatar(request.getAvatar());
        }
        if (request.getSystemPrompt() != null) {
            agent.setSystemPrompt(request.getSystemPrompt());
        }

        agent.setStatus(AgentStatusEnum.PUBLISHED.getCode());
        this.updateById(agent);
    }

    private Agent checkAgentOwner(Long agentId, Long userId) {
        if (agentId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        Agent agent = this.getById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        if (!agent.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该智能体");
        }
        return agent;
    }
}
