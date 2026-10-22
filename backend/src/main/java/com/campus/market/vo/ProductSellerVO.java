// 文件路径: src/main/java/com/campus/market/vo/ProductSellerVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品详情页的卖家信息卡片。
 */
@Data
@Schema(description = "商品卖家信息")
public class ProductSellerVO {

    @Schema(description = "卖家ID")
    private Long id;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "信誉分")
    private Integer creditScore;

    @Schema(description = "是否认证学生")
    private Boolean studentVerified;

    @Schema(description = "在售商品数")
    private Long onSaleCount;

    @Schema(description = "粉丝数")
    private Integer fansCount;

    @Schema(description = "当前访问者是否已关注该卖家；未登录或为卖家本人时为 false")
    private Boolean followed;
}
