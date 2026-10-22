// 文件路径: src/main/java/com/campus/market/vo/OrderListVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表卡片。商品标题与主图取自订单快照，不从 product 表回查，
 * 这样即使商品已被删除，列表也不会出现空白卡片。
 */
@Data
@Schema(description = "订单列表项")
public class OrderListVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "状态码")
    private Integer status;

    @Schema(description = "状态文案")
    private String statusLabel;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品标题（快照）")
    private String productTitle;

    @Schema(description = "商品主图（快照）")
    private String productCover;

    @Schema(description = "成交金额")
    private BigDecimal price;

    @Schema(description = "买家ID")
    private Long buyerId;

    @Schema(description = "买家昵称")
    private String buyerNickname;

    @Schema(description = "卖家ID")
    private Long sellerId;

    @Schema(description = "卖家昵称")
    private String sellerNickname;

    @Schema(description = "下单时间")
    private LocalDateTime createTime;

    @Schema(description = "支付截止时间，待付款状态用于倒计时")
    private LocalDateTime payDeadline;
}
