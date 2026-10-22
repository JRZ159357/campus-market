// 文件路径: src/main/java/com/campus/market/dto/ReviewReplyDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 卖家回复评价入参。
 * <p>
 * 只有「买家评卖家」的评价才允许回复：那是别人对自己的评价，
 * 卖家需要解释或致谢；而卖家对买家的评价，买家没有回复入口——
 * 否则一来一回会变成争吵区，平台不提供这种场地。
 */
@Data
@Schema(description = "卖家回复参数")
public class ReviewReplyDTO {

    @NotNull(message = "评价ID不能为空")
    @Schema(description = "评价ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reviewId;

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容最多500字")
    @Schema(description = "回复内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
