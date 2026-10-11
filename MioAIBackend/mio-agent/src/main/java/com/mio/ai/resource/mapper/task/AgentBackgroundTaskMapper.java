package com.mio.ai.resource.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.resource.model.entity.AgentBackgroundTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 后台任务持久化 Mapper
 */
@Mapper
public interface AgentBackgroundTaskMapper extends BaseMapper<AgentBackgroundTask> {

    /** 库内最大任务序号（b_&lt;n&gt; 的 n）：重启后续接注册序号，避免 ID 撞车 */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(task_id, 3) AS UNSIGNED)), 0) FROM agent_background_task")
    long selectMaxTaskSeq();
}
