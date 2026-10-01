package com.mio.ai.customagent.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.customagent.model.entity.Document;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档Mapper接口
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
