// 文件路径: src/main/java/com/campus/market/dto/SensitiveWordBatchDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 敏感词批量添加入参。
 * <p>
 * 只收一个多行文本，由后端按行拆分。为什么不在前端拆完再传数组：
 * 前端拆的话「多少个字算一个词」这条规则就散落在两处，
 * 后端还要再校验一遍；而且批量弹窗里要实时显示「已识别 N 个」，
 * 前端的识别逻辑必须与后端完全一致——不如让后端做唯一的事实来源，
 * 前端只负责展示自己的预估数字（不一致时后端返回的 inserted/skipped 会纠正它）。
 */
@Data
@Schema(description = "敏感词批量添加参数")
public class SensitiveWordBatchDTO {

    @NotBlank(message = "批量内容不能为空")
    @Size(max = 20000, message = "一次最多提交 20000 个字符")
    @Schema(description = "多行文本，一行一个敏感词", requiredMode = Schema.RequiredMode.REQUIRED)
    private String text;

    @Schema(description = "统一类型:1违禁品 2辱骂 3广告 4其他，默认4其他")
    private Integer type;

    @Schema(description = "统一等级:1警告 2拦截，默认1警告")
    private Integer level;

    @Schema(description = "状态:0禁用 1启用，默认启用")
    private Integer status;
}
