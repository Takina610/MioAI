package com.mio.ai.admin.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理后台记录级展示包装：data 为记录本体（各表实体），
 * userName/agentName 等为批量解析出的展示信息，解析不到为 null
 */
@Data
public class AdminRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Object data;

    private String userName;

    private String userAvatar;

    private String agentName;

    private String agentAvatar;

    /** bot-icon 配置 JSON */
    private String agentIcon;

    /** 群成员场景：群本体展示名 */
    private String groupName;
}
