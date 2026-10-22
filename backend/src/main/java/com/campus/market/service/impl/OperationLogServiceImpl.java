// 文件路径: src/main/java/com/campus/market/service/impl/OperationLogServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.OpLogModule;
import com.campus.market.common.RoleConst;
import com.campus.market.dto.OperationLogQueryDTO;
import com.campus.market.entity.SysOperationLog;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.SysOperationLogMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.service.OperationLogService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.OperationLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 操作日志服务实现。
 * <p>
 * 设计上最关键的一点：{@link #record} 吞掉全部异常。
 * 日志是事后追溯的辅助信息，如果日志写失败能把「封禁违规用户」这种
 * 安全操作一起回滚，等于给攻击者递了一个「把数据库写满导致日志失败，
 * 就能让封禁不生效」的绕过路径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog>
        implements OperationLogService {

    private static final int MAX_DETAIL_LENGTH = 500;

    private final SysUserMapper sysUserMapper;

    @Override
    public void record(Long operatorId, String module, String action, Long targetId, String detail) {
        try {
            SysOperationLog entity = new SysOperationLog();
            entity.setOperatorId(operatorId);
            entity.setOperatorRole(resolveRole());
            entity.setOperatorName(resolveName(operatorId));
            entity.setModule(module);
            entity.setAction(action);
            entity.setTargetId(targetId);
            entity.setDetail(truncate(detail));
            entity.setIp(currentIp());
            this.save(entity);
        } catch (Exception e) {
            // 只 warn 不抛出：日志写失败不能影响主业务
            log.warn("操作日志写入失败 module={}, action={}, targetId={}：{}",
                    module, action, targetId, e.getMessage());
        }
    }

    @Override
    public IPage<OperationLogVO> page(OperationLogQueryDTO query) {
        OperationLogQueryDTO condition = query == null ? new OperationLogQueryDTO() : query;
        long current = condition.getPageNum() == null || condition.getPageNum() < 1 ? 1 : condition.getPageNum();
        long size = condition.getPageSize() == null || condition.getPageSize() < 1 ? 10
                : Math.min(condition.getPageSize(), 100);
        // 先把条件值提到局部变量再拼 Wrapper：
        // 像 like(boolean, col, value.trim()) 这种写法，value.trim() 会在
        // boolean 为 false 时照样求值（Java 的实参总是先求值），
        // 于是「不传昵称」这种最常见的调用会直接 NPE。
        String operatorName = StringUtils.hasText(condition.getOperatorName())
                ? condition.getOperatorName().trim() : null;
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .eq(condition.getOperatorId() != null, SysOperationLog::getOperatorId, condition.getOperatorId())
                .like(operatorName != null, SysOperationLog::getOperatorName, operatorName)
                .eq(StringUtils.hasText(condition.getModule()), SysOperationLog::getModule, condition.getModule())
                .eq(StringUtils.hasText(condition.getAction()), SysOperationLog::getAction, condition.getAction())
                .ge(condition.getStartTime() != null, SysOperationLog::getCreateTime, condition.getStartTime())
                .le(condition.getEndTime() != null, SysOperationLog::getCreateTime, condition.getEndTime())
                .orderByDesc(SysOperationLog::getId);
        IPage<SysOperationLog> result = this.page(new Page<>(current, size), wrapper);
        Page<OperationLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<OperationLogVO> records = result.getRecords().stream().map(this::toVO).toList();
        voPage.setRecords(records);
        return voPage;
    }

    // ==================== 内部方法 ====================

    private OperationLogVO toVO(SysOperationLog entity) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(entity.getId());
        vo.setOperatorId(entity.getOperatorId());
        vo.setOperatorName(entity.getOperatorName());
        vo.setOperatorRole(entity.getOperatorRole());
        vo.setOperatorRoleLabel(RoleConst.label(entity.getOperatorRole()));
        vo.setModule(entity.getModule());
        vo.setModuleLabel(OpLogModule.label(entity.getModule()));
        vo.setAction(entity.getAction());
        vo.setActionLabel(OpLogModule.actionLabel(entity.getAction()));
        vo.setTargetId(entity.getTargetId());
        vo.setDetail(entity.getDetail());
        vo.setIp(entity.getIp());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private String resolveRole() {
        String role = UserContext.getRole();
        return StringUtils.hasText(role) ? role : "system";
    }

    /**
     * 操作人昵称优先从上下文拿，其次回查数据库。
     * <p>
     * 为什么要冗余昵称而不是只存 operatorId：日志是几年后要给人看的，
     * 那时运营人员可能已经改过昵称，只留 ID 会导致「这个 37 号到底是谁」无从查证。
     */
    private String resolveName(Long operatorId) {
        if (operatorId == null) {
            return "系统";
        }
        try {
            SysUser user = sysUserMapper.selectById(operatorId);
            return user != null && StringUtils.hasText(user.getNickname())
                    ? user.getNickname() : ("用户#" + operatorId);
        } catch (Exception e) {
            // 查不到就退化成 ID 字符串，不影响主流程
            return "用户#" + operatorId;
        }
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            var request = attributes.getRequest();
            // 取 X-Forwarded-For 的第一段：经过 Nginx 后 getRemoteAddr 拿到的是代理 IP
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() > MAX_DETAIL_LENGTH ? detail.substring(0, MAX_DETAIL_LENGTH) : detail;
    }
}