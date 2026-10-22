// 文件路径: src/main/java/com/campus/market/vo/OrderProductSnapshotVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单内的商品快照。
 * 下单时把商品的关键信息冻结进 trade_order.product_snapshot，
 * 之后卖家改价、改标题甚至删除商品，历史订单展示的仍是成交当时的信息。
 */
@Data
@Schema(description = "订单商品快照")
public class OrderProductSnapshotVO {

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "主图")
    private String cover;

    @Schema(description = "成交价")
    private BigDecimal price;

    @Schema(description = "原价")
    private BigDecimal originalPrice;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "商品描述（截断到 120 字，仅供订单页展示）")
    private String description;

    @Schema(description = "卖家是否认证学生")
    private Boolean isVerifiedSeller;
}
