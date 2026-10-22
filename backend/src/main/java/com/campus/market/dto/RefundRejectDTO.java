// 文件路径: src/main/java/com/campus/market/dto/RefundRejectDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 卖家拒绝退款参数。原因必填，买家可据此申请客服介入。
 */
@Data
@Schema(description = "拒绝退款参数")
public class RefundRejectDTO {

    @NotBlank(message = "请填写拒绝退款的原因")
    @Size(min = 2, max = 200, message = "拒绝原因需 2-200 个字")
    @Schema(description = "拒绝退款原因", example = "商品已按描述如实发出，瑕疵在下单前已说明")
    private String reason;
}
