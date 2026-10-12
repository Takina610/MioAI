package com.mio.ai.resource.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.resource.mapper.agent.AgentVersionMapper;
import com.mio.ai.resource.mapper.knowledge.KnowledgeBaseMapper;
import com.mio.ai.resource.mapper.mcp.McpToolMapper;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.model.entity.AgentVersion;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.model.entity.McpTool;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.resource.model.vo.agent.AgentVersionVO;
import com.mio.ai.resource.service.agent.AgentVersionService;
import com.mio.ai.resource.service.skill.SkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 智能体发布版本快照服务实现
 */
@Service
public class AgentVersionServiceImpl extends ServiceImpl<AgentVersionMapper, AgentVersion>
        implements AgentVersionService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private McpToolMapper mcpToolMapper;

    @Autowired
    @Lazy
    private SkillService skillService;

    @Override
    public AgentVersion createSnapshot(Agent agent, String bindingsJson, Long userId, String rollbackFrom) {
        AgentVersion snapshot = new AgentVersion();
        snapshot.setAgentId(agent.getId());
        snapshot.setVersion(nextVersion(agent.getId()));
        snapshot.setName(agent.getName());
        snapshot.setDescription(agent.getDescription());
        snapshot.setAvatar(agent.getAvatar());
        snapshot.setIcon(agent.getIcon());
        snapshot.setSystemPrompt(agent.getSystemPrompt());
        snapshot.setBindingsJson(bindingsJson);
        snapshot.setRollbackFrom(rollbackFrom);
        snapshot.setUserId(userId);
        this.save(snapshot);
        return snapshot;
    }

    /** 版本号递增：首个版本 v1.0，其后取最大版本 v{major}.{minor} → minor+1，逢 10 进位（v1.9 → v2.0） */
    private String nextVersion(Long agentId) {
        List<AgentVersion> existing = this.list(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentId, agentId).orderByDesc(AgentVersion::getId).last("LIMIT 1"));
        if (existing.isEmpty()) {
            return "v1.0";
        }
        int major = 1;
        int minor = 0;
        String current = existing.get(0).getVersion();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("v?(\\d+)\\.(\\d+)").matcher(current == null ? "" : current);
        if (matcher.find()) {
            major = Integer.parseInt(matcher.group(1));
            minor = Integer.parseInt(matcher.group(2));
        }
        minor++;
        if (minor > 9) {
            major++;
            minor = 0;
        }
        return "v" + major + "." + minor;
    }

    @Override
    public List<AgentVersionVO> listVersions(Long agentId) {
        List<AgentVersion> versions = this.list(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentId, agentId).orderByDesc(AgentVersion::getId));
        Map<String, String> nameCache = resolveTargetNames(versions);
        return versions.stream().map(v -> toVO(v, nameCache)).toList();
    }

    private AgentVersionVO toVO(AgentVersion version, Map<String, String> nameCache) {
        AgentVersionVO vo = new AgentVersionVO();
        vo.setId(version.getId());
        vo.setAgentId(version.getAgentId());
        vo.setVersion(version.getVersion());
        vo.setName(version.getName());
        vo.setDescription(version.getDescription());
        vo.setAvatar(version.getAvatar());
        vo.setIcon(version.getIcon());
        vo.setSystemPrompt(version.getSystemPrompt());
        vo.setRollbackFrom(version.getRollbackFrom());
        vo.setCreateTime(version.getCreateTime());
        vo.setBindings(parseBindings(version.getBindingsJson(), nameCache));
        return vo;
    }

    /** 跨版本聚合解析资源名称：三次批量查询覆盖全部版本引用的目标 */
    private Map<String, String> resolveTargetNames(List<AgentVersion> versions) {
        Map<String, String> cache = new HashMap<>();
        List<Long> kbIds = new ArrayList<>();
        List<Long> mcpIds = new ArrayList<>();
        List<Long> skillIds = new ArrayList<>();
        for (AgentVersion version : versions) {
            for (AgentVersionVO.VersionBinding binding : parseBindings(version.getBindingsJson(), null)) {
                switch (binding.getType()) {
                    case "kb" -> kbIds.add(binding.getTargetId());
                    case "mcp" -> mcpIds.add(binding.getTargetId());
                    case "skill" -> skillIds.add(binding.getTargetId());
                    default -> { }
                }
            }
        }
        if (!kbIds.isEmpty()) {
            for (KnowledgeBase kb : knowledgeBaseMapper.selectBatchIds(kbIds)) {
                cache.put("kb:" + kb.getId(), kb.getName());
            }
        }
        if (!mcpIds.isEmpty()) {
            for (McpTool mcp : mcpToolMapper.selectBatchIds(mcpIds)) {
                cache.put("mcp:" + mcp.getId(), mcp.getName());
            }
        }
        if (!skillIds.isEmpty()) {
            for (Skill skill : skillService.listByIds(skillIds)) {
                cache.put("skill:" + skill.getId(), skill.getName());
            }
        }
        return cache;
    }

    /**
     * 绑定快照JSON解析为绑定列表；nameCache 非空时填充资源名称与删除标记
     * （格式：{"kbs":[{"kbId":1,"retrievalConfig":null,"enabled":1}],"mcps":[...],"skills":[...]}）
     */
    private static List<AgentVersionVO.VersionBinding> parseBindings(String bindingsJson,
                                                                     Map<String, String> nameCache) {
        List<AgentVersionVO.VersionBinding> result = new ArrayList<>();
        if (bindingsJson == null || bindingsJson.isBlank()) {
            return result;
        }
        try {
            Map<?, ?> root = JacksonUtil.readValue(bindingsJson, Map.class);
            if (root == null) {
                return result;
            }
            collectBindings(root.get("kbs"), "kb", "kbId", result, nameCache);
            collectBindings(root.get("mcps"), "mcp", "mcpId", result, nameCache);
            collectBindings(root.get("skills"), "skill", "skillId", result, nameCache);
        } catch (Exception ignored) {
            // 快照解析失败按无绑定处理（老版本无快照字段）
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void collectBindings(Object rawList, String type, String idKey,
                                        List<AgentVersionVO.VersionBinding> result,
                                        Map<String, String> nameCache) {
        if (!(rawList instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object idValue = ((Map<String, Object>) map).get(idKey);
            if (idValue == null) {
                continue;
            }
            AgentVersionVO.VersionBinding binding = new AgentVersionVO.VersionBinding();
            binding.setType(type);
            binding.setTargetId(Long.valueOf(String.valueOf(idValue)));
            if (nameCache != null) {
                String name = nameCache.get(type + ":" + binding.getTargetId());
                binding.setName(name);
                binding.setTargetDeleted(name == null);
            }
            result.add(binding);
        }
    }
}
