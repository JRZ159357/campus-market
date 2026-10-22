// 文件路径: src/main/java/com/campus/market/controller/DashboardController.java
package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.service.DashboardService;
import com.campus.market.vo.DashboardDistributionVO;
import com.campus.market.vo.DashboardRankingVO;
import com.campus.market.vo.DashboardRecentVO;
import com.campus.market.vo.DashboardSummaryVO;
import com.campus.market.vo.DashboardTrendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据仪表盘接口（模块 10）。
 * <p>
 * 全部是 GET，只读。管理端首页与客服工作台共用这一组接口。
 * <p>
 * <b>为什么客服也能看</b>：客服排查纠纷时需要「这个商品是不是热点」、
 * 「这个卖家是不是平台头部」这类上下文，把仪表盘限定为管理员专属
 * 反而让客服没法判断问题的量级。因此按 {@code @RequireRole({ADMIN, CUSTOMER_SERVICE})}
 * 放开，两个角色看到的是同一份数据。
 * <p>
 * <b>为什么没有写接口</b>：除了「刷新缓存」，本模块不提供任何修改数据的能力。
 * 仪表盘是统计视图，它的数据全部来自其它模块的表，
 * 在这里加一个「修正数字」的接口等于允许绕过业务规则直接改统计口径，
 * 那样一来所有指标就都不可信了。
 * <p>
 * 唯一的写操作是 {@link #refresh()}：它只清 Redis 缓存，不碰数据库。
 * 用 POST 而非 GET 表达「这是一个状态变更」，
 * 且只给管理员——客服是只读账号，顶部刷新按钮对它不可见，
 * 拦截器的客服只读规则也会在到达这里之前就把请求挡掉。
 */
@Tag(name = "数据仪表盘")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "核心指标（管理员与客服，只读）")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @GetMapping("/summary")
    public Result<DashboardSummaryVO> summary() {
        return Result.ok(dashboardService.summary());
    }

    @Operation(summary = "近7日/30日趋势（管理员与客服，只读）")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @GetMapping("/trend")
    public Result<DashboardTrendVO> trend(
            @Parameter(description = "统计天数，7 或 30，默认 7")
            @RequestParam(value = "days", required = false) Integer days) {
        return Result.ok(dashboardService.trend(days));
    }

    @Operation(summary = "分布统计（管理员与客服，只读）")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @GetMapping("/distribution")
    public Result<DashboardDistributionVO> distribution() {
        return Result.ok(dashboardService.distribution());
    }

    @Operation(summary = "排行榜（管理员与客服，只读）")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @GetMapping("/ranking")
    public Result<DashboardRankingVO> ranking() {
        return Result.ok(dashboardService.ranking());
    }

    @Operation(summary = "最近动态（管理员与客服，只读）")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @GetMapping("/recent")
    public Result<List<DashboardRecentVO>> recent() {
        return Result.ok(dashboardService.recent());
    }

    @Operation(summary = "手动刷新：清除仪表盘缓存后重新统计（仅管理员）")
    @RequireRole(RoleConst.ADMIN)
    @PostMapping("/refresh")
    public Result<Void> refresh() {
        dashboardService.refresh();
        return Result.ok("缓存已清除，刷新后即为最新数据", null);
    }
}
