// 文件路径: src/main/java/com/campus/market/service/impl/AdminUserServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.CreditLevel;
import com.campus.market.common.CreditRule;
import com.campus.market.common.Gender;
import com.campus.market.common.OpLogModule;
import com.campus.market.common.annotation.OpLog;
import com.campus.market.common.ResultCode;
import com.campus.market.common.RoleConst;
import com.campus.market.common.UserRule;
import com.campus.market.dto.AdminUserQueryDTO;
import com.campus.market.dto.AdminUserRoleDTO;
import com.campus.market.dto.AdminUserStatusDTO;
import com.campus.market.dto.StudentAuthAuditDTO;
import com.campus.market.dto.UserProfileUpdateDTO;
import com.campus.market.entity.StudentAuth;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.ReviewMapper;
import com.campus.market.mapper.StudentAuthMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.service.AdminUserService;
import com.campus.market.service.CreditService;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.service.NoticeService;
import com.campus.market.service.OrderService;
import com.campus.market.service.ProductService;
import com.campus.market.service.StudentAuthService;
import com.campus.market.service.UserService;
import com.campus.market.service.support.ReviewAssembler;
import com.campus.market.vo.AdminUserDetailVO;
import com.campus.market.vo.AdminUserVO;
import com.campus.market.vo.CreditLogVO;
import com.campus.market.vo.OrderListVO;
import com.campus.market.vo.ProductListVO;
import com.campus.market.vo.ReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 管理端用户管理服务实现。
 * <p>
 * 与用户自助操作的关键区别：这里改的是「别人的数据」，
 * 因此每一步都要做「不能对自己做」「不能做危险的事」这类额外校验。
 * <ul>
 *   <li>管理员不能封禁/降级自己，否则会把自己锁在门外；</li>
 *   <li>封禁用户时同步下架其全部在架商品——人已经不能交易了，
 *       商品还挂在平台上被点是直接的资损入口；</li>
 *   <li>降级为普通用户时回收发布额度，否则普通用户会残留 20 件的商品额度。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
        implements AdminUserService {

    /** 详情弹窗里各列表最多展示的条数 */
    private static final int DETAIL_LIMIT = 20;

    /** 认证学生默认在售商品上限，与学生认证模块保持一致 */
    private static final int STUDENT_PUBLISH_LIMIT = 20;

    private final UserService userService;
    private final StudentAuthService studentAuthService;
    private final StudentAuthMapper studentAuthMapper;
    private final ReviewMapper reviewMapper;
    private final CreditService creditService;
    private final OrderService orderService;
    private final ProductService productService;
    private final NoticeService noticeService;
    private final ReviewAssembler reviewAssembler;
    private final DashboardCacheService dashboardCacheService;

    // ==================== 列表 ====================

    @Override
    public IPage<AdminUserVO> page(AdminUserQueryDTO query) {
        AdminUserQueryDTO condition = query == null ? new AdminUserQueryDTO() : query;
        long current = condition.getPageNum() == null || condition.getPageNum() < 1 ? 1 : condition.getPageNum();
        long size = condition.getPageSize() == null || condition.getPageSize() < 1 ? 10
                : Math.min(condition.getPageSize(), 100);
        Page<AdminUserVO> page = new Page<>(current, size);
        IPage<AdminUserVO> result = baseMapper.selectAdminPage(page, condition);
        result.getRecords().forEach(this::decorate);
        return result;
    }

    // ==================== 详情 ====================

    @Override
    public AdminUserDetailVO detail(Long userId) {
        SysUser user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        AdminUserDetailVO vo = new AdminUserDetailVO();
        AdminUserVO base = new AdminUserVO();
        base.setUserId(user.getId());
        base.setNickname(user.getNickname());
        base.setAvatar(user.getAvatar());
        base.setUsername(user.getUsername());
        base.setPhone(user.getPhone());
        base.setGender(user.getGender());
        base.setRole(user.getRole());
        base.setStatus(user.getStatus());
        base.setStudentVerified(user.getStudentVerified());
        base.setCreditScore(user.getCreditScore());
        base.setCreateTime(user.getCreateTime());
        base.setLastLoginTime(user.getLastLoginTime());
        decorate(base);
        vo.setUser(base);
        vo.setIntro(user.getIntro());
        vo.setGenderLabel(Gender.label(user.getGender()));
        vo.setCreateTime(user.getCreateTime());

        // 信誉分变动：直接复用 CreditService 的流水查询，
        // 保证管理端看到的变动记录与用户端「分数明细」是同一份数据
        IPage<CreditLogVO> logs = creditService.logs(userId, null, 1L, (long) DETAIL_LIMIT);
        vo.setCreditLogs(logs.getRecords());

        // 发布商品
        IPage<ProductListVO> products = productService.pageBySeller(userId, null, 1L, (long) DETAIL_LIMIT);
        vo.setProducts(products.getRecords());
        vo.setPublishCount(products.getTotal());

        // 订单记录（买 + 卖）
        IPage<OrderListVO> orders = orderService.pageByParticipant(userId, 1L, (long) DETAIL_LIMIT);
        vo.setOrders(orders.getRecords());
        vo.setOrderCount(orders.getTotal());

        // 收到的评价：管理端不脱敏，管理员要看到真实评价人才能判断是否违规
        List<ReviewVO> reviews = reviewAssembler.toVOList(
                reviewMapper.selectRecentByReviewee(userId, DETAIL_LIMIT), null, false);
        vo.setReviews(reviews);
        vo.setReviewCount(reviewMapper.countByReviewee(userId));

        // 认证信息
        StudentAuth auth = latestAuth(userId);
        if (auth != null) {
            vo.setStudentNo(auth.getStudentNo());
            vo.setRealName(auth.getRealName());
            vo.setSchool(auth.getSchool());
        }
        return vo;
    }

    // ==================== 编辑资料 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = OpLogModule.USER, action = OpLogModule.ACTION_UPDATE_PROFILE, targetIndex = 1,
            desc = "修改用户资料")
    public void updateProfile(Long userId, UserProfileUpdateDTO dto, Long operatorId) {
        SysUser user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        boolean changed = false;

        if (StringUtils.hasText(dto.getNickname())) {
            String nickname = dto.getNickname().trim();
            if (nickname.length() > UserRule.NICKNAME_MAX_LENGTH) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "昵称不能超过 " + UserRule.NICKNAME_MAX_LENGTH + " 个字");
            }
            update.setNickname(nickname);
            changed = true;
        }
        if (dto.getAvatar() != null) {
            String avatar = dto.getAvatar().trim();
            update.setAvatar(StringUtils.hasText(avatar) ? avatar : null);
            changed = true;
        }
        if (dto.getGender() != null) {
            if (!Gender.isValid(dto.getGender())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "性别取值不合法");
            }
            update.setGender(dto.getGender());
            changed = true;
        }
        if (dto.getIntro() != null) {
            String intro = dto.getIntro().trim();
            if (intro.length() > UserRule.INTRO_MAX_LENGTH) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "个人简介不能超过 " + UserRule.INTRO_MAX_LENGTH + " 个字");
            }
            update.setIntro(StringUtils.hasText(intro) ? intro : null);
            changed = true;
        }
        if (!changed) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "没有需要修改的内容");
        }
        baseMapper.updateById(update);
        userService.evictCache(userId);
        log.info("管理员 {} 修改用户 {} 的资料", operatorId, userId);
    }

    // ==================== 封禁 / 解封 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = OpLogModule.USER, action = OpLogModule.ACTION_BAN, targetIndex = 1,
            desc = "封禁/解封用户")
    public void updateStatus(Long userId, AdminUserStatusDTO dto, Long operatorId) {
        SysUser user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        int target = dto.getStatus() == null ? 0 : dto.getStatus();
        if (userId.equals(operatorId) && target == 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能封禁自己的账号");
        }
        if (Integer.valueOf(target).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    target == 1 ? "该账号已处于封禁状态" : "该账号未被封禁");
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(target);
        baseMapper.updateById(update);

        if (target == 1) {
            // 封禁后其商品必须下架：账号已无法交易，商品留在架上等于让别人下单找不着卖家
            int offline = productService.offlineAllBySeller(userId);
            log.info("管理员 {} 封禁用户 {}，下架商品 {} 件", operatorId, userId, offline);
            notifyQuietly(userId, "账号已被封禁",
                    StringUtils.hasText(dto.getReason())
                            ? "你的账号已被封禁：" + dto.getReason().trim()
                            : "你的账号因违反平台规则被封禁，如有疑问请联系客服。");
        } else {
            log.info("管理员 {} 解封用户 {}", operatorId, userId);
            notifyQuietly(userId, "账号已解封", "你的账号已恢复使用，可以继续发布与购买闲置物品了。");
        }
        // 封禁状态变更必须清缓存：拦截器每次请求都读缓存判断是否放行
        userService.evictCache(userId);
        // 封禁会连带下架其全部在架商品，商品与用户两侧的统计口径都变了
        dashboardCacheService.evictOnUserChange();
        if (target == 1) {
            dashboardCacheService.evictOnProductChange();
        }
    }

    // ==================== 分配角色 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = OpLogModule.USER, action = OpLogModule.ACTION_UPDATE_ROLE, targetIndex = 1,
            desc = "变更用户角色")
    public void updateRole(Long userId, AdminUserRoleDTO dto, Long operatorId) {
        SysUser user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        String role = dto.getRole() == null ? "" : dto.getRole().trim();
        if (!RoleConst.USER.equals(role) && !RoleConst.STUDENT.equals(role)
                && !RoleConst.CUSTOMER_SERVICE.equals(role) && !RoleConst.ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色取值不合法");
        }
        if (role.equals(user.getRole())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该用户已经是「" + RoleConst.label(role) + "」");
        }
        if (userId.equals(operatorId) && !RoleConst.ADMIN.equals(user.getRole())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能修改自己的角色");
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setRole(role);
        // 认证状态与发布额度必须跟着角色一起走：
        // 降级为普通用户却留着 20 件发布额度，等于白送了一个绕过「仅认证学生可发布」的口子
        if (RoleConst.STUDENT.equals(role)) {
            update.setStudentVerified(1);
            update.setPublishLimit(STUDENT_PUBLISH_LIMIT);
        } else {
            update.setStudentVerified(0);
            update.setPublishLimit(0);
        }
        baseMapper.updateById(update);
        // 角色变更必须清缓存，否则拦截器仍按旧角色鉴权
        userService.evictCache(userId);
        // 角色变了，仪表盘的「用户角色分布」随之变化
        dashboardCacheService.evictOnUserChange();
        log.info("管理员 {} 将用户 {} 的角色从 {} 改为 {}", operatorId, userId, user.getRole(), role);
        notifyQuietly(userId, "账号角色已变更", "你的账号角色已变更为「" + RoleConst.label(role) + "」。");
    }

    // ==================== 学生认证审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = OpLogModule.STUDENT_AUTH, action = OpLogModule.ACTION_AUDIT, targetIndex = 1,
            desc = "审核学生认证")
    public void auditStudentAuth(Long userId, StudentAuthAuditDTO dto, Long operatorId) {
        StudentAuth pending = studentAuthMapper.selectOne(new LambdaQueryWrapper<StudentAuth>()
                .eq(StudentAuth::getUserId, userId)
                .eq(StudentAuth::getStatus, 0)
                .orderByDesc(StudentAuth::getId)
                .last("limit 1"));
        if (pending == null) {
            throw new BusinessException("该用户没有待审核的认证申请");
        }
        // 复用学生认证模块的审核逻辑：通过后升级角色并给发布额度、驳回后通知申请人
        studentAuthService.audit(pending.getId(), operatorId, dto);
    }

    // ==================== 内部方法 ====================

    /**
     * 给列表行补上派生文案：角色名、状态名、认证状态名、信誉等级名与颜色。
     * 这些都是从已有字段推导出来的，不落库——落库就会出现「分数改了但等级忘了改」的不一致。
     */
    private void decorate(AdminUserVO vo) {
        vo.setRoleLabel(RoleConst.label(vo.getRole()));
        vo.setStatusLabel(Integer.valueOf(1).equals(vo.getStatus()) ? "已封禁" : "正常");
        vo.setStudentVerifiedLabel(Integer.valueOf(1).equals(vo.getStudentVerified()) ? "已认证" : "未认证");
        int score = vo.getCreditScore() == null ? CreditRule.INITIAL_SCORE : vo.getCreditScore();
        CreditLevel level = CreditLevel.of(score);
        vo.setCreditScore(score);
        vo.setCreditLevelLabel(level.getLabel());
        vo.setCreditLevelColor(level.getColor());
    }

    private StudentAuth latestAuth(Long userId) {
        return studentAuthMapper.selectOne(new LambdaQueryWrapper<StudentAuth>()
                .eq(StudentAuth::getUserId, userId)
                .eq(StudentAuth::getStatus, 1)
                .orderByDesc(StudentAuth::getId)
                .last("limit 1"));
    }

    /**
     * 通知用户。通知失败绝不能连带把封禁 / 角色变更回滚——
     * 封禁是安全操作，用户即便没收到通知也必须被拦住。
     */
    private void notifyQuietly(Long userId, String title, String content) {
        try {
            noticeService.notifyAudit(userId, title, content, null);
        } catch (Exception e) {
            log.warn("用户管理通知发送失败 userId={}：{}", userId, e.getMessage());
        }
    }
}
