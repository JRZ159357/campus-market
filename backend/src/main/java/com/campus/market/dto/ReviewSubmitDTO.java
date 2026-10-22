// 文件路径: src/main/java/com/campus/market/dto/ReviewSubmitDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交评价入参。
 * <p>
 * 星级用 1-5 的整数而不是 1.0-5.0 的小数：需求里的三维度打分是「点几颗星」，
 * 不存在半星，整数能直接落到 tinyint 列，也省掉浮点比较的坑。
 * <p>
 * 具体校哪个维度、哪些必须填，由 Service 按 reviewType 判定：
 * 买家评卖家必须有描述/发货/态度，卖家评买家必须有付款速度/态度。
 * 这里只做「传了就必在 1-5 之间」的格式校验，不按方向写死，
 * 因为一个 DTO 要同时承载两个方向。
 */
@Data
@Schema(description = "提交评价参数")
public class ReviewSubmitDTO {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderId;

    @NotNull(message = "评价方向不能为空")
    @Schema(description = "评价方向:1买家评卖家 2卖家评买家", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer reviewType;

    @Schema(description = "描述相符1-5，买家评卖家必填")
    private Integer descStars;

    @Schema(description = "发货速度1-5，买家评卖家必填")
    private Integer shipStars;

    @Schema(description = "沟通态度1-5，两个方向都必填")
    private Integer attitudeStars;

    @Schema(description = "付款速度1-5，卖家评买家必填")
    private Integer payStars;

    @Size(max = 500, message = "评价内容最多500字")
    @Schema(description = "文字评价，选填")
    private String content;

    @Size(max = 3, message = "评价图片最多3张")
    @Schema(description = "图片地址列表，最多3张")
    private List<String> images;

    @Schema(description = "是否匿名:true匿名")
    private Boolean isAnonymous;
}
