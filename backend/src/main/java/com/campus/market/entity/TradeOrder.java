// 文件路径: src/main/java/com/campus/market/entity/TradeOrder.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易订单实体，对应表 trade_order。
 * <p>
 * productSnapshot / addressSnapshot 是 JSON 文本，业务层用 Jackson 序列化 VO 后写入，
 * 不做成关联表：订单一旦生成就不应随商品或地址的后续修改而变化。
 */
@Data
@TableName("trade_order")
@Schema(description = "交易订单")
public class TradeOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "买家ID")
    private Long buyerId;

    @Schema(description = "卖家ID")
    private Long sellerId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品快照JSON")
    private String productSnapshot;

    @Schema(description = "成交价格")
    private BigDecimal price;

    @Schema(description = "状态:0待付款 1待发货 2待收货 3已完成 4已取消 5退款中 6已退款")
    private Integer status;

    @Schema(description = "申请退款前的状态，卖家拒绝退款时回退用")
    private Integer refundFromStatus;

    @Schema(description = "地址快照JSON")
    private String addressSnapshot;

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

    @Schema(description = "买家是否已评价:0未评 1已评")
    private Integer buyerReviewed;

    @Schema(description = "卖家是否已评价:0未评 1已评")
    private Integer sellerReviewed;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "下单时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableLogic
    @Schema(description = "逻辑删除:0未删 1已删")
    private Integer deleted;
}
