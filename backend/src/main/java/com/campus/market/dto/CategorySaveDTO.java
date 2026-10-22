// 文件路径: src/main/java/com/campus/market/dto/CategorySaveDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新增 / 编辑入参。
 */
@Data
@Schema(description = "分类保存参数")
public class CategorySaveDTO {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 30, message = "分类名称不能超过30个字")
    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 500, message = "图标地址过长")
    @Schema(description = "图标：图片地址或emoji文本")
    private String icon;

    @Schema(description = "排序值，越小越靠前；新增不传时自动追加到末尾")
    private Integer sort;

    @Schema(description = "状态:0禁用 1启用，新增默认启用")
    private Integer status;
}
