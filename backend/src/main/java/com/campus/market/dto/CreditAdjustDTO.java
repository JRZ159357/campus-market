// 文件路径: src/main/java/com/campus/market/dto/CreditAdjustDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员调整信誉分入参。
 * <p>
 * changeScore 带符号：正数加分、负数减分，一次只调一项，
 * 不用「方向 + 分值」两个字段——两个字段就有「方向选了加分但分值是负数」这种
 * 非法组合要额外校验，带符号一个字段从根上避免。
 * <p>
 * 单次幅度限制 ±100 且原因必填：信誉分是用户的核心资产，
 * 不允许一次把 0 分调到 150，也不允许无理由调分。
 */
@Data
@Schema(description = "信誉分调整参数")
public class CreditAdjustDTO {

    @NotNull(message = "调整分值不能为空")
    @Min(value = -100, message = "单次最多扣 100 分")
    @Max(value = 100, message = "单次最多加 100 分")
    @Schema(description = "调整分值，正数加分负数减分，范围 -100~100",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer changeScore;

    @NotBlank(message = "调整原因不能为空")
    @Size(max = 200, message = "调整原因最多200字")
    @Schema(description = "调整原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
