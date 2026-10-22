// 文件路径: src/main/java/com/campus/market/service/impl/AdminAccountServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BusinessException;
import com.campus.market.common.OpLogModule;
import com.campus.market.common.RedisKey;
import com.campus.market.common.ResultCode;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.OpLog;
import com.campus.market.dto.AdminAccountPasswordDTO;
import com.campus.market.dto.AdminAccountQueryDTO;
import com.campus.market.dto.AdminAccountSaveDTO;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.service.AdminAccountService;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.service.UserService;
import com.campus.market.vo.AdminAccountVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 后台账号管理实现。
 * <p>
 * 这个类里绝大部分代码不是 CRUD，而是<b>护栏</b>。后台账号出问题的
 * 后果与普通数据不同：删错一个商品还能恢复，把最后一个管理员禁用掉
 * 就意味着谁都进不了管理端，只能去数据库里改。因此每个写操作都先问
 * 三个问题：
 * <ol>
 *   <li>这个操作会不会让我自己失去登录能力？（禁止操作自己）</li>
 *   <li>这个操作会不会让系统再也无人可管？（至少留一个可用管理员）</li>
 *   <li>这个操作会不会污染历史审计？(账号字段不可改，删除时改名释放唯一键)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl implements AdminAccountService {

    /** 后台可管理的角色白名单。普通用户与认证学生不在此列表内 */
    private static final List<String> MANAGED_ROLES =
            List.of(RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE);

    /** 状态：正常 */
    private static final int STATUS_NORMAL = 0;

    /** 状态：禁用 */
    private static final int STATUS_DISABLED = 1;

    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final SysUserMapper sysUserMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final DashboardCacheService dashboardCacheService;
    private final StringRedisTemplate redis;

    @Override
    public IPage<AdminAccountVO> page(AdminAccountQueryDTO query, Long operatorId) {
        AdminAccountQueryDTO q = query == null ? new AdminAccountQueryDTO() : query;
        long pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1L : q.getPageNum();
        long pageSize = q.getPageSize() == null || q.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(q.getPageSize(), MAX_PAGE_SIZE);

        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                // 角色白名单是硬条件，不随筛选参数变化：
                // 即使前端传 role=user，也不会把小程序用户查出来
                .in(SysUser::getRole, MANAGED_ROLES)
                .eq(StringUtils.hasText(q.getRole()), SysUser::getRole, q.getRole())
                .eq(q.getStatus() != null, SysUser::getStatus, q.getStatus())
                .and(StringUtils.hasText(q.getKeyword()), w -> w
                        .like(SysUser::getUsername, q.getKeyword())
                        .or()
                        .like(SysUser::getNickname, q.getKeyword()))
                // 管理员排前面，同角色按创建时间倒序（新加的账号一眼能看到）
                .orderByAsc(SysUser::getRole)
                .orderByDesc(SysUser::getCreateTime)
                .orderByDesc(SysUser::getId);

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return page.convert(user -> toVO(user, operatorId));
    }

    @Override
    @OpLog(module = OpLogModule.ACCOUNT, action = OpLogModule.ACTION_CREATE, desc = "新增后台账号")
    @Transactional(rollbackFor = Exception.class)
    public Long create(AdminAccountSaveDTO dto) {
        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号不能为空");
        }
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新增账号时必须设置密码");
        }
        String role = validRole(dto.getRole());

        Long exists = sysUserMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException("账号「" + username + "」已被占用");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        // 注册方式 2 = 账号密码，与 DataInitializer 创建的初始账号保持一致
        user.setRegisterType(2);
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname().trim() : username);
        user.setRole(role);
        user.setStatus(dto.getStatus() != null && dto.getStatus() == STATUS_DISABLED
                ? STATUS_DISABLED : STATUS_NORMAL);
        user.setStudentVerified(0);
        // 后台账号不发商品，额度给 0：万一角色被误改成 student，
        // 也不会突然多出 10 件商品的发布额度
        user.setCreditScore(100);
        user.setPublishLimit(0);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("账号「" + username + "」已被占用");
        }

        dashboardCacheService.evictOnUserChange();
        log.info("新增后台账号: id={}, username={}, role={}", user.getId(), username, role);
        return user.getId();
    }

    @Override
    @OpLog(module = OpLogModule.ACCOUNT, action = OpLogModule.ACTION_UPDATE, targetIndex = 1,
            desc = "编辑后台账号")
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AdminAccountSaveDTO dto, Long operatorId) {
        SysUser target = requireManagedAccount(id);
        String role = validRole(dto.getRole());
        int status = dto.getStatus() != null && dto.getStatus() == STATUS_DISABLED
                ? STATUS_DISABLED : STATUS_NORMAL;

        // 护栏 1：不能改自己的角色。
        // 把自己从 admin 降成 customer_service 会立刻失去管理端写权限，
        // 如果又是最后一个管理员，整个系统就没人能管了——而当事人
        // 点下按钮之前完全不会意识到这一点。
        if (id.equals(operatorId) && !role.equals(target.getRole())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能修改自己的角色");
        }
        // 护栏 2：不能禁用自己
        if (id.equals(operatorId) && status == STATUS_DISABLED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能禁用自己的账号");
        }
        // 护栏 3：把最后一个可用管理员降级 / 禁用，等于锁死系统
        if (RoleConst.ADMIN.equals(target.getRole())
                && (!RoleConst.ADMIN.equals(role) || status == STATUS_DISABLED)) {
            ensureOtherAdminExists(id, "系统至少需要保留一个可用的管理员账号");
        }

        SysUser update = new SysUser();
        update.setId(id);
        update.setNickname(dto.getNickname().trim());
        update.setRole(role);
        update.setStatus(status);
        // 编辑接口不处理密码：改密码走独立的 resetPassword，
        // 那条路径有单独的日志与「不能改自己」的判断
        sysUserMapper.updateById(update);

        // 角色与状态都参与鉴权，缓存必须立刻失效，
        // 否则被降级的管理员在缓存过期前（30 分钟）仍能写数据
        userService.evictCache(id);
        dashboardCacheService.evictOnUserChange();
        log.info("编辑后台账号: id={}, role={}, status={}, operator={}", id, role, status, operatorId);
    }

    @Override
    @OpLog(module = OpLogModule.ACCOUNT, action = OpLogModule.ACTION_RESET_PASSWORD, targetIndex = 1,
            desc = "重置后台账号密码")
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, AdminAccountPasswordDTO dto, Long operatorId) {
        requireManagedAccount(id);
        if (id.equals(operatorId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "请通过右上角「修改密码」重置自己的密码（需要校验旧密码）");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(dto.getPassword()));
        sysUserMapper.updateById(update);

        /*
         * 密码已更换，旧登录态必须立即作废。
         *
         * 这里必须显式删 Redis 里的 token，而不能只清用户缓存：
         * 本系统的鉴权是「JWT 里只有 userId + Redis 白名单校验」，
         * 每个请求并不会拿密码去比对——也就是说，改完密码后旧 token
         * 在过期前依然畅通。而「重置密码」这个动作的典型场景恰恰是
         * 账号可能已被他人使用，如果旧 token 还能用，这次重置就毫无意义。
         */
        try {
            redis.delete(RedisKey.LOGIN_TOKEN + id);
        } catch (Exception e) {
            // 删 token 失败不能把密码回滚：密码已经改对了，
            // 只是旧会话没踢掉，记 warn 让人工介入
            log.warn("重置密码后清除登录态失败 userId={}：{}", id, e.getMessage());
        }
        userService.evictCache(id);
        log.info("重置后台账号密码: id={}, operator={}", id, operatorId);
    }

    @Override
    @OpLog(module = OpLogModule.ACCOUNT, action = OpLogModule.ACTION_STATUS, targetIndex = 1,
            desc = "启用/禁用后台账号")
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status, Long operatorId) {
        if (status == null || (status != STATUS_NORMAL && status != STATUS_DISABLED)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能是 0（正常）或 1（禁用）");
        }
        SysUser target = requireManagedAccount(id);
        if (Integer.valueOf(status).equals(target.getStatus())) {
            throw new BusinessException(status == STATUS_DISABLED ? "该账号已被禁用" : "该账号已是正常状态");
        }
        if (id.equals(operatorId) && status == STATUS_DISABLED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能禁用自己的账号");
        }
        if (status == STATUS_DISABLED && RoleConst.ADMIN.equals(target.getRole())) {
            ensureOtherAdminExists(id, "系统至少需要保留一个可用的管理员账号");
        }

        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        sysUserMapper.updateById(update);
        userService.evictCache(id);
        dashboardCacheService.evictOnUserChange();
        log.info("{}后台账号: id={}, operator={}", status == STATUS_DISABLED ? "禁用" : "启用", id, operatorId);
    }

    @Override
    @OpLog(module = OpLogModule.ACCOUNT, action = OpLogModule.ACTION_DELETE, targetIndex = 1,
            desc = "删除后台账号")
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long operatorId) {
        SysUser target = requireManagedAccount(id);
        if (id.equals(operatorId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能删除自己的账号");
        }
        if (RoleConst.ADMIN.equals(target.getRole())) {
            ensureOtherAdminExists(id, "系统至少需要保留一个管理员账号");
        }

        /*
         * 逻辑删除 + 释放账号名。
         *
         * sys_user 上 uk_username 是唯一索引，而逻辑删除只把 deleted 置 1，
         * 行还在表里。若不同时把 username 改掉，这个账号名就永久占用了——
         * 运营常见的「删了重建同名账号」会直接撞 1052 报错，而且从界面上
         * 看这个账号已经不存在了，运维根本想不到是唯一索引在挡。
         *
         * 改名格式带上 id：既保证唯一（同一个用户名删两次会有两个不同的 id），
         * 也让 DBA 一眼看出这是被删除的账号而不是真有人叫这个名字。
         */
        String freed = target.getUsername() == null ? "deleted" : target.getUsername();
        String archived = truncate(freed, 40) + "#del" + id;

        // 先改账号名再删：@TableLogic 的字段不会随 updateById 一起更新
        // （MP 在 SET 里会跳过逻辑删除列），所以必须分成两步。
        // 顺序不能反：先删再改名的话，逻辑删除后这行在 MP 的视角里
        // 已经不存在了，updateById 会带 deleted=0 的 where 条件，更新 0 行。
        sysUserMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, id)
                .set(SysUser::getUsername, archived));
        sysUserMapper.deleteById(id);

        // 删除后该账号的 token 也不该继续有效：清缓存后，
        // 拦截器回源查库会拿到 deleted=1（MP 自动过滤）→ 查不到人 → 401
        userService.evictCache(id);
        dashboardCacheService.evictOnUserChange();
        log.info("删除后台账号: id={}, username={}, operator={}", id, target.getUsername(), operatorId);
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 取一个「可管理的后台账号」，不存在或不是后台角色一律拒绝。
     * <p>
     * 不能只判存在：接口是 {@code /api/admin/account/{id}}，
     * 如果传入一个普通用户的 id，直接改角色就等于绕过了用户管理模块
     * 的角色分配逻辑（那里还有发布额度回收等联动），
     * 所以这里必须校验目标本身属于后台角色。
     */
    private SysUser requireManagedAccount(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "账号不存在或已被删除");
        }
        if (!MANAGED_ROLES.contains(user.getRole())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该用户不是后台账号，请到用户管理中操作");
        }
        return user;
    }

    /**
     * 校验除了 {@code excludeId} 之外，还存在「正常状态的」管理员。
     * <p>
     * 条件必须同时包含 role=admin 与 status=0：
     * 只数 role=admin 会把已被禁用的管理员算进去，
     * 结果就是「系统里有两个管理员，但两个都进不去」。
     */
    private void ensureOtherAdminExists(Long excludeId, String message) {
        Long others = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, RoleConst.ADMIN)
                .eq(SysUser::getStatus, STATUS_NORMAL)
                .ne(SysUser::getId, excludeId));
        if (others == null || others == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
    }

    private String validRole(String role) {
        String normalized = role == null ? "" : role.trim();
        if (!MANAGED_ROLES.contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "角色只能是 admin（管理员）或 customer_service（客服）");
        }
        return normalized;
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private AdminAccountVO toVO(SysUser user, Long operatorId) {
        AdminAccountVO vo = new AdminAccountVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setRoleLabel(RoleConst.label(user.getRole()));
        vo.setStatus(user.getStatus());
        vo.setStatusLabel(Integer.valueOf(STATUS_DISABLED).equals(user.getStatus()) ? "禁用" : "正常");
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        vo.setSelf(user.getId().equals(operatorId));
        return vo;
    }
}
