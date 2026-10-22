// 文件路径: src/main/java/com/campus/market/dto/ForceOfflineDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员强制下架入参。必须填写原因，将展示给卖家。
 */
@Data
@Schema(description = "强制下架参数")
public class ForceOfflineDTO {

    @NotBlank(message = "请填写强制下架原因")
    @Size(max = 200, message = "下架原因不能超过200个字")
    @Schema(description = "强制下架原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
