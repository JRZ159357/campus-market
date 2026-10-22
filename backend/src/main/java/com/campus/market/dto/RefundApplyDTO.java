// 文件路径: src/main/java/com/campus/market/dto/RefundApplyDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 买家申请退款参数。原因必填且不少于 5 个字，避免"退款""不要了"这类无法判断的申请。
 */
@Data
@Schema(description = "申请退款参数")
public class RefundApplyDTO {

    @NotBlank(message = "请填写退款原因")
    @Size(min = 5, max = 200, message = "退款原因需 5-200 个字")
    @Schema(description = "退款原因", example = "商品与描述不符，外壳有明显裂痕")
    private String reason;
}
