package com.mio.ai.resource.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.resource.mapper.agent.AgentGroupMemberMapper;
import com.mio.ai.resource.mapper.agent.AgentMapper;
import com.mio.ai.resource.model.dto.agent.AgentGroupCreateRequest;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.model.entity.AgentGroupMember;
import com.mio.ai.resource.model.enums.AgentStatusEnum;
import com.mio.ai.resource.model.enums.AgentTypeEnum;
import com.mio.ai.resource.service.agent.AgentGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 群聊智能体服务实现。成员校验不走 AccessGuardService（其依赖 AgentService 会成环）：
 * 成员在创建时已被限定为用户自己的自定义智能体，可用性判定 = 归属本人即可。
 */
@Service
public class AgentGroupServiceImpl extends ServiceImpl<AgentGroupMemberMapper, AgentGroupMember>
        implements AgentGroupService {

    private static final int MIN_MEMBERS = 2;
    private static final int MAX_MEMBERS = 10;

    @Autowired
    private AgentMapper agentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createGroup(AgentGroupCreateRequest request, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        List<Long> memberIds = request.getMemberAgentIds().stream().distinct().toList();
        if (memberIds.size() < MIN_MEMBERS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "群聊至少需要 " + MIN_MEMBERS + " 个成员智能体");
        }
        if (memberIds.size() > MAX_MEMBERS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "群聊成员最多 " + MAX_MEMBERS + " 个");
        }

        Agent group = new Agent();
        group.setUserId(userId);
        group.setName(request.getName().strip());
        group.setDescription(request.getDescription() == null || request.getDescription().isBlank()
                ? "由 " + memberIds.size() + " 个智能体组成的协作群聊"
                : request.getDescription().strip());
        group.setType(AgentTypeEnum.GROUP.getCode());
        // 群聊无草稿流程，创建即可聊；不进广场
        group.setStatus(AgentStatusEnum.PUBLISHED.getCode());
        group.setIsPublic(0);
        group.setUsageCount(0);
        agentMapper.insert(group);

        List<AgentGroupMember> rows = new ArrayList<>();
        for (Long memberId : memberIds) {
            Agent member = agentMapper.selectById(memberId);
            // 成员必须是本人的自定义智能体（群聊不可嵌套，内置智能体不进群）
            if (member == null || !userId.equals(member.getUserId())
                    || !Integer.valueOf(AgentTypeEnum.CUSTOM.getCode()).equals(member.getType())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "成员智能体不可用：#" + memberId);
            }
            AgentGroupMember row = new AgentGroupMember();
            row.setGroupAgentId(group.getId());
            row.setMemberAgentId(memberId);
            row.setCreateTime(new Date());
            rows.add(row);
        }
        this.saveBatch(rows);
        return group.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateGroup(com.mio.ai.resource.model.dto.agent.AgentGroupUpdateRequest request, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Agent group = agentMapper.selectById(request.getId());
        if (group == null || !userId.equals(group.getUserId())
                || !Integer.valueOf(AgentTypeEnum.GROUP.getCode()).equals(group.getType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "群聊不存在");
        }
        List<Long> memberIds = request.getMemberAgentIds().stream().distinct().toList();
        if (memberIds.size() < MIN_MEMBERS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "群聊至少需要保留 " + MIN_MEMBERS + " 个成员");
        }
        for (Long memberId : memberIds) {
            Agent member = agentMapper.selectById(memberId);
            if (member == null || !userId.equals(member.getUserId())
                    || !Integer.valueOf(AgentTypeEnum.CUSTOM.getCode()).equals(member.getType())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "成员智能体不可用：#" + memberId);
            }
        }
        group.setName(request.getName().strip());
        agentMapper.updateById(group);
        this.remove(new LambdaQueryWrapper<AgentGroupMember>()
                .eq(AgentGroupMember::getGroupAgentId, group.getId()));
        List<AgentGroupMember> rows = new ArrayList<>();
        for (Long memberId : memberIds) {
            AgentGroupMember row = new AgentGroupMember();
            row.setGroupAgentId(group.getId());
            row.setMemberAgentId(memberId);
            row.setCreateTime(new Date());
            rows.add(row);
        }
        this.saveBatch(rows);
        return true;
    }

    @Override
    public List<Agent> listMembers(Long groupAgentId) {
        List<AgentGroupMember> rows = this.list(new LambdaQueryWrapper<AgentGroupMember>()
                .eq(AgentGroupMember::getGroupAgentId, groupAgentId)
                .orderByAsc(AgentGroupMember::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Agent> byId = new LinkedHashMap<>();
        for (Agent agent : agentMapper.selectBatchIds(
                rows.stream().map(AgentGroupMember::getMemberAgentId).toList())) {
            byId.put(agent.getId(), agent);
        }
        // selectBatchIds 结果顺序不保序，按关联表加入顺序还原；已删除成员查不出自动缺席
        List<Agent> members = new ArrayList<>();
        for (AgentGroupMember row : rows) {
            Agent agent = byId.get(row.getMemberAgentId());
            if (agent != null) {
                members.add(agent);
            }
        }
        return members;
    }

    @Override
    public List<Agent> listUsableMembers(Long groupAgentId, Long userId) {
        // 成员创建时已限定为本人智能体，可用性 = 仍然存在且归属未变
        return listMembers(groupAgentId).stream()
                .filter(agent -> userId != null && userId.equals(agent.getUserId()))
                .toList();
    }

    @Override
    public void deleteByGroupId(Long groupAgentId) {
        this.remove(new LambdaQueryWrapper<AgentGroupMember>()
                .eq(AgentGroupMember::getGroupAgentId, groupAgentId));
    }

    @Override
    public void deleteByMemberId(Long memberAgentId) {
        this.remove(new LambdaQueryWrapper<AgentGroupMember>()
                .eq(AgentGroupMember::getMemberAgentId, memberAgentId));
    }

    @Override
    public void attachMembers(com.mio.ai.resource.model.vo.agent.AgentVO vo) {
        if (vo == null || !Integer.valueOf(AgentTypeEnum.GROUP.getCode()).equals(vo.getType())) {
            return;
        }
        vo.setMembers(buildMembers(vo.getId()));
    }

    @Override
    public List<com.mio.ai.resource.model.vo.agent.AgentGroupMemberVO> buildMembers(Long groupAgentId) {
        List<com.mio.ai.resource.model.vo.agent.AgentGroupMemberVO> members = new ArrayList<>();
        for (Agent member : listMembers(groupAgentId)) {
            com.mio.ai.resource.model.vo.agent.AgentGroupMemberVO brief =
                    new com.mio.ai.resource.model.vo.agent.AgentGroupMemberVO();
            brief.setId(member.getId());
            brief.setName(member.getName());
            brief.setIcon(member.getIcon());
            brief.setAvatar(member.getAvatar());
            members.add(brief);
        }
        return members;
    }
}
