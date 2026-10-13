package com.mio.ai.resource.model.dto.agent;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 智能体左轨手动排序请求：拖动结束后的最终顺序（服务端按序重写 sort_order）
 */
@Data
public class AgentReorderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "排序列表不能为空")
    private List<Long> orderedIds;
}
