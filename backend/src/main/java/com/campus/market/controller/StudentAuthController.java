// 文件路径: src/main/java/com/campus/market/controller/StudentAuthController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.StudentAuthApplyDTO;
import com.campus.market.dto.StudentAuthAuditDTO;
import com.campus.market.dto.StudentAuthQueryDTO;
import com.campus.market.service.StudentAuthService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.AuthStatusVO;
import com.campus.market.vo.StudentAuthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 学生认证接口。用户端提交/查询，管理端审核。
 */
@Tag(name = "学生认证")
@RestController
@RequestMapping("/api/student-auth")
@RequiredArgsConstructor
public class StudentAuthController {

    private final StudentAuthService studentAuthService;

    @Operation(summary = "查询我的认证状态（含最近一次申请记录）")
    @GetMapping("/status")
    public Result<AuthStatusVO> myStatus() {
        return Result.ok(studentAuthService.myStatus(UserContext.getUserId()));
    }

    @Operation(summary = "提交学生认证申请")
    @PostMapping
    public Result<Void> apply(@RequestBody @Valid StudentAuthApplyDTO dto) {
        studentAuthService.apply(UserContext.getUserId(), dto);
        return Result.ok("提交成功，请等待审核", null);
    }

    @Operation(summary = "分页查询认证申请（管理端）")
    @GetMapping("/page")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<IPage<StudentAuthVO>> page(StudentAuthQueryDTO query) {
        return Result.ok(studentAuthService.pageQuery(query));
    }

    @Operation(summary = "查看认证申请详情（管理端）")
    @GetMapping("/{id}")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<StudentAuthVO> detail(@Parameter(description = "申请ID") @PathVariable Long id) {
        return Result.ok(studentAuthService.detail(id));
    }

    @Operation(summary = "审核认证申请（管理端）")
    @PutMapping("/{id}/audit")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> audit(@Parameter(description = "申请ID") @PathVariable Long id,
                              @RequestBody @Valid StudentAuthAuditDTO dto) {
        studentAuthService.audit(id, UserContext.getUserId(), dto);
        return Result.ok("审核完成", null);
    }
}
