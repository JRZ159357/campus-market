// 文件路径: src/main/java/com/campus/market/dto/StudentAuthAuditDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生认证审核入参。
 */
@Data
@Schema(description = "学生认证审核入参")
public class StudentAuthAuditDTO {

    @NotNull(message = "审核结果不能为空")
    @Min(value = 1, message = "审核结果只能为1(通过)或2(驳回)")
    @Max(value = 2, message = "审核结果只能为1(通过)或2(驳回)")
    @Schema(description = "审核结果:1通过 2驳回", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Size(max = 200, message = "驳回原因不能超过200个字符")
    @Schema(description = "驳回原因，驳回时必填")
    private String rejectReason;
}
