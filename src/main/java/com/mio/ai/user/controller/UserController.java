package com.mio.ai.user.controller;

/**
 * @author: Takina
 * @date: 2026/3/28 14:50
 * @description:
 */

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.common.FileType;
import com.mio.ai.common.constant.SystemConstant;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.exception.ThrowUtils;
import com.mio.ai.common.utils.R2Util;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.RedisUtil;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.user.model.dto.*;
import com.mio.ai.user.model.entity.User;
import com.mio.ai.user.model.vo.LoginUserVO;
import com.mio.ai.user.model.vo.UserVO;
import com.mio.ai.user.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisComponent redisComponent;

    @Autowired
    private R2Util r2Util;


    /**
     * 用户注册
     */
    @PostMapping("/register")
    @LogInfo
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @LogInfo
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    @LogInfo
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        return ResultUtils.success(userService.getLoginUser(request));
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    @LogInfo
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 创建用户
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        // 默认密码
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        // 插入数据库
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<User> getUserById(@PathVariable long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     */
    @GetMapping("/get/vo/{id}")
    @LogInfo
    public BaseResponse<UserVO> getUserVOById(@PathVariable long id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户
     */
    @PostMapping("/delete/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> deleteUser(@PathVariable Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean isRemoved = userService.removeById(id);

        // 清理 Token
        String tmpToken = redisUtil.get(SystemConstant.REDIS_KEY_TOKEN_USERID + id);
        if (StringUtils.hasText(tmpToken)) {
            redisUtil.delete(SystemConstant.REDIS_KEY_TOKEN + tmpToken);
        }

        return ResultUtils.success(isRemoved);
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 获取 token
        String token = request.getHeader("token");
        LoginUserVO loginUserVO = redisComponent.getUserInfoByToken(token);
        if (null != loginUserVO) {
            // 更新缓存信息
            BeanUtils.copyProperties(userUpdateRequest, loginUserVO);
            redisComponent.saveTokenUserInfo(loginUserVO);
        }

        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

    /**
     * 修改密码
     */
    @PostMapping("/password/update")
    @LogInfo
    public BaseResponse<Boolean> updatePassword(@RequestBody UserPasswordUpdateRequest passwordUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(passwordUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        LoginUserVO loginUser = userService.getLoginUser(request);
        String oldPassword = passwordUpdateRequest.getOldPassword();
        String newPassword = passwordUpdateRequest.getNewPassword();
        String confirmPassword = passwordUpdateRequest.getConfirmPassword();
        
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        
        boolean result = userService.updatePassword(loginUser.getId(), oldPassword, newPassword);
        return ResultUtils.success(result);
    }

    /**
     * 上传头像
     */
    @PostMapping("/avatar/upload")
    @LogInfo
    public BaseResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        LoginUserVO loginUser = userService.getLoginUser(request);
        
        try {
            String avatarUrl = r2Util.uploadFile(file, FileType.USER_AVATAR, String.valueOf(loginUser.getId()));
            userService.updateAvatar(loginUser.getId(), avatarUrl);
            
            String token = request.getHeader("token");
            LoginUserVO cachedUser = redisComponent.getUserInfoByToken(token);
            if (cachedUser != null) {
                cachedUser.setUserAvatar(avatarUrl);
                redisComponent.saveTokenUserInfo(cachedUser);
            }
            
            return ResultUtils.success(avatarUrl);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传头像失败: " + e.getMessage());
        }
    }

    /**
     * 更新当前用户信息
     */
    @PostMapping("/update/my")
    @LogInfo
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        if (userUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LoginUserVO loginUser = userService.getLoginUser(request);
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        user.setId(loginUser.getId());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        String token = request.getHeader("token");
        LoginUserVO cachedUser = redisComponent.getUserInfoByToken(token);
        if (cachedUser != null) {
            if (userUpdateRequest.getUserName() != null) {
                cachedUser.setUserName(userUpdateRequest.getUserName());
            }
            if (userUpdateRequest.getUserProfile() != null) {
                cachedUser.setUserProfile(userUpdateRequest.getUserProfile());
            }
            redisComponent.saveTokenUserInfo(cachedUser);
        }

        return ResultUtils.success(true);
    }
}

