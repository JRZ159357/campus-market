// 文件路径: src/main/java/com/campus/market/vo/ReviewOrderVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单维度的评价信息：一笔订单最多两个评价（买卖各一），
 * 订单详情页要同时展示「我的评价」和「对方的评价」，所以这里一次返回两条。
 * <p>
 * canReview / canFollow / myReviewId 由服务端算好，前端只按布尔值渲染按钮，
 * 与订单模块「可执行动作全部由服务端判定」的做法保持一致。
 */
@Data
@Schema(description = "订单评价信息")
public class ReviewOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品标题")
    private String productTitle;

    @Schema(description = "商品封面")
    private String productCover;

    @Schema(description = "成交价")
    private BigDecimal price;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "买家对卖家的评价")
    private ReviewVO buyerReview;

    @Schema(description = "卖家对买家的评价")
    private ReviewVO sellerReview;

    @Schema(description = "当前查看者的评价方向:1买家评卖家 2卖家评买家，非参与者为null")
    private Integer myReviewType;

    @Schema(description = "当前查看者是否还能评价")
    private Boolean canReview;

    @Schema(description = "当前查看者是否还能追评")
    private Boolean canFollow;

    @Schema(description = "当前查看者自己那条评价的ID，未评价为null")
    private Long myReviewId;

    @Schema(description = "追评截止时间（完成时间+7天）")
    private LocalDateTime followDeadline;
}
