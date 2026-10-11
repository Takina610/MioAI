package com.mio.ai.bot.service;

import com.mio.ai.bot.model.entity.ChatFileDO;

import java.util.List;
import java.util.Map;

/**
 * 会话文件台账：沙箱暂存文件的登记与删除联动。
 * 删除语义：先置 status=待删除 再异步删沙箱，沙箱删成功才删行——
 * 行是删除意图的持久凭证，重试耗尽后由兜底扫描（ChatFilePendingSweeper）重入队。
 */
public interface ChatFileService {

    int KIND_UPLOAD = 1;
    int KIND_OUTPUT = 2;

    int STATUS_ACTIVE = 1;
    int STATUS_PENDING_DELETE = 2;

    /** 登记一个暂存文件（上传成功 / 产出收集时调用） */
    void register(String conversationId, Long userId, Long agentId, int kind, String path, String fileName, long size);

    /** 批量登记 Agent 产出文件（produced 为 listDir 形态的 {path,name,size}） */
    void registerOutputs(String conversationId, Long userId, Long agentId, List<Map<String, Object>> produced);

    /** 撤回单个附件：置待删（行不存在视作成功，兼容历史未登记文件） */
    void markPendingByPath(String path);

    /** 删除会话：该会话全部登记行置待删 */
    void markPendingByConversation(String conversationId);

    /** 沙箱文件已删除后的台账清理（幂等） */
    void deleteByPath(String path);

    /** 会话目录已删除后的台账清理（幂等） */
    void deleteByConversation(String conversationId);

    /** 待删行快照（兜底扫描重入队用），按 id 升序限量 */
    List<ChatFileDO> listPending(int limit);
}
