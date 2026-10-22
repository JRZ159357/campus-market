// 文件路径: src/main/java/com/campus/market/vo/DashboardRankingVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 仪表盘排行榜。
 * <p>
 * 三张榜单的排序与截断都在 SQL 里完成（ORDER BY + LIMIT），
 * Java 端只做字段映射，不做全量排序。
 */
@Data
@Schema(description = "仪表盘排行榜")
public class DashboardRankingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "卖家销量榜TOP10")
    private List<SellerItem> topSellers;

    @Schema(description = "热门商品TOP10")
    private List<ProductItem> topProducts;

    @Schema(description = "热门分类TOP7")
    private List<CategoryItem> topCategories;

    /**
     * 卖家榜条目。
     */
    @Data
    @Schema(description = "卖家榜条目")
    public static class SellerItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "卖家用户ID")
        private Long sellerId;

        @Schema(description = "昵称")
        private String nickname;

        @Schema(description = "头像")
        private String avatar;

        @Schema(description = "在售商品数")
        private Long productCount;

        @Schema(description = "已售出订单数")
        private Long soldCount;

        @Schema(description = "成交额(元)")
        private BigDecimal amount;

        @Schema(description = "信誉分")
        private Integer creditScore;

        @Schema(description = "信誉分等级名")
        private String creditLevel;

        @Schema(description = "信誉分等级配色")
        private String creditColor;
    }

    /**
     * 热门商品条目。
     */
    @Data
    @Schema(description = "热门商品条目")
    public static class ProductItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "商品ID")
        private Long productId;

        @Schema(description = "标题")
        private String title;

        @Schema(description = "价格")
        private BigDecimal price;

        @Schema(description = "浏览数")
        private Long viewCount;

        @Schema(description = "收藏数")
        private Long favoriteCount;

        @Schema(description = "主图")
        private String cover;
    }

    /**
     * 热门分类条目。
     */
    @Data
    @Schema(description = "热门分类条目")
    public static class CategoryItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "分类ID")
        private Long categoryId;

        @Schema(description = "分类名")
        private String name;

        @Schema(description = "商品数")
        private Long productCount;

        @Schema(description = "总浏览量")
        private Long viewCount;
    }
}
