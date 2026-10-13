package com.mio.ai.resource.model.dto.agent;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 编辑群聊请求：改名 + 整体替换成员（前端从当前成员列表里移除后提交剩余名单，至少保留 2 个）
 */
@Data
public class AgentGroupUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "群聊ID不能为空")
    private Long id;

    @NotBlank(message = "群聊名称不能为空")
    @Size(max = 32, message = "群聊名称长度不能超过32")
    @XssClean(mode = "strict")
    private String name;

    @NotEmpty(message = "请至少保留 2 个成员")
    private List<Long> memberAgentIds;
}
