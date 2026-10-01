package com.mio.ai.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.user.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/3/28 14:46
 * @description: 针对表【user(用户)】的数据库操作Mapper
 */

@Mapper
public interface UserMapper extends BaseMapper<User> {

}
