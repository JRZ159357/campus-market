// 文件路径: src/main/java/com/campus/market/controller/ReviewController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.PublicApi;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.ReviewFollowDTO;
import com.campus.market.dto.ReviewQueryDTO;
import com.campus.market.dto.ReviewReplyDTO;
import com.campus.market.dto.ReviewSubmitDTO;
import com.campus.market.service.CreditService;
import com.campus.market.service.ReviewService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.ReviewOrderVO;
import com.campus.market.vo.ReviewPendingVO;
import com.campus.market.vo.ReviewStatVO;
import com.campus.market.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价接口。
 * <p>
 * 权限分层：
 * <ul>
 *   <li>提交 / 追评 / 回复：登录用户，且必须是订单参与方（服务层校验）；</li>
 *   <li>商品评价、用户评价、商品统计：公开只读，未登录也能看（评价是商品决策依据，不该藏起来）；</li>
 *   <li>订单评价、待评价：登录用户，服务层保证只能看自己参与订单的评价详情。</li>
 * </ul>
 * 删除违规评价只对管理员开放：客服是只读角色，拦截器已经拦掉非 GET，
 * 这里再挂 @RequireRole(ADMIN) 形成双保险。
 */
@Tag(name = "评价")
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    private final CreditService creditService;

    @Operation(summary = "提交评价（订单已完成、当前用户是对应角色、未评价过）")
    @PostMapping
    public Result<ReviewVO> submit(@RequestBody @Valid ReviewSubmitDTO dto) {
        return Result.ok("评价成功", reviewService.submit(UserContext.getUserId(), dto));
    }

    @Operation(summary = "追评（评价人本人、未追评过、交易完成7天内）")
    @PostMapping("/follow")
    public Result<ReviewVO> follow(@RequestBody @Valid ReviewFollowDTO dto) {
        return Result.ok("追评成功", reviewService.follow(UserContext.getUserId(), dto));
    }

    @Operation(summary = "卖家回复买家评价（仅被评价的卖家、只能回一次）")
    @PostMapping("/reply")
    public Result<ReviewVO> reply(@RequestBody @Valid ReviewReplyDTO dto) {
        return Result.ok("回复成功", reviewService.reply(UserContext.getUserId(), dto));
    }

    @Operation(summary = "单条评价详情（追评页展示原评价用）")
    @GetMapping("/{id}")
    @PublicApi
    public Result<ReviewVO> detail(@Parameter(description = "评价ID") @PathVariable Long id) {
        return Result.ok(reviewService.detail(id, UserContext.getUserId()));
    }

    @Operation(summary = "商品评价列表（匿名已脱敏，支持好评/中评/差评/有图筛选）")
    @GetMapping("/product/{productId}")
    @PublicApi
    public Result<IPage<ReviewVO>> productReviews(
            @Parameter(description = "商品ID") @PathVariable Long productId,
            ReviewQueryDTO query) {
        return Result.ok(reviewService.pageByProduct(productId, query));
    }

    @Operation(summary = "商品评价统计（平均分 / 好评数 / 各星级分布）")
    @GetMapping("/product/{productId}/stat")
    @PublicApi
    public Result<ReviewStatVO> productStat(@Parameter(description = "商品ID") @PathVariable Long productId) {
        return Result.ok(creditService.statOfProduct(productId));
    }

    @Operation(summary = "某用户收到的评价列表（带信誉等级标签）")
    @GetMapping("/user/{userId}")
    @PublicApi
    public Result<IPage<ReviewVO>> userReviews(
            @Parameter(description = "用户ID") @PathVariable Long userId,
            ReviewQueryDTO query) {
        return Result.ok(reviewService.pageByUser(userId, query));
    }

    @Operation(summary = "订单评价详情（买卖双方评价 + 当前用户能否评价/追评）")
    @GetMapping("/order/{orderId}")
    public Result<ReviewOrderVO> orderReviews(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return Result.ok(reviewService.orderReviews(orderId, UserContext.getUserId()));
    }

    @Operation(summary = "我待评价的订单列表")
    @GetMapping("/my/pending")
    public Result<IPage<ReviewPendingVO>> pending(
            @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "1") Long pageNum,
            @Parameter(description = "每页条数") @RequestParam(required = false, defaultValue = "10") Long pageSize) {
        return Result.ok(reviewService.pending(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "我待评价的订单数（个人中心角标）")
    @GetMapping("/my/pending/count")
    public Result<Long> pendingCount() {
        return Result.ok(reviewService.pendingCount(UserContext.getUserId()));
    }

    @Operation(summary = "管理端评价列表（多条件筛选）")
    @GetMapping("/admin/page")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<IPage<ReviewVO>> adminPage(ReviewQueryDTO query) {
        return Result.ok(reviewService.adminPage(query));
    }

    @Operation(summary = "删除违规评价（仅管理员，不回退信誉分）")
    @DeleteMapping("/{id}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> delete(@Parameter(description = "评价ID") @PathVariable Long id) {
        reviewService.adminDelete(id, UserContext.getUserId());
        return Result.ok("评价已删除", null);
    }
}
