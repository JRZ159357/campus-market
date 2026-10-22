// 文件路径: src/main/java/com/campus/market/dto/SensitiveWordSaveDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 敏感词新增 / 编辑入参。
 */
@Data
@Schema(description = "敏感词保存参数")
public class SensitiveWordSaveDTO {

    @NotBlank(message = "敏感词不能为空")
    @Size(max = 50, message = "敏感词不能超过50个字")
    @Schema(description = "敏感词", requiredMode = Schema.RequiredMode.REQUIRED)
    private String word;

    @Schema(description = "类型:1违禁品 2辱骂 3广告 4其他，默认4其他")
    private Integer type;

    @Schema(description = "等级:1警告 2拦截，默认1警告")
    private Integer level;

    @Schema(description = "状态:0禁用 1启用，新增默认启用")
    private Integer status;
}
