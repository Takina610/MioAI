package com.mio.ai.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.user.model.dto.UserQueryRequest;
import com.mio.ai.user.model.entity.User;
import com.mio.ai.user.model.vo.LoginUserVO;
import com.mio.ai.user.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/28 14:43
 * @description: 针对表【user(用户)】的数据库操作 Service
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取加密后的密码
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    LoginUserVO getLoginUser(HttpServletRequest request);

    /**
     * 获得脱敏后的登录用户信息
     *
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获得脱敏后的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获得脱敏后的用户信息列表
     *
     * @param userList
     * @return 脱敏后的用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取查询条件
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    /**
     * 修改用户密码
     *
     * @param userId      用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean updatePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 更新用户头像
     *
     * @param userId      用户ID
     * @param avatarUrl   头像URL
     * @return 是否成功
     */
    boolean updateAvatar(Long userId, String avatarUrl);

    /**
     * 根据用户ID获取用户名
     *
     * @param userId 用户ID
     * @return 用户名
     */
    String getUserNameById(Long userId);
}
