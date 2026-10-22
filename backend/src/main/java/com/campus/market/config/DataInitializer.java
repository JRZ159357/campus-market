// 文件路径: src/main/java/com/campus/market/config/DataInitializer.java
package com.campus.market.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.market.common.RoleConst;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 初始化后台账号。仅在账号不存在时创建，可重复启动。
 * 初始密码 123456，首次登录后应尽快修改。
 * <p>
 * 升级说明：V4 之后所有角色都可以用「账号 + 密码」登录，
 * 因此后台账号统一标记 register_type = 2（账号密码注册）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String DEFAULT_PASSWORD = "123456";

    /** 注册方式：2 账号密码 */
    private static final Integer REGISTER_TYPE_ACCOUNT = 2;

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createIfAbsent("admin", "系统管理员", RoleConst.ADMIN);
        createIfAbsent("service01", "客服小助手", RoleConst.CUSTOMER_SERVICE);
        createIfAbsent("service02", "客服小助手二号", RoleConst.CUSTOMER_SERVICE);
        markAsAccountRegister();
    }

    private void createIfAbsent(String username, String nickname, String role) {
        Long exists = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRegisterType(REGISTER_TYPE_ACCOUNT);
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(0);
        user.setStudentVerified(0);
        user.setCreditScore(100);
        user.setPublishLimit(0);
        sysUserMapper.insert(user);
        log.info("初始化后台账号: {} / {} ({})", username, DEFAULT_PASSWORD, RoleConst.label(role));
    }

    /**
     * 兼容 V4 之前的存量后台账号：把 register_type 统一修正为 2，
     * 避免历史数据被误判为「微信注册」。
     */
    private void markAsAccountRegister() {
        int updated = sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .set(SysUser::getRegisterType, REGISTER_TYPE_ACCOUNT)
                .isNotNull(SysUser::getUsername)
                .isNull(SysUser::getOpenId)
                .ne(SysUser::getRegisterType, REGISTER_TYPE_ACCOUNT));
        if (updated > 0) {
            log.info("已修正 {} 个后台账号的注册方式标记为账号密码", updated);
        }
    }
}
