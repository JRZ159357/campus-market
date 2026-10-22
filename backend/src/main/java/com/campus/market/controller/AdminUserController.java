// 文件路径: src/main/java/com/campus/market/controller/AdminUserController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.AdminUserQueryDTO;
import com.campus.market.dto.AdminUserRoleDTO;
import com.campus.market.dto.AdminUserStatusDTO;
import com.campus.market.dto.StudentAuthAuditDTO;
import com.campus.market.dto.UserProfileUpdateDTO;
import com.campus.market.service.AdminUserService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.AdminUserDetailVO;
import com.campus.market.vo.AdminUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端用户管理接口。
 * <p>
 * 路径规范：/api/admin/user/{操作}。
 * <p>
 * 读接口对管理员与客服都开放（客服要能查用户信息处理咨询），
 * 写接口只给管理员——这与拦截器的「客服只读」规则是两层独立的保障：
 * 即使有人绕过前端直接调接口，服务端依然会拦下。
 */
@Tag(name = "用户管理（管理端）")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "用户列表分页（标签 + 关键词 + 信誉分区间 + 注册时间区间）")
    @GetMapping("/page")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<IPage<AdminUserVO>> page(AdminUserQueryDTO query) {
        return Result.ok(adminUserService.page(query));
    }

    @Operation(summary = "用户详情（基本信息 + 信誉分变动 + 商品 + 订单 + 评价）")
    @GetMapping("/{userId}")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<AdminUserDetailVO> detail(@Parameter(description = "用户ID") @PathVariable Long userId) {
        return Result.ok(adminUserService.detail(userId));
    }

    @Operation(summary = "编辑用户资料（昵称 / 头像 / 性别 / 简介）")
    @PutMapping("/{userId}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> updateProfile(@Parameter(description = "用户ID") @PathVariable Long userId,
                                      @RequestBody @Valid UserProfileUpdateDTO dto) {
        adminUserService.updateProfile(userId, dto, UserContext.getUserId());
        return Result.ok("资料已更新", null);
    }

    @Operation(summary = "封禁 / 解封用户")
    @PutMapping("/{userId}/status")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> updateStatus(@Parameter(description = "用户ID") @PathVariable Long userId,
                                      @RequestBody @Valid AdminUserStatusDTO dto) {
        adminUserService.updateStatus(userId, dto, UserContext.getUserId());
        return Result.ok(dto.getStatus() != null && dto.getStatus() == 1 ? "已封禁该账号" : "已解封", null);
    }

    @Operation(summary = "分配角色")
    @PutMapping("/{userId}/role")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> updateRole(@Parameter(description = "用户ID") @PathVariable Long userId,
                                   @RequestBody @Valid AdminUserRoleDTO dto) {
        adminUserService.updateRole(userId, dto, UserContext.getUserId());
        return Result.ok("角色已更新", null);
    }

    @Operation(summary = "学生认证通过 / 驳回")
    @PutMapping("/{userId}/student-auth")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> auditStudentAuth(@Parameter(description = "用户ID") @PathVariable Long userId,
                                         @RequestBody @Valid StudentAuthAuditDTO dto) {
        adminUserService.auditStudentAuth(userId, dto, UserContext.getUserId());
        return Result.ok(dto.getStatus() != null && dto.getStatus() == 1 ? "认证已通过" : "认证已驳回", null);
    }
}
