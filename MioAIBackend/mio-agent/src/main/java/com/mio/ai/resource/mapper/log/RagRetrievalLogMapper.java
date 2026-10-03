package com.mio.ai.resource.mapper.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.resource.model.entity.RagRetrievalLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: RAG检索日志Mapper接口
 */
@Mapper
public interface RagRetrievalLogMapper extends BaseMapper<RagRetrievalLog> {
}
