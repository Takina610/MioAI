package com.mio.ai.admin.component;

import cn.hutool.core.collection.CollUtil;
import com.mio.ai.resource.service.log.AgentDisplayResolver;
import com.mio.ai.user.model.entity.User;
import com.mio.ai.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台展示信息解析：按 ID 批量解析用户名/头像与智能体名/头像，
 * 供会话、日志、运行数据各管理页把裸 ID 还原为可读展示
 */
@Component
public class AdminNameResolver {

    @Resource
    private UserService userService;

    @Resource
    private AgentDisplayResolver agentDisplayResolver;

    public record UserDisplay(String name, String avatar) {
    }

    public Map<Long, UserDisplay> resolveUsers(Collection<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        Map<Long, UserDisplay> result = new HashMap<>();
        Map<Long, User> users = userService.listByIds(userIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        for (Long id : userIds) {
            User user = users.get(id);
            if (user != null) {
                result.put(id, new UserDisplay(user.getUserName(), user.getUserAvatar()));
            }
        }
        return result;
    }

    public Map<Long, AgentDisplayResolver.Display> resolveAgents(Collection<Long> agentIds) {
        if (CollUtil.isEmpty(agentIds)) {
            return Collections.emptyMap();
        }
        return agentDisplayResolver.resolve(agentIds);
    }
}
