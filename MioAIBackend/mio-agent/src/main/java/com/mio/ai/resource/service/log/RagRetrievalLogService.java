package com.mio.ai.resource.service.log;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.dto.log.RagRetrievalLogQueryRequest;
import com.mio.ai.resource.model.entity.log.RagRetrievalLog;
import com.mio.ai.resource.model.vo.log.RagRetrievalLogVO;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: RAG检索日志服务接口
 */
public interface RagRetrievalLogService extends IService<RagRetrievalLog> {

    /**
     * 记录RAG检索日志
     */
    Long logRetrieval(RagRetrievalLog log);

    /**
     * 分页查询RAG检索日志
     */
    Page<RagRetrievalLogVO> queryRetrievalLogs(RagRetrievalLogQueryRequest request);

    /** 按用户限定查询（业务侧入口：只能看到自己的检索记录） */
    Page<RagRetrievalLogVO> queryRetrievalLogs(RagRetrievalLogQueryRequest request, Long userId);
}
