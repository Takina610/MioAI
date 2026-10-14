package com.mio.ai.resource.service.agent.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.bot.agent.MioBot;
import com.mio.ai.bot.controller.MioBotController;
import com.mio.ai.bot.mapper.ChatConversationDOMapper;
import com.mio.ai.bot.model.entity.ChatConversationDO;
import com.mio.ai.bot.service.ChatCascadeDeleteService;
import com.mio.ai.framework.zagent.memory.AgentMemory;
import com.mio.ai.framework.zagent.tools.SandboxFs;
import com.mio.ai.framework.zagent.tools.ToolsetFactory;
import com.mio.ai.resource.mapper.agent.AgentKnowledgeMapper;
import com.mio.ai.resource.mapper.agent.AgentMcpMapper;
import com.mio.ai.resource.mapper.agent.AgentMapper;
import com.mio.ai.resource.mapper.knowledge.KnowledgeBaseMapper;
import com.mio.ai.resource.mapper.mcp.McpToolMapper;
import com.mio.ai.resource.mapper.task.AgentBackgroundTaskMapper;
import com.mio.ai.resource.model.dto.agent.AgentAddRequest;
import com.mio.ai.resource.model.dto.agent.AgentQueryRequest;
import com.mio.ai.resource.model.dto.agent.AgentUpdateRequest;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.model.entity.AgentBackgroundTask;
import com.mio.ai.resource.model.entity.AgentKnowledge;
import com.mio.ai.resource.model.entity.AgentMcp;
import com.mio.ai.resource.model.entity.AgentSkill;
import com.mio.ai.resource.model.entity.AgentUserConfig;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.model.entity.McpTool;
import com.mio.ai.resource.model.enums.AgentStatusEnum;
import com.mio.ai.resource.model.enums.AgentTypeEnum;
import com.mio.ai.resource.model.enums.KnowledgeBaseStatusEnum;
import com.mio.ai.resource.model.enums.McpToolStatusEnum;
import com.mio.ai.resource.model.vo.agent.AgentDetailVO;
import com.mio.ai.resource.model.vo.agent.AgentVO;
import com.mio.ai.resource.model.vo.agent.AgentVersionVO;
import com.mio.ai.resource.model.vo.knowledge.KnowledgeBaseVO;
import com.mio.ai.resource.model.vo.mcp.McpToolVO;
import com.mio.ai.resource.model.vo.skill.SkillVO;
import com.mio.ai.resource.service.skill.SkillService;
import com.mio.ai.resource.service.agent.AgentKnowledgeService;
import com.mio.ai.resource.service.agent.AgentMcpService;
import com.mio.ai.resource.service.agent.AgentService;
import com.mio.ai.resource.service.agent.AgentSkillService;
import com.mio.ai.resource.service.agent.AgentUserConfigService;
import com.mio.ai.resource.service.agent.AgentVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import com.mio.ai.resource.model.entity.Skill;
import java.util.Map;
import java.util.Objects;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体服务实现类
 */
