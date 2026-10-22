// 文件路径: src/main/java/com/campus/market/vo/FavoriteProductVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的收藏列表项。
 * <p>
 * 字段命名与 {@link ProductListVO} 对齐，小程序端可直接复用 product-card 组件；
 * 额外带上 favoriteId（游标，取消收藏时定位）与 favoriteTime（列表展示），
 * 以及商品最新状态，用于已售/已下架的灰色蒙层。
 */
@Data
@Schema(description = "收藏商品列表项")
public class FavoriteProductVO {

    @Schema(description = "收藏记录ID，同时作为游标分页的 cursor")
    private Long favoriteId;

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "卖家ID")
    private Long sellerId;

    @Schema(description = "卖家昵称")
    private String sellerNickname;

    @Schema(description = "卖家头像")
    private String sellerAvatar;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "售价")
    private BigDecimal price;

    @Schema(description = "原价")
    private BigDecimal originalPrice;

    @Schema(description = "主图地址")
    private String cover;

    @Schema(description = "商品最新状态:0待审核 1已上架 2已下架 3已售出 4违规下架")
    private Integer status;

    @Schema(description = "状态文案，仅非在售状态展示蒙层")
    private String statusLabel;

    @Schema(description = "浏览数")
    private Integer viewCount;

    @Schema(description = "收藏数")
    private Integer favoriteCount;

    @Schema(description = "卖家是否认证学生:0否 1是")
    private Integer isVerifiedSeller;

    @Schema(description = "收藏时间")
    private LocalDateTime favoriteTime;
}
