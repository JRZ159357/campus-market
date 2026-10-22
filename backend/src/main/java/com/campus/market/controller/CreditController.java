// 文件路径: src/main/java/com/campus/market/controller/CreditController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.PublicApi;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.CreditAdjustDTO;
import com.campus.market.service.CreditService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.CreditLogVO;
import com.campus.market.vo.CreditUserVO;
import com.campus.market.vo.CreditVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信誉分接口。
 * <p>
 * 查询类接口对未登录开放：信誉分是「这个卖家靠不靠谱」的公开信号，
 * 买家在商品详情页就要能看到，要求先登录才能看反而阻碍交易决策。
 * 调整接口只对管理员开放，且必须写原因——信誉分是用户资产，
 * 任何人工修改都要留痕可追溯。
 */
@Tag(name = "信誉分")
@RestController
@RequestMapping("/api/credit")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @Operation(summary = "用户信誉分详情（分数、等级、评价统计、最近评价与变动）")
    @GetMapping("/{userId}")
    @PublicApi
    public Result<CreditVO> detail(@Parameter(description = "用户ID") @PathVariable Long userId) {
        return Result.ok(creditService.detail(userId));
    }

    @Operation(summary = "信誉分变动记录（type=3 时只看管理员调整）")    @GetMapping("/{userId}/logs")
    public Result<IPage<CreditLogVO>> logs(
            @Parameter(description = "用户ID") @PathVariable Long userId,
            @Parameter(description = "类型:1评价 2举报 3管理员调整 4评价被删除")
            @RequestParam(required = false) Integer type,
            @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "1") Long pageNum,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Long pageSize) {
        return Result.ok(creditService.logs(userId, type, pageNum, pageSize));
    }

    @Operation(summary = "管理员按关键词搜索用户（昵称 / 账号 / 手机号 / ID）")
    @GetMapping("/admin/users")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<IPage<CreditUserVO>> adminUsers(
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "1") Long pageNum,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Long pageSize) {
        return Result.ok(creditService.pageUsers(keyword, pageNum, pageSize));
    }

    @Operation(summary = "管理员调整信誉分（需填原因，写流水）")
    @PutMapping("/{userId}/adjust")
    @RequireRole(RoleConst.ADMIN)
    public Result<CreditVO> adjust(@Parameter(description = "用户ID") @PathVariable Long userId,
                                   @RequestBody @Valid CreditAdjustDTO dto) {
        return Result.ok("信誉分已调整", creditService.adjust(userId, dto, UserContext.getUserId()));
    }
}
