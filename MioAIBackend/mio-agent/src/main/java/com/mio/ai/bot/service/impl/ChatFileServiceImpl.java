package com.mio.ai.bot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mio.ai.bot.mapper.ChatFileMapper;
import com.mio.ai.bot.model.entity.ChatFileDO;
import com.mio.ai.bot.service.ChatFileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatFileServiceImpl implements ChatFileService {

    @Resource
    private ChatFileMapper chatFileMapper;

    @Override
    public void register(String conversationId, Long userId, Long agentId, int kind,
                         String path, String fileName, long size) {
        ChatFileDO row = new ChatFileDO();
        row.setConversationId(conversationId);
        row.setUserId(userId);
        row.setAgentId(agentId);
        row.setKind(kind);
        row.setFileName(fileName);
        row.setPath(path);
        row.setFileSize(size);
        row.setStatus(STATUS_ACTIVE);
        Date now = new Date();
        row.setCreateTime(now);
        row.setUpdateTime(now);
        chatFileMapper.insert(row);
    }

    @Override
    public void registerOutputs(String conversationId, Long userId, Long agentId, List<Map<String, Object>> produced) {
        for (Map<String, Object> item : produced) {
            String path = String.valueOf(item.get("path"));
            String name = item.get("name") == null ? path.substring(path.lastIndexOf('/') + 1)
                    : String.valueOf(item.get("name"));
            long size = item.get("size") instanceof Number n ? n.longValue() : 0;
            try {
                register(conversationId, userId, agentId, KIND_OUTPUT, path, name, size);
            } catch (Exception e) {
                // 单个登记失败不阻断其余文件：目录级删除按目录清理，不依赖行存在
                log.warn("产出文件登记失败, path={}: {}", path, e.getMessage());
            }
        }
    }

    @Override
    public void markPendingByPath(String path) {
        updateStatus(new UpdateWrapper<ChatFileDO>().eq("path", path));
    }

    @Override
    public void markPendingByConversation(String conversationId) {
        updateStatus(new UpdateWrapper<ChatFileDO>().eq("conversation_id", conversationId));
    }

    private void updateStatus(UpdateWrapper<ChatFileDO> wrapper) {
        wrapper.set("status", STATUS_PENDING_DELETE).set("update_time", new Date());
        chatFileMapper.update(null, wrapper);
    }

    @Override
    public void deleteByPath(String path) {
        chatFileMapper.delete(new QueryWrapper<ChatFileDO>().eq("path", path));
    }

    @Override
    public void deleteByConversation(String conversationId) {
        chatFileMapper.delete(new QueryWrapper<ChatFileDO>().eq("conversation_id", conversationId));
    }

    @Override
    public List<ChatFileDO> listPending(int limit) {
        return chatFileMapper.selectList(new QueryWrapper<ChatFileDO>()
                .eq("status", STATUS_PENDING_DELETE)
                .orderByAsc("id")
                .last("LIMIT " + limit));
    }
}
