// 文件路径: src/main/java/com/campus/market/vo/ProductListVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品列表项：小程序卡片、管理端审核表格共用。
 * 只含主图 cover，详情页才返回全部图片，避免列表响应过大。
 */
@Data
@Schema(description = "商品列表项")
public class ProductListVO {

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

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "售价")
    private BigDecimal price;

    @Schema(description = "原价")
    private BigDecimal originalPrice;

    @Schema(description = "主图地址")
    private String cover;

    @Schema(description = "状态:0待审核 1已上架 2已下架 3已售出 4违规下架")
    private Integer status;

    @Schema(description = "状态文案")
    private String statusLabel;

    @Schema(description = "浏览数")
    private Integer viewCount;

    @Schema(description = "收藏数")
    private Integer favoriteCount;

    @Schema(description = "卖家是否认证学生:0否 1是")
    private Integer isVerifiedSeller;

    @Schema(description = "待人工复核:0否 1是（发布时命中警告级敏感词）")
    private Integer needManualReview;

    @Schema(description = "命中的敏感词，逗号分隔")
    private String sensitiveHit;

    @Schema(description = "审核驳回原因 / 强制下架原因")
    private String auditReason;

    @Schema(description = "发布时间")
    private LocalDateTime createTime;
}
