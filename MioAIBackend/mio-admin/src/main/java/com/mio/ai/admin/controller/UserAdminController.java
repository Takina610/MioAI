package com.mio.ai.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.exception.ThrowUtils;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.user.model.dto.UserQueryRequest;
import com.mio.ai.user.model.entity.User;
import com.mio.ai.user.model.vo.UserVO;
import com.mio.ai.user.service.UserService;
import com.mio.ai.admin.model.dto.UserAdminCreateRequest;
import com.mio.ai.admin.model.dto.UserAdminUpdateRequest;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员用户管理接口
 */
@Validated
@Slf4j
@RestController
@RequestMapping("/admin/users")
public class UserAdminController {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Resource
    private UserService userService;

    /**
     * 创建用户：走注册链路（账号唯一/密码强度校验、BCrypt 加密、触发建专属 MioBot），再补全资料
     */
    @PostMapping
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Long> createUser(@Valid @RequestBody UserAdminCreateRequest request) {
        long userId = userService.userRegister(request.getUserAccount(), request.getUserPassword(), request.getUserPassword());
        boolean hasExtras = StrUtil.isNotBlank(request.getUserName()) || request.getUserProfile() != null
                || StrUtil.isNotBlank(request.getUserRole()) || request.getUserAvatar() != null;
        if (hasExtras) {
            User update = new User();
            update.setId(userId);
            if (StrUtil.isNotBlank(request.getUserName())) {
                update.setUserName(request.getUserName());
            }
            if (request.getUserProfile() != null) {
                update.setUserProfile(request.getUserProfile());
            }
            if (StrUtil.isNotBlank(request.getUserRole())) {
                ThrowUtils.throwIf(!"user".equals(request.getUserRole()) && !"admin".equals(request.getUserRole()),
                        ErrorCode.PARAMS_ERROR, "角色不合法");
                update.setUserRole(request.getUserRole());
            }
            if (request.getUserAvatar() != null) {
                update.setUserAvatar(request.getUserAvatar());
            }
            userService.updateById(update);
        }
        return ResultUtils.success(userId);
    }

    /**
     * 分页查询用户列表
     */
    @PostMapping("/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Page<UserVO>> listUserByPage(@Valid @RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 50");

        QueryWrapper<User> queryWrapper;
        String keyword = userQueryRequest.getKeyword();
        if (StrUtil.isNotBlank(keyword)) {
            // 关键词搜索：同时匹配用户名或账号（OR 关系）
            queryWrapper = new QueryWrapper<>();
            queryWrapper.and(qw -> qw.like("user_name", keyword).or().like("user_account", keyword));
            String userRole = userQueryRequest.getUserRole();
            if (StrUtil.isNotBlank(userRole)) {
                queryWrapper.eq("user_role", userRole);
            }
            String sortField = userQueryRequest.getSortField();
            String sortOrder = userQueryRequest.getSortOrder();
            queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), "ascend".equals(sortOrder), sortField);
        } else {
            queryWrapper = userService.getQueryWrapper(userQueryRequest);
        }

        Page<User> userPage = userService.page(new Page<>(current, size), queryWrapper);
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

    /**
     * 根据 id 获取用户详情
     */
    @GetMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<UserVO> getUserById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "用户 id 不合法");
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 更新用户信息（管理员）：资料/角色/账号/密码重置
     */
    @PutMapping
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> updateUser(@Valid @RequestBody UserAdminUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        User dbUser = userService.getById(request.getId());
        ThrowUtils.throwIf(dbUser == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        User user = new User();
        user.setId(request.getId());
        if (request.getUserName() != null) {
            user.setUserName(request.getUserName());
        }
        if (request.getUserProfile() != null) {
            user.setUserProfile(request.getUserProfile());
        }
        if (request.getUserRole() != null) {
            user.setUserRole(request.getUserRole());
        }
        if (request.getUserAvatar() != null) {
            user.setUserAvatar(request.getUserAvatar());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        if (StrUtil.isNotBlank(request.getUserAccount()) && !request.getUserAccount().equals(dbUser.getUserAccount())) {
            ThrowUtils.throwIf(request.getUserAccount().length() < 4, ErrorCode.PARAMS_ERROR, "用户账号过短");
            long count = userService.count(new QueryWrapper<User>().eq("user_account", request.getUserAccount()));
            ThrowUtils.throwIf(count > 0, ErrorCode.PARAMS_ERROR, "账号已存在");
            user.setUserAccount(request.getUserAccount());
        }
        if (StrUtil.isNotBlank(request.getUserPassword())) {
            ThrowUtils.throwIf(request.getUserPassword().length() < 8, ErrorCode.PARAMS_ERROR, "密码长度至少 8 位");
            user.setUserPassword(PASSWORD_ENCODER.encode(request.getUserPassword()));
        }
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 删除用户（软删除）
     */
    @DeleteMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> deleteUser(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "用户 id 不合法");
        boolean result = userService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }
}
