// 文件路径: src/main/java/com/campus/market/dto/ReviewFollowDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 追评入参。
 * <p>
 * 追评只追加内容与图片，不重新打分：星级代表「这次交易的整体感受」，
 * 允许事后改分等于给了买家一个"不满意就改成一星"的威胁手段，
 * 对卖家不公平；追评只是补充说明，所以不带 stars 字段。
 */
@Data
@Schema(description = "追评参数")
public class ReviewFollowDTO {

    @NotNull(message = "评价ID不能为空")
    @Schema(description = "评价ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reviewId;

    @Size(max = 500, message = "追评内容最多500字")
    @Schema(description = "追评内容")
    private String content;

    @Size(max = 3, message = "追评图片最多3张")
    @Schema(description = "追评图片列表，最多3张")
    private List<String> images;
}
