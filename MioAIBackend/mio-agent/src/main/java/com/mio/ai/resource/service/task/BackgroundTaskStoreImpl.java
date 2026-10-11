package com.mio.ai.resource.service.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.framework.zagent.task.BackgroundTaskStore;
import com.mio.ai.framework.zagent.task.BackgroundTasks;
import com.mio.ai.resource.mapper.task.AgentBackgroundTaskMapper;
import com.mio.ai.resource.model.entity.AgentBackgroundTask;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 后台任务持久化实现（MySQL）：登记/完结即 upsert；TaskOutput 内存未命中经 find 重连。
 * 输出只保尾部（text 列上限保护），完整输出在沙箱 .out 文件。
 */
@Slf4j
public class BackgroundTaskStoreImpl implements BackgroundTaskStore {

    /** 落库输出上限（字符，保尾部） */
    private static final int OUTPUT_CAP = 64_000;

    private final AgentBackgroundTaskMapper mapper;

    public BackgroundTaskStoreImpl(AgentBackgroundTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsert(BackgroundTasks.Task task) {
        AgentBackgroundTask row = mapper.selectById(task.id);
        boolean insert = row == null;
        if (insert) {
            row = new AgentBackgroundTask();
            row.setTaskId(task.id);
            row.setChatId(task.chatId);
            row.setType(task.type);
            row.setDescription(task.description);
            row.setStartedAt(task.startedAt);
            row.setCreateTime(new Date());
        }
        row.setStatus(task.status);
        row.setExitCode(task.exitCode);
        row.setOutput(tail(task.output));
        row.setDetached(task.detached ? 1 : 0);
        row.setSandboxPid(task.sandboxPid);
        row.setFinishedAt(task.finishedAt > 0 ? task.finishedAt : null);
        row.setDeadlineAt(task.deadlineAt);
        row.setUpdateTime(new Date());
        if (insert) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
    }

    @Override
    public Optional<BackgroundTasks.Task> find(String taskId) {
        AgentBackgroundTask row = mapper.selectById(taskId);
        return row == null ? Optional.empty() : Optional.of(toTask(row));
    }

    @Override
    public List<BackgroundTasks.Task> findByChatAndStatus(String chatId, String status) {
        return mapper.selectList(new LambdaQueryWrapper<AgentBackgroundTask>()
                        .eq(AgentBackgroundTask::getChatId, chatId)
                        .eq(AgentBackgroundTask::getStatus, status)
                        .orderByAsc(AgentBackgroundTask::getStartedAt))
                .stream().map(BackgroundTaskStoreImpl::toTask).toList();
    }

    @Override
    public List<BackgroundTasks.Task> findByStatus(String status) {
        return mapper.selectList(new LambdaQueryWrapper<AgentBackgroundTask>()
                        .eq(AgentBackgroundTask::getStatus, status)
                        .orderByAsc(AgentBackgroundTask::getStartedAt))
                .stream().map(BackgroundTaskStoreImpl::toTask).toList();
    }

    @Override
    public long maxTaskSeq() {
        return mapper.selectMaxTaskSeq();
    }

    private static BackgroundTasks.Task toTask(AgentBackgroundTask row) {
        return BackgroundTasks.Task.rehydrated(row.getTaskId(), row.getType(), row.getDescription(),
                row.getChatId(), row.getStatus(), row.getExitCode(), row.getOutput(),
                row.getStartedAt() == null ? 0 : row.getStartedAt(),
                row.getFinishedAt() == null ? 0 : row.getFinishedAt(),
                row.getDetached() != null && row.getDetached() == 1,
                row.getSandboxPid(),
                row.getDeadlineAt() == null ? 0 : row.getDeadlineAt());
    }

    private static String tail(String output) {
        if (output == null) {
            return "";
        }
        return output.length() > OUTPUT_CAP
                ? output.substring(output.length() - OUTPUT_CAP) : output;
    }
}
