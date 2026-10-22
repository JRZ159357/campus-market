// 文件路径: src/main/java/com/campus/market/service/impl/StudentAuthServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.RoleConst;
import com.campus.market.dto.StudentAuthApplyDTO;
import com.campus.market.dto.StudentAuthAuditDTO;
import com.campus.market.dto.StudentAuthQueryDTO;
import com.campus.market.entity.StudentAuth;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.StudentAuthMapper;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.service.NoticeService;
import com.campus.market.service.StudentAuthService;
import com.campus.market.service.UserService;
import com.campus.market.vo.AuthStatusVO;
import com.campus.market.vo.StudentAuthVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学生认证业务实现。
 * <p>
 * 核心规则：
 * 1) 仅"普通用户"且未认证者可提交，已认证或已有待审核申请时拒绝；
 * 2) 同一学号只允许被一个账号认证成功；
 * 3) 审核通过后角色升级为 student，publish_limit=10，并清除用户缓存使其立即生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAuthServiceImpl extends ServiceImpl<StudentAuthMapper, StudentAuth>
        implements StudentAuthService {

    /** 认证学生默认在售商品上限（模块2需求：最多同时上架 20 件） */
    private static final int STUDENT_PUBLISH_LIMIT = 20;

    private final UserService userService;
    private final NoticeService noticeService;
    private final DashboardCacheService dashboardCacheService;

    @Override
    public AuthStatusVO myStatus(Long userId) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        AuthStatusVO vo = new AuthStatusVO();
        vo.setVerified(Integer.valueOf(1).equals(user.getStudentVerified()));

        StudentAuth latest = this.lambdaQuery()
                .eq(StudentAuth::getUserId, userId)
                .orderByDesc(StudentAuth::getId)
                .last("limit 1")
                .one();
        if (latest == null) {
            vo.setStatus(null);
            vo.setStatusLabel("未提交认证");
            return vo;
        }
        vo.setStatus(latest.getStatus());
        vo.setStatusLabel(statusLabel(latest.getStatus()));
        vo.setRejectReason(latest.getRejectReason());
        vo.setStudentNo(latest.getStudentNo());
        vo.setRealName(latest.getRealName());
        vo.setSchool(latest.getSchool());
        vo.setApplyTime(latest.getCreateTime());
        vo.setAuditTime(latest.getAuditTime());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(Long userId, StudentAuthApplyDTO dto) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (Integer.valueOf(1).equals(user.getStudentVerified())) {
            throw new BusinessException("你已完成学生认证，无需重复提交");
        }
        if (RoleConst.CUSTOMER_SERVICE.equals(user.getRole()) || RoleConst.ADMIN.equals(user.getRole())) {
            throw new BusinessException("后台账号无需进行学生认证");
        }

        long pending = this.lambdaQuery()
                .eq(StudentAuth::getUserId, userId)
                .eq(StudentAuth::getStatus, 0)
                .count();
        if (pending > 0) {
            throw new BusinessException("你已有一条待审核的申请，请耐心等待审核结果");
        }

        long occupied = this.lambdaQuery()
                .eq(StudentAuth::getStudentNo, dto.getStudentNo())
                .eq(StudentAuth::getStatus, 1)
                .count();
        if (occupied > 0) {
            throw new BusinessException("该学号已被其他账号完成认证，如有疑问请联系客服");
        }

        StudentAuth auth = new StudentAuth();
        auth.setUserId(userId);
        auth.setStudentNo(dto.getStudentNo().trim());
        auth.setRealName(dto.getRealName().trim());
        auth.setSchool(StringUtils.hasText(dto.getSchool()) ? dto.getSchool().trim() : null);
        auth.setCertImg(dto.getCertImg());
        auth.setStatus(0);
        this.save(auth);
        log.info("学生认证申请提交: userId={}, studentNo={}", userId, auth.getStudentNo());
    }

    @Override
    public IPage<StudentAuthVO> pageQuery(StudentAuthQueryDTO query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;

        LambdaQueryWrapper<StudentAuth> wrapper = Wrappers.<StudentAuth>lambdaQuery()
                .eq(query.getStatus() != null, StudentAuth::getStatus, query.getStatus())
                .and(keyword != null, w -> w
                        .like(StudentAuth::getStudentNo, keyword)
                        .or()
                        .like(StudentAuth::getRealName, keyword))
                .orderByAsc(StudentAuth::getStatus)
                .orderByDesc(StudentAuth::getCreateTime);

        Page<StudentAuth> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        // 一次性批量取用户信息，避免逐行查库
        List<Long> userIds = page.getRecords().stream()
                .map(StudentAuth::getUserId)
                .distinct()
                .toList();
        Map<Long, SysUser> userMap = loadUserMap(userIds);
        return page.convert(auth -> toVO(auth, userMap));
    }

    @Override
    public StudentAuthVO detail(Long id) {
        StudentAuth auth = this.getById(id);
        if (auth == null) {
            throw new BusinessException("认证申请不存在");
        }
        return toVO(auth, loadUserMap(Collections.singletonList(auth.getUserId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long authId, Long auditorId, StudentAuthAuditDTO dto) {
        StudentAuth auth = this.getById(authId);
        if (auth == null) {
            throw new BusinessException("认证申请不存在");
        }
        if (!Integer.valueOf(0).equals(auth.getStatus())) {
            throw new BusinessException("该申请已审核，不能重复操作");
        }
        boolean pass = Integer.valueOf(1).equals(dto.getStatus());
        if (!pass && !StringUtils.hasText(dto.getRejectReason())) {
            throw new BusinessException("驳回时必须填写驳回原因");
        }

        StudentAuth update = new StudentAuth();
        update.setId(authId);
        update.setStatus(dto.getStatus());
        update.setAuditorId(auditorId);
        update.setAuditTime(LocalDateTime.now());
        update.setRejectReason(pass ? null : dto.getRejectReason().trim());
        this.updateById(update);

        if (pass) {
            SysUser userUpdate = new SysUser();
            userUpdate.setId(auth.getUserId());
            userUpdate.setRole(RoleConst.STUDENT);
            userUpdate.setStudentVerified(1);
            userUpdate.setPublishLimit(STUDENT_PUBLISH_LIMIT);
            userService.updateById(userUpdate);
            // 角色变更必须清缓存，否则拦截器仍按旧角色鉴权
            userService.evictCache(auth.getUserId());
            // 角色从「普通用户」变成「认证学生」，仪表盘的角色分布随之变化
            dashboardCacheService.evictOnUserChange();
            log.info("学生认证通过: userId={}, auditorId={}", auth.getUserId(), auditorId);
        } else {
            log.info("学生认证驳回: userId={}, reason={}", auth.getUserId(), dto.getRejectReason());
        }

        // 认证结果关系到他能不能发布商品，必须主动通知，不能只等着用户自己回来看
        try {
            if (pass) {
                noticeService.notifyAudit(auth.getUserId(), "学生认证已通过",
                        "恭喜！你的学生认证已通过，现在可以发布闲置物品了（最多同时上架 "
                                + STUDENT_PUBLISH_LIMIT + " 件）。", null);
            } else {
                noticeService.notifyAudit(auth.getUserId(), "学生认证未通过",
                        "认证资料未通过审核：" + dto.getRejectReason().trim()
                                + "。请核对学号与姓名后重新提交。", null);
            }
        } catch (Exception e) {
            log.warn("学生认证通知发送失败 userId={}：{}", auth.getUserId(), e.getMessage());
        }
    }

    private Map<Long, SysUser> loadUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = userService.listByIds(userIds);
        return users.stream().collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
    }

    private StudentAuthVO toVO(StudentAuth auth, Map<Long, SysUser> userMap) {
        StudentAuthVO vo = new StudentAuthVO();
        BeanUtils.copyProperties(auth, vo);
        SysUser user = userMap.get(auth.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setPhone(user.getPhone());
        }
        return vo;
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "未提交认证";
        }
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            default -> "未知";
        };
    }
}
