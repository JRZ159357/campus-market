// 文件路径: src/main/java/com/campus/market/vo/ReviewPendingVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待评价订单（个人中心「待评价」列表 / 角标）。
 * <p>
 * 与订单列表 VO 的区别：这里只关心「评价这一件事」，
 * 所以带出 peerNickname（要评价的人）与 reviewType（点进去后是评卖家还是评买家），
 * 前端拿到就能直接跳评价页，不用再判断一次自己是买家还是卖家。
 */
@Data
@Schema(description = "待评价订单")
public class ReviewPendingVO implements Serializable {

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

    @Schema(description = "对方用户ID")
    private Long peerId;

    @Schema(description = "对方昵称")
    private String peerNickname;

    @Schema(description = "我要提交的评价方向:1买家评卖家 2卖家评买家")
    private Integer reviewType;

    @Schema(description = "评价方向名称")
    private String reviewTypeLabel;
}
