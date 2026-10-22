// 文件路径: src/main/java/com/campus/market/dto/ProductAuditDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商品审核入参。status=1 通过，status=2 驳回（驳回必须填原因）。
 */
@Data
@Schema(description = "商品审核参数")
public class ProductAuditDTO {

    @NotNull(message = "请选择审核结果")
    @Min(value = 1, message = "审核结果取值为1通过、2驳回")
    @Max(value = 2, message = "审核结果取值为1通过、2驳回")
    @Schema(description = "审核结果:1通过 2驳回", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Size(max = 200, message = "驳回原因不能超过200个字")
    @Schema(description = "驳回原因，status=2 时必填")
    private String auditReason;
}
