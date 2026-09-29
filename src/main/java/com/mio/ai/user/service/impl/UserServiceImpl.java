package com.mio.ai.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.constant.SystemConstant;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.RedisUtil;
import com.mio.ai.user.mapper.UserMapper;
import com.mio.ai.user.model.dto.UserQueryRequest;
import com.mio.ai.user.model.entity.User;
import com.mio.ai.user.model.enums.UserRoleEnum;
import com.mio.ai.user.model.vo.LoginUserVO;
import com.mio.ai.user.model.vo.UserVO;
import com.mio.ai.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author: Takina
 * @date: 2026/3/28 14:45
 * @description: 针对表【user(用户)】的数据库操作Service实现
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /**
     * BCrypt 编码器：每次加密自带随机盐，强度默认 10
     */
    private static final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder BCRYPT_ENCODER =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    @Autowired
    RedisUtil redisUtil;

    @Autowired
    RedisComponent redisComponent;

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 检查用户账号是否和数据库中已有的重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 密码一定要加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 插入数据到数据库中
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号应不低于4位");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码应不低于8位");
        }
        // 2. 按账号查询用户（密码校验移到内存中进行，以同时兼容历史 MD5 口令与 BCrypt 口令）
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 不存在或密码不匹配，抛异常
        if (user == null || !matchesPassword(userPassword, user.getUserPassword())) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或者密码错误");
        }
        // 3. 历史口令平滑升级：MD5 口令在首次登录成功后自动改存为 BCrypt
        if (!isBcryptHash(user.getUserPassword())) {
            user.setUserPassword(getEncryptPassword(userPassword));
            this.updateById(user);
            log.info("用户 {} 的口令已从 MD5 升级为 BCrypt", userAccount);
        }
        return this.getLoginUserVO(user);
    }

    /**
     * 获取加密后的密码（BCrypt，自带随机盐）
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        return BCRYPT_ENCODER.encode(userPassword);
    }

    /**
     * 校验明文密码与库中口令是否匹配。
     * 兼容两种存储格式：BCrypt（$2a$/$2b$ 开头）与历史 MD5 加盐口令
     */
    @Override
    public boolean matchesPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isBcryptHash(storedPassword)) {
            return BCRYPT_ENCODER.matches(rawPassword, storedPassword);
        }
        return DigestUtils.md5DigestAsHex((SystemConstant.SALT + rawPassword).getBytes()).equals(storedPassword);
    }

    private boolean isBcryptHash(String stored) {
        return stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2b$")
                || stored.startsWith("$2y$"));
    }

    @Override
    public LoginUserVO getLoginUser(HttpServletRequest request) {
        // 获取 token
        String token = request.getHeader("token");

        LoginUserVO currentUser = redisComponent.getUserInfoByToken(token);
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取脱敏类的用户信息
     *
     * @param user 用户
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();

        // 生成Token
        String token = DigestUtils.md5DigestAsHex((user.getId() + RandomUtil.randomString(20)).getBytes(StandardCharsets.UTF_8));
        loginUserVO.setToken(token);

        // 清理旧Token
        String tmpToken = redisUtil.get(SystemConstant.REDIS_KEY_TOKEN_USERID + user.getId());
        if (StringUtils.hasText(tmpToken)) {
            redisUtil.delete(SystemConstant.REDIS_KEY_TOKEN + tmpToken);
        }

        BeanUtil.copyProperties(user, loginUserVO);

        // 缓存新Token
        redisComponent.saveTokenUserInfo(loginUserVO);

        return loginUserVO;
    }

    /**
     * 获得脱敏后的用户信息
     *
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏后的用户列表
     *
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 获取 token
        String token = request.getHeader("token");

        // 判断是否已经登录
        LoginUserVO loginUserVO = redisComponent.getUserInfoByToken(token);
        if (loginUserVO == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }

        // 清理Token
        String tmpToken = redisUtil.get(SystemConstant.REDIS_KEY_TOKEN_USERID + loginUserVO.getId());
        if (StringUtils.hasText(tmpToken)) {
            redisUtil.delete(SystemConstant.REDIS_KEY_TOKEN + tmpToken);
        }
        return true;
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "user_role", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "user_account", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "user_name", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "user_profile", userProfile);
        // sortOrder 可能为 null（前端未传排序方向），用常量在前的比较避免 NPE
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), "ascend".equals(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public boolean updatePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null || StrUtil.hasBlank(oldPassword, newPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        
        if (newPassword.length() < 8 || newPassword.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度为8-20个字符");
        }
        
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        
        if (!matchesPassword(oldPassword, user.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "原密码错误");
        }
        
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码不能与原密码相同");
        }
        
        String newEncryptPassword = getEncryptPassword(newPassword);
        user.setUserPassword(newEncryptPassword);
        
        return this.updateById(user);
    }

    @Override
    public boolean updateAvatar(Long userId, String avatarUrl) {
        if (userId == null || StrUtil.isBlank(avatarUrl)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        
        user.setUserAvatar(avatarUrl);
        return this.updateById(user);
    }

    @Override
    public String getUserNameById(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = this.getById(userId);
        if (user == null) {
            return null;
        }
        return user.getUserName();
    }
}