@Service
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent> implements AgentService {

    /** SLF4J 专用记录器：父类 ServiceImpl 的 log 是 MyBatis Log（不支持占位符），不能占用同名 */
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(AgentServiceImpl.class);


    @Autowired
    private AgentMcpMapper agentMcpMapper;

    @Autowired
    private AgentKnowledgeMapper agentKnowledgeMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private McpToolMapper mcpToolMapper;

    @Autowired
    private ChatConversationDOMapper chatConversationDOMapper;

    @Autowired
    private AgentBackgroundTaskMapper agentBackgroundTaskMapper;

    @Autowired
    private ChatCascadeDeleteService chatCascadeDeleteService;

    @Autowired
    private AgentMcpService agentMcpService;

    @Autowired
    private AgentKnowledgeService agentKnowledgeService;

    @Autowired
    private AgentSkillService agentSkillService;

    @Autowired
    private ToolsetFactory toolsetFactory;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private SkillService skillService;

    @Autowired
    private AgentVersionService agentVersionService;

    @Autowired
    private com.mio.ai.resource.service.agent.AgentGroupService agentGroupService;

    @Autowired
    private AgentUserConfigService agentUserConfigService;

    /** 广场 MioBot 槽位署名：发布者用户名 */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.mio.ai.user.service.UserService userService;

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

        // 忽略 null：编辑弹窗只传名称/描述/图标，不允许把未传字段（提示词/公开状态）静默置空
        BeanUtil.copyProperties(request, agent,
                CopyOptions.create().setIgnoreNullValue(true).setIgnoreError(true));
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
        // 内置智能体（user_id 为空，如 MioBot）与通用型智能体不可删除；个人专属 MioBot 同样不可删除
        if (agent.getUserId() == null || Objects.equals(agent.getType(), AgentTypeEnum.GENERAL.getCode())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "内置智能体不可删除");
        }
        if (Objects.equals(agent.getType(), AgentTypeEnum.MIOBOT.getCode())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "MioBot 不可删除");
        }
        if (!agent.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除该智能体");
        }
        return deleteAgentCascade(agent.getId());
    }

    /**
     * 删除智能体的完整级联：该智能体全部会话（会话行/对话记忆/消息与版本/沙箱暂存目录）
     * → 会话后台任务台账 → 知识库/MCP/技能三张绑定表 → 沙箱跨会话记忆目录 → 智能体本体（逻辑删除）。
     * 沙箱目录删除等外部操作尽力而为；工具调用/用量等审计日志按约定持久保留，不随删除清空。
     */
    private boolean deleteAgentCascade(Long agentId) {
        List<ChatConversationDO> chats = chatConversationDOMapper.selectList(
                new LambdaQueryWrapper<ChatConversationDO>().eq(ChatConversationDO::getAgentId, agentId));

        // 有执行中轮次先拒绝：与删除单个会话同一规则，删后落库会把已删数据写回
        for (ChatConversationDO chat : chats) {
            if (MioBotController.hasActiveTurn(chat.getConversationId())) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "该智能体有会话正在执行中，请等待完成后再删除");
            }
        }

        for (ChatConversationDO chat : chats) {
            try {
                chatCascadeDeleteService.deleteConversationCompletely(chat.getConversationId());
            } catch (Exception e) {
                // 单会话清理失败不阻断删除：台账置待删/兜底扫描等机制会收敛残留
                LOGGER.warn("级联删除会话失败, agentId={}, chatId={}: {}",
                        agentId, chat.getConversationId(), e.getMessage());
            }
        }

        if (!chats.isEmpty()) {
            agentBackgroundTaskMapper.delete(new LambdaQueryWrapper<AgentBackgroundTask>()
                    .in(AgentBackgroundTask::getChatId,
                            chats.stream().map(ChatConversationDO::getConversationId).toList()));
        }

        // 解除知识库/MCP/技能绑定
        agentMcpService.deleteByAgentId(agentId);
        agentKnowledgeService.deleteByAgentId(agentId);
        agentSkillService.deleteByAgentId(agentId);

        // 群聊关系双向清理：本智能体是群则解散，是某群成员则从群中移除
        agentGroupService.deleteByGroupId(agentId);
        agentGroupService.deleteByMemberId(agentId);

        deleteSandboxMemory(agentId);

        return this.removeById(agentId);
    }

    /** 沙箱跨会话记忆目录 memory/&lt;agentId&gt;/：尽力而为，沙箱未启用或删除失败不影响主体删除 */
    private void deleteSandboxMemory(Long agentId) {
        try {
            SandboxFs fs = toolsetFactory.sandboxFs();
            if (fs == null) {
                return;
            }
            fs.exec("rm -rf " + quote(AgentMemory.directory(agentId)), 30, null);
        } catch (Exception e) {
            LOGGER.warn("清理智能体沙箱记忆失败, agentId={}: {}", agentId, e.getMessage());
        }
    }

    private static String quote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
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
    public AgentVO getAgentById(Long id, Long userId) {
        AgentVO vo = getAgentById(id);
        applyUserOverride(vo, id, userId);
        return vo;
    }

    /**
     * 内置智能体（user_id 为空，如 MioBot）按用户合并个性化覆盖（人设/图标）；
     * 自定义智能体没有用户级配置，原样返回
     */
    private void applyUserOverride(AgentVO vo, Long agentId, Long userId) {
        if (userId == null) {
            return;
        }
        Agent agent = this.getById(agentId);
        if (agent == null || agent.getUserId() != null) {
            return;
        }
        AgentUserConfig override = agentUserConfigService.getOverride(userId, agentId);
        if (override == null) {
            return;
        }
        if (override.getIcon() != null) {
            vo.setIcon(override.getIcon());
        }
    }

    @Override
    public Page<AgentVO> queryAgents(AgentQueryRequest request) {
        Page<Agent> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(request.getName()), Agent::getName, request.getName())
                .eq(request.getUserId() != null, Agent::getUserId, request.getUserId())
                .eq(request.getType() != null, Agent::getType, request.getType())
                .ne(Agent::getType, AgentTypeEnum.GENERAL.getCode())
                // 置顶分组在前，组内按手动排序，未排序项按最近更新回退
                .orderByDesc(Agent::getPinned)
                .orderByAsc(Agent::getSortOrder)
                .orderByDesc(Agent::getUpdateTime);
        Page<Agent> agentPage = this.page(page, wrapper);
        Page<AgentVO> voPage = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
        voPage.setRecords(agentPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public boolean pinAgent(Long id, Long userId, boolean pinned) {
        Agent agent = checkAgentOwner(id, userId);
        // 移到目标分组顶部：sort_order = 该分组当前最小值 - 1
        Agent top = this.getOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getUserId, userId)
                .eq(Agent::getPinned, pinned ? 1 : 0)
                .orderByAsc(Agent::getSortOrder)
                .last("LIMIT 1"), false);
        long nextSort = (top == null || top.getSortOrder() == null ? 0L : top.getSortOrder()) - 1;
        Agent row = new Agent();
        row.setId(agent.getId());
        row.setPinned(pinned ? 1 : 0);
        row.setSortOrder(nextSort);
        return this.updateById(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reorderAgents(Long userId, List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return true;
        }
        // 只重排本人名下的智能体；最小字段更新，不触碰 update_time
        java.util.Set<Long> mine = this.list(new LambdaQueryWrapper<Agent>()
                        .eq(Agent::getUserId, userId)
                        .in(Agent::getId, orderedIds))
                .stream().map(Agent::getId).collect(java.util.stream.Collectors.toSet());
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            if (!mine.contains(id)) {
                continue;
            }
            Agent row = new Agent();
            row.setId(id);
            row.setSortOrder((long) i);
            this.updateById(row);
        }
        return true;
    }

    @Override
    public Page<AgentVO> getPublicAgents(long current, long size, Long userId) {
        Page<Agent> page = new Page<>(current, size);
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        // 内置智能体不进公共查询：广场的 MioBot 槽位按当前用户单独装配；
        // 个人专属 MioBot（type=3）同样不进广场
        wrapper.eq(Agent::getIsPublic, 1)
                .eq(Agent::getStatus, AgentStatusEnum.PUBLISHED.getCode())
                .isNotNull(Agent::getUserId)
                .ne(Agent::getType, AgentTypeEnum.MIOBOT.getCode())
                .orderByDesc(Agent::getCreateTime);
        Page<Agent> agentPage = this.page(page, wrapper);
        Agent builtin = this.getById(MioBot.AGENT_ID);
        // 官方 MioBot 槽位独占该名字：用户自建的同名智能体不再作为第二个 MioBot 出现
        String officialName = builtin != null && builtin.getName() != null ? builtin.getName() : "";
        Page<AgentVO> voPage = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
        List<AgentVO> records = new ArrayList<>();
        appendMioBotSlot(records, userId);
        agentPage.getRecords().stream()
                .filter(a -> !officialName.equals(a.getName()))
                .map(this::convertToVO)
                .forEach(records::add);
        voPage.setRecords(records);
        return voPage;
    }

    /**
     * 广场的 MioBot 槽位：每个用户都相当于拥有自己的 MioBot。
     * 登录用户发布过自己的 MioBot（配置行 is_public=1）→ 显示自己的（个性化覆盖 + 本人署名，置顶）；
     * 未发布/未个性化 → 回退官方内置 MioBot——广场永远有且只有一个 MioBot；
     * 游客 → 显示官方内置 MioBot。
     */
    private void appendMioBotSlot(List<AgentVO> records, Long userId) {
        Agent builtin = this.getById(MioBot.AGENT_ID);
        if (builtin == null) {
            return;
        }
        AgentVO slot = null;
        if (userId != null) {
            com.mio.ai.resource.model.entity.AgentUserConfig config =
                    agentUserConfigService.getOverride(userId, builtin.getId());
            if (config != null && Integer.valueOf(1).equals(config.getIsPublic())) {
                slot = convertToVO(builtin);
                // 槽位归属当前用户：userId 供前端判定"自己发布的"（隐藏模板复制按钮）
                slot.setUserId(userId);
                applyUserOverride(slot, builtin.getId(), userId);
                slot.setUserName(userService.getUserNameById(userId));
            }
        }
        if (slot == null && Integer.valueOf(1).equals(builtin.getIsPublic())
                && AgentStatusEnum.PUBLISHED.getCode() == builtin.getStatus()) {
            // 未发布过个人 MioBot（含游客）：回退官方内置位
            slot = convertToVO(builtin);
        }
        if (slot != null) {
            records.add(slot);
        }
    }

    private AgentVO convertToVO(Agent agent) {
        AgentVO vo = new AgentVO();
        BeanUtil.copyProperties(agent, vo, CopyOptions.create().setIgnoreError(true));
        AgentTypeEnum typeEnum = AgentTypeEnum.getByCode(agent.getType());
        vo.setTypeDesc(typeEnum != null ? typeEnum.getDesc() : "未知");
        AgentStatusEnum statusEnum = AgentStatusEnum.getByCode(agent.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        // 群聊附带成员概要（非群聊零开销）：前端堆叠群头像渲染
        if (Integer.valueOf(AgentTypeEnum.GROUP.getCode()).equals(agent.getType())) {
            agentGroupService.attachMembers(vo);
        }
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
        // 内置智能体（user_id 为空）对所有用户可见，避免空指针
        if (userId != null && agent.getUserId() != null && !agent.getUserId().equals(userId)
                && !(Integer.valueOf(1).equals(agent.getIsPublic())
                     && AgentStatusEnum.PUBLISHED.getCode() == agent.getStatus())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看该智能体");
        }

        AgentDetailVO vo = new AgentDetailVO();
        BeanUtil.copyProperties(agent, vo, CopyOptions.create().setIgnoreError(true));
        AgentTypeEnum typeEnum = AgentTypeEnum.getByCode(agent.getType());
        vo.setTypeDesc(typeEnum != null ? typeEnum.getDesc() : "未知");
        AgentStatusEnum statusEnum = AgentStatusEnum.getByCode(agent.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        // 群聊附带成员概要（堆叠群头像渲染）
        if (Integer.valueOf(AgentTypeEnum.GROUP.getCode()).equals(agent.getType())) {
            vo.setMembers(agentGroupService.buildMembers(id));
        }

        // 内置智能体：标记当前用户是否有个性化覆盖（聊天页图标按"我的配置"生效）
        if (agent.getUserId() == null && userId != null) {
            AgentUserConfig override = agentUserConfigService.getOverride(userId, id);
            vo.setPersonalized(override != null);
            if (override != null && override.getIcon() != null) {
                vo.setIcon(override.getIcon());
            }
        }

        vo.setKnowledgeBases(getKnowledgeBasesByAgentId(id));
        vo.setMcpTools(getMcpToolsByAgentId(id));
        vo.setSkills(getSkillsByAgentId(id));

        return vo;
    }

    private List<SkillVO> getSkillsByAgentId(Long agentId) {
        List<Skill> skills = skillService.getEnabledSkillsForAgent(agentId);
        List<SkillVO> voList = new ArrayList<>();
        for (Skill skill : skills) {
            SkillVO vo = new SkillVO();
            // files 实体侧是 JSON 字符串、VO 侧是列表，BeanUtil 强转会炸，忽略转换错误
            BeanUtil.copyProperties(skill, vo, CopyOptions.create().setIgnoreError(true));
            voList.add(vo);
        }
        return voList;
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
    @Transactional(rollbackFor = Exception.class)
    public String publishAgent(Long agentId, AgentUpdateRequest request, Long userId) {
        Agent agent = checkAgentOwner(agentId, userId);
        if (Integer.valueOf(AgentTypeEnum.GROUP.getCode()).equals(agent.getType())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "群聊智能体不支持发布");
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
        if (request.getIcon() != null) {
            agent.setIcon(request.getIcon());
        }
        if (request.getSystemPrompt() != null) {
            agent.setSystemPrompt(request.getSystemPrompt());
        }
        if (request.getIsPublic() != null) {
            agent.setIsPublic(request.getIsPublic());
        }

        agent.setStatus(AgentStatusEnum.PUBLISHED.getCode());
        // 版本快照：发布即留档，agent.version 指向最新版本
        String bindingsJson = bindingsSnapshot(agentId);
        com.mio.ai.resource.model.entity.AgentVersion snapshot =
                agentVersionService.createSnapshot(agent, bindingsJson, userId, null);
        agent.setVersion(snapshot.getVersion());
        this.updateById(agent);
        return snapshot.getVersion();
    }

    @Override
    public List<AgentVersionVO> listVersions(Long agentId, Long userId) {
        checkAgentOwner(agentId, userId);
        return agentVersionService.listVersions(agentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rollbackToVersion(Long agentId, Long versionId, Long userId) {
        Agent agent = checkAgentOwner(agentId, userId);
        com.mio.ai.resource.model.entity.AgentVersion target = agentVersionService.getById(versionId);
        if (target == null || !agentId.equals(target.getAgentId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "版本不存在");
        }

        agent.setName(target.getName());
        agent.setDescription(target.getDescription());
        agent.setAvatar(target.getAvatar());
        agent.setIcon(target.getIcon());
        agent.setSystemPrompt(target.getSystemPrompt());
        agent.setStatus(AgentStatusEnum.PUBLISHED.getCode());

        // 资源绑定恢复到版本快照（此后被删除的资源跳过），再以恢复后的状态生成新版本留档
        String bindingsJson = target.getBindingsJson();
        restoreBindings(agentId, bindingsJson);
        com.mio.ai.resource.model.entity.AgentVersion snapshot =
                agentVersionService.createSnapshot(agent, bindingsJson, userId, target.getVersion());
        agent.setVersion(snapshot.getVersion());
        this.updateById(agent);
        return snapshot.getVersion();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long forkAgent(Long templateId, Long userId) {
        Agent template = this.getById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        if (Integer.valueOf(AgentTypeEnum.GROUP.getCode()).equals(template.getType())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "群聊智能体不支持复制");
        }

        Agent copy = new Agent();
        copy.setUserId(userId);
        copy.setName((template.getName() == null ? "未命名智能体" : template.getName()) + " 副本");
        copy.setDescription(template.getDescription());
        copy.setAvatar(template.getAvatar());
        copy.setIcon(template.getIcon());
        copy.setSystemPrompt(template.getSystemPrompt());
        copy.setType(AgentTypeEnum.CUSTOM.getCode());
        copy.setStatus(AgentStatusEnum.DRAFT.getCode());
        copy.setIsPublic(0);
        copy.setUsageCount(0);
        this.save(copy);

        forkBindings(templateId, copy.getId(), userId);
        return copy.getId();
    }

    /**
     * 复制模板的资源绑定：仅复制当前用户可读的目标（本人所有或公开），
     * 私有资源跳过不报错——副本以用户自己的资源为准
     */
    private void forkBindings(Long templateId, Long newAgentId, Long userId) {
        LambdaQueryWrapper<AgentKnowledge> kbWrapper = new LambdaQueryWrapper<AgentKnowledge>()
                .eq(AgentKnowledge::getAgentId, templateId);
        for (AgentKnowledge ak : agentKnowledgeMapper.selectList(kbWrapper)) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(ak.getKbId());
            if (kb == null || !readable(kb.getUserId(), kb.getIsPublic(), userId)) {
                continue;
            }
            AgentKnowledge row = new AgentKnowledge();
            row.setAgentId(newAgentId);
            row.setKbId(ak.getKbId());
            row.setRetrievalConfig(ak.getRetrievalConfig());
            row.setEnabled(ak.getEnabled());
            agentKnowledgeService.save(row);
        }

        LambdaQueryWrapper<AgentMcp> mcpWrapper = new LambdaQueryWrapper<AgentMcp>()
                .eq(AgentMcp::getAgentId, templateId);
        for (AgentMcp am : agentMcpMapper.selectList(mcpWrapper)) {
            McpTool mcp = mcpToolMapper.selectById(am.getMcpId());
            if (mcp == null || !readable(mcp.getUserId(), mcp.getIsPublic(), userId)) {
                continue;
            }
            AgentMcp row = new AgentMcp();
            row.setAgentId(newAgentId);
            row.setMcpId(am.getMcpId());
            row.setConfigOverride(am.getConfigOverride());
            row.setEnabled(am.getEnabled());
            agentMcpService.save(row);
        }

        LambdaQueryWrapper<AgentSkill> skillWrapper = new LambdaQueryWrapper<AgentSkill>()
                .eq(AgentSkill::getAgentId, templateId);
        for (AgentSkill as : agentSkillService.list(skillWrapper)) {
            Skill skill = skillService.getById(as.getSkillId());
            if (skill == null || !readable(skill.getUserId(), skill.getIsPublic(), userId)) {
                continue;
            }
            AgentSkill row = new AgentSkill();
            row.setAgentId(newAgentId);
            row.setSkillId(as.getSkillId());
            row.setEnabled(as.getEnabled());
            agentSkillService.save(row);
        }
    }

    private static boolean readable(Long ownerId, Integer isPublic, Long userId) {
        return (ownerId != null && ownerId.equals(userId)) || Integer.valueOf(1).equals(isPublic);
    }

    /** 资源绑定快照：{"kbs":[{kbId,retrievalConfig,enabled}],"mcps":[{mcpId,configOverride,enabled}],"skills":[{skillId,enabled}]} */
    private String bindingsSnapshot(Long agentId) {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Map<String, Object>> kbs = new ArrayList<>();
        for (AgentKnowledge ak : agentKnowledgeMapper.selectList(
                new LambdaQueryWrapper<AgentKnowledge>().eq(AgentKnowledge::getAgentId, agentId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kbId", ak.getKbId());
            row.put("retrievalConfig", ak.getRetrievalConfig());
            row.put("enabled", ak.getEnabled());
            kbs.add(row);
        }
        List<Map<String, Object>> mcps = new ArrayList<>();
        for (AgentMcp am : agentMcpMapper.selectList(
                new LambdaQueryWrapper<AgentMcp>().eq(AgentMcp::getAgentId, agentId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mcpId", am.getMcpId());
            row.put("configOverride", am.getConfigOverride());
            row.put("enabled", am.getEnabled());
            mcps.add(row);
        }
        List<Map<String, Object>> skills = new ArrayList<>();
        for (AgentSkill as : agentSkillService.list(
                new LambdaQueryWrapper<AgentSkill>().eq(AgentSkill::getAgentId, agentId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skillId", as.getSkillId());
            row.put("enabled", as.getEnabled());
            skills.add(row);
        }
        root.put("kbs", kbs);
        root.put("mcps", mcps);
        root.put("skills", skills);
        return JacksonUtil.writeValueAsString(root);
    }

    /** 回滚时按快照重建资源绑定：整删整插，快照里此后已被删除的资源跳过 */
    private void restoreBindings(Long agentId, String bindingsJson) {
        agentMcpService.deleteByAgentId(agentId);
        agentKnowledgeService.deleteByAgentId(agentId);
        agentSkillService.deleteByAgentId(agentId);
        if (bindingsJson == null || bindingsJson.isBlank()) {
            return;
        }
        try {
            Map<?, ?> root = JacksonUtil.readValue(bindingsJson, Map.class);
            if (root == null) {
                return;
            }
            for (Object item : asList(root.get("kbs"))) {
                Map<?, ?> row = (Map<?, ?>) item;
                Long kbId = toLong(row.get("kbId"));
                if (kbId == null || knowledgeBaseMapper.selectById(kbId) == null) {
                    continue;
                }
                AgentKnowledge entity = new AgentKnowledge();
                entity.setAgentId(agentId);
                entity.setKbId(kbId);
                entity.setRetrievalConfig(asString(row.get("retrievalConfig")));
                entity.setEnabled(toInt(row.get("enabled"), 1));
                agentKnowledgeService.save(entity);
            }
            for (Object item : asList(root.get("mcps"))) {
                Map<?, ?> row = (Map<?, ?>) item;
                Long mcpId = toLong(row.get("mcpId"));
                if (mcpId == null || mcpToolMapper.selectById(mcpId) == null) {
                    continue;
                }
                AgentMcp entity = new AgentMcp();
                entity.setAgentId(agentId);
                entity.setMcpId(mcpId);
                entity.setConfigOverride(asString(row.get("configOverride")));
                entity.setEnabled(toInt(row.get("enabled"), 1));
                agentMcpService.save(entity);
            }
            for (Object item : asList(root.get("skills"))) {
                Map<?, ?> row = (Map<?, ?>) item;
                Long skillId = toLong(row.get("skillId"));
                if (skillId == null || skillService.getById(skillId) == null) {
                    continue;
                }
                AgentSkill entity = new AgentSkill();
                entity.setAgentId(agentId);
                entity.setSkillId(skillId);
                entity.setEnabled(toInt(row.get("enabled"), 1));
                agentSkillService.save(entity);
            }
        } catch (Exception ignored) {
            // 快照损坏按空绑定处理，不阻断回滚主体
        }
    }

    private static List<?> asList(Object raw) {
        return raw instanceof List<?> list ? list : List.of();
    }

    private static Long toLong(Object raw) {
        return raw == null ? null : Long.valueOf(String.valueOf(raw));
    }

    private static Integer toInt(Object raw, int fallback) {
        return raw == null ? fallback : Integer.valueOf(String.valueOf(raw));
    }

    private static String asString(Object raw) {
        return raw == null ? null : String.valueOf(raw);
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

    /**
     * 每个用户一份专属 MioBot（type=3）：注册时创建；历史用户/误删场景在下次调用时补建。
     * 初始配置复制内置 MioBot（id=1），行为一致且可自由编辑
     */
    @Override
    public Long ensurePersonalMioBot(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Agent existing = this.getOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getUserId, userId)
                .eq(Agent::getType, AgentTypeEnum.MIOBOT.getCode())
                .orderByDesc(Agent::getId)
                .last("LIMIT 1"), false);
        if (existing != null) {
            return existing.getId();
        }
        Agent builtin = this.getById(MioBot.AGENT_ID);
        if (builtin == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "内置 MioBot 不存在，无法创建个人 MioBot");
        }
        Agent copy = new Agent();
        copy.setUserId(userId);
        copy.setName(builtin.getName());
        copy.setDescription(builtin.getDescription());
        copy.setType(AgentTypeEnum.MIOBOT.getCode());
        copy.setStatus(AgentStatusEnum.PUBLISHED.getCode());
        copy.setIsPublic(0);
        copy.setUsageCount(0);
        copy.setVersion(builtin.getVersion());
        // 用户在内置 MioBot 上保存过的个性化（人设/图标）带入副本，体验连续
        com.mio.ai.resource.model.entity.AgentUserConfig override =
                agentUserConfigService.getOverride(userId, MioBot.AGENT_ID);
        copy.setSystemPrompt(override != null && override.getSystemPrompt() != null
                && !override.getSystemPrompt().isBlank() ? override.getSystemPrompt() : builtin.getSystemPrompt());
        copy.setIcon(override != null && override.getIcon() != null ? override.getIcon() : builtin.getIcon());
        this.save(copy);
        LOGGER.info("为用户 {} 创建个人 MioBot, agentId={}", userId, copy.getId());
        return copy.getId();
    }
}
