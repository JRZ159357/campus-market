// 文件路径: src/main/java/com/campus/market/controller/AdminAccountController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.AdminAccountPasswordDTO;
import com.campus.market.dto.AdminAccountQueryDTO;
import com.campus.market.dto.AdminAccountSaveDTO;
import com.campus.market.dto.AdminAccountStatusDTO;
import com.campus.market.service.AdminAccountService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.AdminAccountVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台账号管理接口。
 * <p>
 * 全部接口<b>只给管理员</b>，连读接口也不给客服：账号列表就是
 * 「平台上有哪些人能进管理端」的地图，属于安全信息，
 * 客服没有查看的业务必要（他查用户走的是用户管理）。
 * 这也与需求「客服登录后系统管理菜单整体隐藏」保持一致——
 * 菜单隐藏只是体验优化，注解才是真正的边界。
 */
@Tag(name = "账号管理（管理端）")
@RestController
@RequestMapping("/api/admin/account")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @Operation(summary = "后台账号分页（admin 与 customer_service）")
    @GetMapping("/page")
    @RequireRole(RoleConst.ADMIN)
    public Result<IPage<AdminAccountVO>> page(AdminAccountQueryDTO query) {
        return Result.ok(adminAccountService.page(query, UserContext.getUserId()));
    }

    @Operation(summary = "新增后台账号")
    @PostMapping
    @RequireRole(RoleConst.ADMIN)
    public Result<Long> create(@RequestBody @Valid AdminAccountSaveDTO dto) {
        return Result.ok("账号已创建", adminAccountService.create(dto));
    }

    @Operation(summary = "编辑后台账号（昵称 / 角色 / 状态）")
    @PutMapping("/{id}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> update(@Parameter(description = "账号ID") @PathVariable Long id,
                               @RequestBody @Valid AdminAccountSaveDTO dto) {
        adminAccountService.update(id, dto, UserContext.getUserId());
        return Result.ok("账号已更新", null);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/password")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> resetPassword(@Parameter(description = "账号ID") @PathVariable Long id,
                                      @RequestBody @Valid AdminAccountPasswordDTO dto) {
        adminAccountService.resetPassword(id, dto, UserContext.getUserId());
        return Result.ok("密码已重置，请通知该账号使用新密码登录", null);
    }

    @Operation(summary = "启用 / 禁用账号")
    @PutMapping("/{id}/status")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> updateStatus(@Parameter(description = "账号ID") @PathVariable Long id,
                                     @RequestBody @Valid AdminAccountStatusDTO dto) {
        adminAccountService.updateStatus(id, dto.getStatus(), UserContext.getUserId());
        return Result.ok(dto.getStatus() != null && dto.getStatus() == 1 ? "账号已禁用" : "账号已启用", null);
    }

    @Operation(summary = "删除账号（不能删自己，至少保留一个管理员）")
    @DeleteMapping("/{id}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> delete(@Parameter(description = "账号ID") @PathVariable Long id) {
        adminAccountService.delete(id, UserContext.getUserId());
        return Result.ok("账号已删除", null);
    }
}
