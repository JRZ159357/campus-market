// 文件路径: src/main/java/com/campus/market/vo/ProductDetailVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情。在小程序详情页与管理端审核弹窗共用。
 */
@Data
@Schema(description = "商品详情")
public class ProductDetailVO {

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "卖家ID")
    private Long sellerId;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "商品描述")
    private String description;

    @Schema(description = "售价")
    private BigDecimal price;

    @Schema(description = "原价")
    private BigDecimal originalPrice;

    @Schema(description = "全部图片，按sort升序，第一张为主图")
    private List<String> images;

    @Schema(description = "状态:0待审核 1已上架 2已下架 3已售出 4违规下架")
    private Integer status;

    @Schema(description = "状态文案")
    private String statusLabel;

    @Schema(description = "浏览数")
    private Integer viewCount;

    @Schema(description = "收藏数")
    private Integer favoriteCount;

    @Schema(description = "当前访问者是否已收藏该商品；未登录时为 false")
    private Boolean favorited;

    @Schema(description = "卖家是否认证学生")
    private Boolean isVerifiedSeller;

    @Schema(description = "审核驳回原因 / 强制下架原因")
    private String auditReason;

    @Schema(description = "发布时间")
    private LocalDateTime createTime;

    @Schema(description = "当前访问者是否为卖家本人")
    private Boolean isOwner;

    @Schema(description = "卖家信息")
    private ProductSellerVO seller;
}
