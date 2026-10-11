package com.mio.ai.bot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 会话文件登记：沙箱暂存文件（uploads/&lt;chatId&gt;/ 与 outputs/&lt;chatId&gt;/）的持久台账。
 * <p>引用此前只存在于消息 blocks JSON 里，会话删除后沙箱文件无从定位；
 * 登记后删除会话按台账清理，待删失败的行由兜底扫描重入队——孤儿判断以本表为准。
 */
@Data
@TableName("chat_file")
public class ChatFileDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    /** 游客为空 */
    private Long userId;

    /** 产出文件归属智能体；上传发生在发消息前，此时未知为空 */
    private Long agentId;

    /** 来源：1-用户上传 2-Agent产出（取值见 ChatFileService 常量） */
    private Integer kind;

    /** 原始文件名 */
    private String fileName;

    /** 沙箱工作区内相对路径（uploads|outputs/<chatId>/<file>） */
    private String path;

    private Long fileSize;

    /** 状态：1-正常 2-待删除（取值见 ChatFileService 常量） */
    private Integer status;

    private Date createTime;

    private Date updateTime;
}
