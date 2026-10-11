package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 后台任务持久化（zagent BackgroundTasks 的存储层）：
 * run_in_background bash（沙箱脱离式）与子代理任务的登记/完结快照，
 * 后端重启后 TaskOutput 经此重连沙箱任务、读历史任务输出。
 */
@Data
@TableName("agent_background_task")
public class AgentBackgroundTask implements Serializable {

    /** 任务ID（b_&lt;seq&gt;，序号跨重启续接） */
    @TableId(value = "task_id", type = IdType.INPUT)
    private String taskId;

    private String chatId;

    /** bash / agent */
    private String type;

    private String description;

    /** running / completed / failed / stopped / interrupted */
    private String status;

    private Integer exitCode;

    /** 输出尾部（写入时截断） */
    private String output;

    /** 1 = 沙箱脱离式（重启后进程仍活，可探针重连） */
    private Integer detached;

    private Long sandboxPid;

    private Long startedAt;

    private Long finishedAt;

    /** 超时截止（epoch ms，0 = 不限时） */
    private Long deadlineAt;

    private Date createTime;

    private Date updateTime;
}
