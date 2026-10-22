// 文件路径: src/main/java/com/campus/market/service/AdminUserService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.dto.AdminUserQueryDTO;
import com.campus.market.dto.AdminUserRoleDTO;
import com.campus.market.dto.AdminUserStatusDTO;
import com.campus.market.dto.StudentAuthAuditDTO;
import com.campus.market.dto.UserProfileUpdateDTO;
import com.campus.market.vo.AdminUserDetailVO;
import com.campus.market.vo.AdminUserVO;

/**
 * 管理端用户管理服务。
 * <p>
 * 与 {@link UserCenterService} 分开的原因：两者的权限模型完全不同。
 * 个人中心是「用户改自己的数据」，只能改自己的；
 * 用户管理是「管理员改别人的数据」，必须记录操作人，并且有角色变更、
 * 封禁这类高危操作，审计要求完全不同。混在一个 Service 里，
 * 迟早会出现「某个方法忘了判断操作人是不是本人」这种越权。
 */
public interface AdminUserService {

    /**
     * 用户列表分页。顶部标签与搜索栏共用同一套条件。
     */
    IPage<AdminUserVO> page(AdminUserQueryDTO query);

    /**
     * 用户详情：基本信息 + 信誉分变动 + 发布商品 + 订单 + 评价。
     */
    AdminUserDetailVO detail(Long userId);

    /**
     * 管理员编辑用户资料（昵称、头像、性别、简介）。
     */
    void updateProfile(Long userId, UserProfileUpdateDTO dto, Long operatorId);

    /**
     * 封禁 / 解封。封禁后该用户所有已上架商品一并下架。
     */
    void updateStatus(Long userId, AdminUserStatusDTO dto, Long operatorId);

    /**
     * 分配角色。客服与管理员角色之间可互转；降级为普通用户时同步回收发布额度。
     */
    void updateRole(Long userId, AdminUserRoleDTO dto, Long operatorId);

    /**
     * 学生认证审核（管理端在用户列表里直接通过/驳回）。
     * <p>
     * 复用已有的 {@link StudentAuthService#audit} 逻辑：
     * 通过后角色升级为认证学生并给到发布额度，驳回后通知申请人。
     */
    void auditStudentAuth(Long userId, StudentAuthAuditDTO dto, Long operatorId);
}
