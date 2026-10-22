// 文件路径: src/main/java/com/campus/market/dto/AdminUserStatusDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 封禁 / 解封入参。管理端专用。
 */
@Data
@Schema(description = "封禁解封入参")
public class AdminUserStatusDTO {

    @Min(value = 0, message = "状态取值不合法")
    @Max(value = 1, message = "状态取值不合法")
    @Schema(description = "目标状态：0正常 1封禁", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Size(max = 200, message = "备注不能超过 200 个字")
    @Schema(description = "封禁备注")
    private String reason;
}
