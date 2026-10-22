// 文件路径: src/main/java/com/campus/market/vo/ReviewStatVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 评价统计。商品详情页的「4.8 分 · 23 条评价 · 好评率 96%」
 * 与个人主页的评价分布都用它，字段一次给全，前端直接取用。
 */
@Data
@Schema(description = "评价统计")
public class ReviewStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "评价总数")
    private Long total;

    @Schema(description = "好评数（4-5星）")
    private Long goodCount;

    @Schema(description = "中评数（3星）")
    private Long midCount;

    @Schema(description = "差评数（1-2星）")
    private Long badCount;

    @Schema(description = "5星数")
    private Long star5;

    @Schema(description = "4星数")
    private Long star4;

    @Schema(description = "3星数")
    private Long star3;

    @Schema(description = "2星数")
    private Long star2;

    @Schema(description = "1星数")
    private Long star1;

    @Schema(description = "平均星级")
    private Double avgStars;

    @Schema(description = "带图评价数")
    private Long imageCount;

    @Schema(description = "好评率（百分比，保留一位小数）")
    private Double goodRate;
}
