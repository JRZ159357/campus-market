// 文件路径: src/main/java/com/campus/market/service/impl/AuthServiceImpl.java
package com.campus.market.service.impl;

import com.campus.market.common.BusinessException;
import com.campus.market.common.RedisKey;
import com.campus.market.common.RegisterType;
import com.campus.market.common.ResultCode;
import com.campus.market.common.RoleConst;
import com.campus.market.dto.AccountLoginDTO;
import com.campus.market.dto.AccountRegisterDTO;
import com.campus.market.dto.AdminLoginDTO;
import com.campus.market.dto.BindAccountDTO;
import com.campus.market.dto.ChangePasswordDTO;
import com.campus.market.dto.WxLoginDTO;
import com.campus.market.entity.SysUser;
import com.campus.market.service.AuthService;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.service.UserService;
import com.campus.market.util.JwtUtil;
import com.campus.market.util.WechatApiClient;
import com.campus.market.vo.LoginVO;
import com.campus.market.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 认证业务实现。
 * <p>
 * 登录策略：JWT 只带 userId，签发后写入 Redis 白名单 login:token:{userId}。
 * 这样同一账号再次登录会使旧 token 失效（单端登录），也支持管理员强制下线。
 * <p>
 * 账号体系：微信登录与账号密码登录共用 sys_user 与同一套 token；
 * 微信用户可在绑定账号密码后使用两种方式登录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 账号规则：4-20 位字母或数字 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,20}$");

    private static final int NICKNAME_MAX = 20;

    private final UserService userService;
    private final WechatApiClient wechatApiClient;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final DashboardCacheService dashboardCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO wxLogin(WxLoginDTO dto) {
        String openId = wechatApiClient.code2OpenId(dto.getCode());
        SysUser user = userService.getByOpenId(openId);

        if (user == null) {
            user = new SysUser();
            user.setOpenId(openId);
            user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : "微信用户");
            user.setAvatar(dto.getAvatar());
            user.setRegisterType(RegisterType.WECHAT);
            user.setRole(RoleConst.USER);
            user.setStatus(0);
            user.setStudentVerified(0);
            user.setCreditScore(100);
            user.setPublishLimit(0);
            user.setLastLoginTime(LocalDateTime.now());
            userService.save(user);
            // 新用户注册：仪表盘的总用户数、角色分布、信誉分分布都要跟着变
            dashboardCacheService.evictOnUserChange();
            log.info("新用户注册: userId={}, openId={}", user.getId(), openId);
        } else {
            if (Integer.valueOf(1).equals(user.getStatus())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "账号已被封禁，请联系客服");
            }
            SysUser update = new SysUser();
            update.setId(user.getId());
            update.setLastLoginTime(LocalDateTime.now());
            if (StringUtils.hasText(dto.getNickname())) {
                update.setNickname(dto.getNickname());
            }
            if (StringUtils.hasText(dto.getAvatar())) {
                update.setAvatar(dto.getAvatar());
            }
            userService.updateById(update);
            userService.evictCache(user.getId());
            user = userService.getById(user.getId());
        }
        return issueToken(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(AccountRegisterDTO dto) {
        String username = normalizeUsername(dto.getUsername());
        checkUsernameFormat(username);

        Long exists = userService.lambdaQuery()
                .eq(SysUser::getUsername, username)
                .count();
        if (exists != null && exists > 0) {
            throw new BusinessException("该账号已被注册，请更换");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRegisterType(RegisterType.ACCOUNT);
        user.setNickname(StringUtils.hasText(dto.getNickname())
                ? truncate(dto.getNickname().trim(), NICKNAME_MAX)
                : "用户" + username);
        user.setRole(RoleConst.USER);
        user.setStatus(0);
        user.setStudentVerified(0);
        user.setCreditScore(100);
        user.setPublishLimit(0);
        user.setLastLoginTime(LocalDateTime.now());
        try {
            userService.save(user);
        } catch (DuplicateKeyException e) {
            // 并发注册时唯一索引兜底
            throw new BusinessException("该账号已被注册，请更换");
        }
        log.info("账号注册成功: userId={}, username={}", user.getId(), username);
        // 新用户注册：仪表盘的总用户数、角色分布、信誉分分布都要跟着变
        dashboardCacheService.evictOnUserChange();
        return issueToken(user);
    }

    @Override
    public LoginVO accountLogin(AccountLoginDTO dto) {
        SysUser user = verifyAccount(dto.getUsername(), dto.getPassword());
        touchLogin(user.getId());
        return issueToken(user);
    }

    @Override
    public LoginVO adminLogin(AdminLoginDTO dto) {
        SysUser user = verifyAccount(dto.getUsername(), dto.getPassword());
        if (!RoleConst.ADMIN.equals(user.getRole())
                && !RoleConst.CUSTOMER_SERVICE.equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该账号无管理端访问权限");
        }
        touchLogin(user.getId());
        return issueToken(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindAccount(Long userId, BindAccountDTO dto) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (StringUtils.hasText(user.getUsername())) {
            throw new BusinessException("当前账号已绑定登录账号，如需更换请联系管理员");
        }

        String username = normalizeUsername(dto.getUsername());
        checkUsernameFormat(username);
        Long exists = userService.lambdaQuery()
                .eq(SysUser::getUsername, username)
                .count();
        if (exists != null && exists > 0) {
            throw new BusinessException("该账号已被使用，请更换");
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setUsername(username);
        update.setPassword(passwordEncoder.encode(dto.getPassword()));
        // 注册方式保留微信，仅补充账号密码，便于个人中心展示“双登录方式”
        if (user.getRegisterType() == null) {
            update.setRegisterType(RegisterType.WECHAT);
        }
        try {
            userService.updateById(update);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该账号已被使用，请更换");
        }
        userService.evictCache(userId);
        log.info("用户 {} 绑定账号密码: {}", userId, username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            throw new BusinessException("当前账号尚未设置密码，请先绑定账号密码");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "原密码不正确");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新密码不能与原密码相同");
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userService.updateById(update);
        userService.evictCache(userId);
        log.info("用户 {} 修改密码成功", userId);
    }

    @Override
    public boolean isUsernameAvailable(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        String normalized = normalizeUsername(username);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            return false;
        }
        Long exists = userService.lambdaQuery()
                .eq(SysUser::getUsername, normalized)
                .count();
        return exists == null || exists == 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindPhone(Long userId, String code) {
        String phone = wechatApiClient.getPhoneNumber(code);

        Long exists = userService.lambdaQuery()
                .eq(SysUser::getPhone, phone)
                .ne(SysUser::getId, userId)
                .count();
        if (exists != null && exists > 0) {
            throw new BusinessException("该手机号已被其他账号绑定");
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPhone(phone);
        userService.updateById(update);
        userService.evictCache(userId);
    }

    @Override
    public void logout(Long userId) {
        redis.delete(RedisKey.LOGIN_TOKEN + userId);
        userService.evictCache(userId);
    }

    @Override
    public UserInfoVO me(Long userId) {
        return userService.getProfile(userId);
    }

    /**
     * 校验账号密码并返回用户实体（不做角色限制）。
     * 账号不存在与密码错误使用同一提示，避免账号枚举。
     */
    private SysUser verifyAccount(String rawUsername, String rawPassword) {
        if (!StringUtils.hasText(rawUsername) || !StringUtils.hasText(rawPassword)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号和密码不能为空");
        }
        SysUser user = userService.lambdaQuery()
                .eq(SysUser::getUsername, normalizeUsername(rawUsername))
                .one();
        if (user == null || !StringUtils.hasText(user.getPassword())
                || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号或密码错误");
        }
        if (Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被封禁，请联系客服");
        }
        return user;
    }

    /**
     * 更新最后登录时间并清理用户缓存，保证角色/状态变更立即生效。
     */
    private void touchLogin(Long userId) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setLastLoginTime(LocalDateTime.now());
        userService.updateById(update);
        userService.evictCache(userId);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    private void checkUsernameFormat(String username) {
        if (!StringUtils.hasText(username) || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号需为4-20位字母或数字");
        }
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * 签发 token 并写入 Redis 白名单。
     */
    private LoginVO issueToken(SysUser user) {
        String token = jwtUtil.create(user.getId());
        redis.opsForValue().set(
                RedisKey.LOGIN_TOKEN + user.getId(),
                token,
                Duration.ofSeconds(jwtUtil.getExpireSeconds()));

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setRole(user.getRole());
        vo.setRoleLabel(RoleConst.label(user.getRole()));
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setStudentVerified(user.getStudentVerified());
        vo.setPublishLimit(user.getPublishLimit());
        return vo;
    }
}
