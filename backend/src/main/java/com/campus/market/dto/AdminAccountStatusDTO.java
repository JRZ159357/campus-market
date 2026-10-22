// 文件路径: src/main/java/com/campus/market/dto/AdminAccountStatusDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台账号启用 / 禁用入参。
 */
@Data
@Schema(description = "后台账号状态参数")
public class AdminAccountStatusDTO {

    @NotNull(message = "状态不能为空")
    @Schema(description = "状态:0正常 1禁用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;
}
