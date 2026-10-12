package com.mio.ai.resource.service.agent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.dto.agent.AgentAddRequest;
import com.mio.ai.resource.model.dto.agent.AgentQueryRequest;
import com.mio.ai.resource.model.dto.agent.AgentUpdateRequest;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.model.vo.agent.AgentDetailVO;
import com.mio.ai.resource.model.vo.agent.AgentVO;
import com.mio.ai.resource.model.vo.agent.AgentVersionVO;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体服务接口
 */
public interface AgentService extends IService<Agent> {

    Long addAgent(AgentAddRequest request, Long userId);

    boolean updateAgent(AgentUpdateRequest request, Long userId);

    boolean deleteAgent(Long id, Long userId);

    AgentVO getAgentById(Long id);

    /**
     * 带用户上下文读取：内置智能体（MioBot）合并当前用户的个性化覆盖（人设/开场白/图标/温度）
     */
    AgentVO getAgentById(Long id, Long userId);

    AgentDetailVO getAgentDetailById(Long id, Long userId);

    Page<AgentVO> queryAgents(AgentQueryRequest request);

    /**
     * 广场列表：自定义公开智能体 + MioBot 槽位。
     * MioBot 按当前用户装配——登录用户已发布自己的 MioBot（agent_user_config.is_public=1）
     * 时显示自己的（带个性化覆盖与本人署名，置顶），否则不显示；游客显示官方内置 MioBot。
     */
    Page<AgentVO> getPublicAgents(long current, long size, Long userId);

    /**
     * 发布：保存配置并生成版本快照，agent.version 指向最新版本；返回新版本号
     */
    String publishAgent(Long agentId, AgentUpdateRequest request, Long userId);

    /**
     * 版本历史（新版本在前，含配置快照与绑定资源名称）
     */
    List<AgentVersionVO> listVersions(Long agentId, Long userId);

    /**
     * 回滚：把指定版本的配置快照恢复到智能体上，并生成一条新版本（rollbackFrom 标记来源）；返回新版本号
     */
    String rollbackToVersion(Long agentId, Long versionId, Long userId);

    /**
     * 从广场模板复制一份为当前用户的草稿智能体（含可读的资源绑定），返回新智能体ID
     */
    Long forkAgent(Long templateId, Long userId);
}
