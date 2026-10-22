// 文件路径: src/main/java/com/campus/market/vo/OrderDetailVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情。
 * <p>
 * canXxx 系列字段由服务端按"当前访问者角色 + 订单状态"计算后下发，
 * 前端只负责按布尔值渲染按钮。这样即便前端改了判断逻辑，
 * 也不会出现"按钮可见但接口 403"的割裂体验。
 */
@Data
@Schema(description = "订单详情")
public class OrderDetailVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "状态码")
    private Integer status;

    @Schema(description = "状态文案")
    private String statusLabel;

    @Schema(description = "成交金额")
    private BigDecimal price;

    @Schema(description = "买家ID")
    private Long buyerId;

    @Schema(description = "买家昵称")
    private String buyerNickname;

    @Schema(description = "买家头像")
    private String buyerAvatar;

    @Schema(description = "卖家ID")
    private Long sellerId;

    @Schema(description = "卖家昵称")
    private String sellerNickname;

    @Schema(description = "卖家头像")
    private String sellerAvatar;

    @Schema(description = "商品快照")
    private OrderProductSnapshotVO product;

    @Schema(description = "地址快照")
    private AddressVO address;

    @Schema(description = "下单时间")
    private LocalDateTime createTime;

    @Schema(description = "支付截止时间")
    private LocalDateTime payDeadline;

    @Schema(description = "支付时间")
    private LocalDateTime paymentTime;

    @Schema(description = "发货时间")
    private LocalDateTime shipTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "取消时间")
    private LocalDateTime cancelTime;

    @Schema(description = "退款完成时间")
    private LocalDateTime refundTime;

    @Schema(description = "取消原因")
    private String cancelReason;

    @Schema(description = "退款原因")
    private String refundReason;

    @Schema(description = "拒绝退款原因")
    private String rejectRefundReason;

    @Schema(description = "申请退款前的状态")
    private Integer refundFromStatus;

    @Schema(description = "操作日志（按时间升序，用于时间线）")
    private List<OrderLogVO> logs;

    @Schema(description = "当前访问者是否为买家")
    private Boolean isBuyer;

    @Schema(description = "当前访问者是否为卖家")
    private Boolean isSeller;

    @Schema(description = "是否可付款")
    private Boolean canPay;

    @Schema(description = "是否可取消")
    private Boolean canCancel;

    @Schema(description = "是否可发货")
    private Boolean canShip;

    @Schema(description = "是否可确认收货")
    private Boolean canReceive;

    @Schema(description = "是否可申请退款")
    private Boolean canApplyRefund;

    @Schema(description = "是否可同意退款")
    private Boolean canAgreeRefund;

    @Schema(description = "是否可拒绝退款")
    private Boolean canRejectRefund;

    @Schema(description = "是否可撤销退款申请")
    private Boolean canCancelRefund;

    @Schema(description = "管理员是否可强制取消")
    private Boolean canAdminCancel;

    @Schema(description = "管理员是否可强制退款")
    private Boolean canAdminRefund;

    @Schema(description = "买家是否已评价:0未评 1已评")
    private Integer buyerReviewed;

    @Schema(description = "卖家是否已评价:0未评 1已评")
    private Integer sellerReviewed;

    @Schema(description = "当前查看者自己那条评价的ID，未评价为null")
    private Long myReviewId;

    @Schema(description = "当前查看者是否可评价（已完成且自己这侧未评）")
    private Boolean canReview;

    @Schema(description = "当前查看者是否可追评（已评价、未追评、7天内）")
    private Boolean canFollowReview;

    @Schema(description = "追评截止时间（完成时间+7天）")
    private LocalDateTime reviewDeadline;
}
