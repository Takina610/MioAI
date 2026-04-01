package com.mio.ai.customagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.customagent.model.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库Mapper接口
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
