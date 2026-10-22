// 文件路径: src/main/java/com/campus/market/controller/OrderController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.AdminOrderOperateDTO;
import com.campus.market.dto.OrderCancelDTO;
import com.campus.market.dto.OrderCreateDTO;
import com.campus.market.dto.OrderQueryDTO;
import com.campus.market.dto.RefundApplyDTO;
import com.campus.market.dto.RefundRejectDTO;
import com.campus.market.service.OrderService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.OrderCountVO;
import com.campus.market.vo.OrderCreateVO;
import com.campus.market.vo.OrderDetailVO;
import com.campus.market.vo.OrderListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 订单接口：买家下单与履约、卖家发货与退款处理、管理端查询与强制干预。
 * 所有写操作都在 Service 里做"身份 + 状态"双重校验，Controller 只做转发。
 */
@Tag(name = "订单")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ==================== 买家侧 ====================

    @Operation(summary = "创建订单（校验商品在售、不能买自己的商品、同一商品不能有进行中订单）")
    @PostMapping("/create")
    public Result<OrderCreateVO> create(@RequestBody @Valid OrderCreateDTO dto) {
        return Result.ok("下单成功", orderService.create(UserContext.getUserId(), dto));
    }

    @Operation(summary = "模拟支付（待付款 → 待发货，同时把商品置为已售出）")
    @PostMapping("/{orderNo}/pay")
    public Result<Void> pay(@Parameter(description = "订单号") @PathVariable String orderNo) {
        orderService.pay(UserContext.getUserId(), orderNo);
        return Result.ok("支付成功", null);
    }

    @Operation(summary = "取消订单（仅待付款，买家本人）")
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@Parameter(description = "订单号") @PathVariable String orderNo,
                               @RequestBody(required = false) OrderCancelDTO dto) {
        orderService.cancel(UserContext.getUserId(), orderNo, dto == null ? null : dto.getReason());
        return Result.ok("订单已取消", null);
    }

    @Operation(summary = "确认收货（仅待收货，买家本人）")
    @PostMapping("/{orderNo}/receive")
    public Result<Void> receive(@Parameter(description = "订单号") @PathVariable String orderNo) {
        orderService.receive(UserContext.getUserId(), orderNo);
        return Result.ok("交易完成", null);
    }

    @Operation(summary = "申请退款（待发货 / 待收货，需填写至少5字原因）")
    @PostMapping("/{orderNo}/apply-refund")
    public Result<Void> applyRefund(@Parameter(description = "订单号") @PathVariable String orderNo,
                                    @RequestBody @Valid RefundApplyDTO dto) {
        orderService.applyRefund(UserContext.getUserId(), orderNo, dto.getReason());
        return Result.ok("退款申请已提交，等待卖家处理", null);
    }

    @Operation(summary = "撤销退款申请（退款中，买家本人）")
    @PostMapping("/{orderNo}/cancel-refund")
    public Result<Void> cancelRefund(@Parameter(description = "订单号") @PathVariable String orderNo) {
        orderService.cancelRefund(UserContext.getUserId(), orderNo);
        return Result.ok("已撤销退款申请", null);
    }

    @Operation(summary = "我买到的（分页，可按状态筛选）")
    @GetMapping("/buyer/page")
    public Result<IPage<OrderListVO>> buyerPage(OrderQueryDTO query) {
        return Result.ok(orderService.buyerPage(UserContext.getUserId(), query));
    }

    @Operation(summary = "我买到的各状态订单数（个人中心角标）")
    @GetMapping("/buyer/count")
    public Result<OrderCountVO> buyerCount() {
        return Result.ok(orderService.buyerCount(UserContext.getUserId()));
    }

    // ==================== 卖家侧 ====================

    @Operation(summary = "卖家发货（待发货 → 待收货，卖家本人）")
    @PostMapping("/{orderNo}/ship")
    public Result<Void> ship(@Parameter(description = "订单号") @PathVariable String orderNo) {
        orderService.ship(UserContext.getUserId(), orderNo);
        return Result.ok("已确认发货", null);
    }

    @Operation(summary = "同意退款（退款中 → 已退款，卖家本人，商品恢复上架）")
    @PostMapping("/{orderNo}/agree-refund")
    public Result<Void> agreeRefund(@Parameter(description = "订单号") @PathVariable String orderNo) {
        orderService.agreeRefund(UserContext.getUserId(), orderNo);
        return Result.ok("已同意退款", null);
    }

    @Operation(summary = "拒绝退款（退款中 → 原状态，卖家本人，需填写原因）")
    @PostMapping("/{orderNo}/reject-refund")
    public Result<Void> rejectRefund(@Parameter(description = "订单号") @PathVariable String orderNo,
                                     @RequestBody @Valid RefundRejectDTO dto) {
        orderService.rejectRefund(UserContext.getUserId(), orderNo, dto.getReason());
        return Result.ok("已拒绝退款，买家可申请客服介入", null);
    }

    @Operation(summary = "我卖出的（分页，可按状态筛选）")
    @GetMapping("/seller/page")
    public Result<IPage<OrderListVO>> sellerPage(OrderQueryDTO query) {
        return Result.ok(orderService.sellerPage(UserContext.getUserId(), query));
    }

    // ==================== 双方共用 ====================

    @Operation(summary = "订单详情（买家 / 卖家 / 管理员 / 客服可查看，含操作日志时间线）")
    @GetMapping("/{orderNo}")
    public Result<OrderDetailVO> detail(@Parameter(description = "订单号") @PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo, UserContext.getUserId(), UserContext.getRole()));
    }

    // ==================== 管理端 ====================

    @Operation(summary = "全部订单（管理端，支持订单号/买卖家昵称/状态/下单时间范围）")
    @GetMapping("/admin/page")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<IPage<OrderListVO>> adminPage(OrderQueryDTO query) {
        return Result.ok(orderService.adminPage(query));
    }

    @Operation(summary = "管理端各状态订单数（标签页角标）")
    @GetMapping("/admin/count")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<OrderCountVO> adminCount() {
        return Result.ok(orderService.adminCount());
    }

    @Operation(summary = "管理端订单详情（含操作日志）")
    @GetMapping("/admin/{orderNo}")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<OrderDetailVO> adminDetail(@Parameter(description = "订单号") @PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo, UserContext.getUserId(), UserContext.getRole()));
    }

    @Operation(summary = "管理员强制取消（订单未完成时可用，已付款订单会恢复商品上架）")
    @PostMapping("/{orderNo}/admin-cancel")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> adminCancel(@Parameter(description = "订单号") @PathVariable String orderNo,
                                    @RequestBody @Valid AdminOrderOperateDTO dto) {
        orderService.adminCancel(orderNo, UserContext.getUserId(), UserContext.getRole(), dto.getReason());
        return Result.ok("订单已强制取消", null);
    }

    @Operation(summary = "管理员强制退款（客服介入后，已支付订单 → 已退款）")
    @PostMapping("/{orderNo}/admin-refund")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> adminRefund(@Parameter(description = "订单号") @PathVariable String orderNo,
                                    @RequestBody @Valid AdminOrderOperateDTO dto) {
        orderService.adminRefund(orderNo, UserContext.getUserId(), UserContext.getRole(), dto.getReason());
        return Result.ok("已强制退款", null);
    }
}
