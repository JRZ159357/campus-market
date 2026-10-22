// 文件路径: src/main/java/com/campus/market/controller/ReportController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.ReportHandleDTO;
import com.campus.market.dto.ReportQueryDTO;
import com.campus.market.dto.ReportSubmitDTO;
import com.campus.market.service.ReportService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.ReportStatsVO;
import com.campus.market.vo.ReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报接口。
 * <p>
 * 路径分层刻意做了区分：
 * <ul>
 *   <li>{@code /api/report/**} —— 用户端。任何登录用户，只能提交与查看自己的举报；</li>
 *   <li>{@code /api/report/admin/**} —— 管理端。客服与管理员都能读，
 *       接单与处理两个写操作开放给客服（这是客服的本职工作，
 *       不给客服写权限的话客服就只是个看客），其余由
 *       {@code @RequireRole} 与拦截器的客服只读规则双重兜底。</li>
 * </ul>
 * 之所以把 admin 段挂在同一前缀下而不是另开 {@code /api/admin/report}：
 * 举报是本系统唯一一个「客服和管理员高频共同操作」的模块，
 * 放在一起能让前端 API 封装收敛到一个文件；真正的权限隔离靠注解而非路径前缀。
 */
@Tag(name = "举报")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // ==================== 用户端 ====================

    @Operation(summary = "提交举报",
            description = "不能举报自己；同一用户对同一对象24小时内只能举报一次；描述至少5字；证据图最多3张")
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody ReportSubmitDTO dto) {
        return Result.ok(reportService.submit(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的举报列表")
    @GetMapping("/my")
    public Result<IPage<ReportVO>> myPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Long pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Long pageSize) {
        return Result.ok(reportService.myPage(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "我的举报详情（只能看自己的）")
    @GetMapping("/my/{id}")
    public Result<ReportVO> myDetail(@Parameter(description = "举报ID") @PathVariable Long id) {
        return Result.ok(reportService.myDetail(UserContext.getUserId(), id));
    }

    // ==================== 管理端 ====================

    @Operation(summary = "举报列表（客服与管理员）")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @GetMapping("/admin/page")
    public Result<IPage<ReportVO>> adminPage(ReportQueryDTO query) {
        return Result.ok(reportService.adminPage(query, UserContext.getUserId()));
    }

    @Operation(summary = "举报详情（含被举报对象快照）")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @GetMapping("/admin/{id}")
    public Result<ReportVO> adminDetail(@Parameter(description = "举报ID") @PathVariable Long id) {
        return Result.ok(reportService.adminDetail(id));
    }

    @Operation(summary = "接单：待处理 → 处理中")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @PutMapping("/admin/{id}/accept")
    public Result<Void> accept(@Parameter(description = "举报ID") @PathVariable Long id) {
        reportService.accept(id, UserContext.getUserId());
        return Result.ok();
    }

    @Operation(summary = "处理举报：执行处置动作并给双方发通知")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @PutMapping("/admin/{id}/handle")
    public Result<Void> handle(@Parameter(description = "举报ID") @PathVariable Long id,
                               @Valid @RequestBody ReportHandleDTO dto) {
        reportService.handle(id, dto, UserContext.getUserId());
        return Result.ok();
    }

    @Operation(summary = "举报统计（工作台：待处理数、今日新增、处理率、平均处理时长）")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @GetMapping("/admin/stats")
    public Result<ReportStatsVO> stats() {
        return Result.ok(reportService.stats());
    }
}