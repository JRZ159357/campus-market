// 文件路径: src/main/java/com/campus/market/dto/HomeCategorySortDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 金刚区批量配置入参（模块 12）。
 * <p>
 * 一次提交的是「首页上显示哪些分类、按什么顺序」的全量快照，
 * 而不是增量打补丁：拖拽后的顺序天然是一个整体，
 * 增量接口反而要求前端算清楚「谁从第几挪到第几」，更容易出错。
 * 全量覆盖的代价是需要校验 ID 是否存在——这比顺序算错便宜得多。
 */
@Data
@Schema(description = "金刚区批量配置参数")
public class HomeCategorySortDTO {

    @NotEmpty(message = "分类列表不能为空")
    @Size(max = 100, message = "一次最多配置100个分类")
    @Valid
    @Schema(description = "分类配置项，数组顺序即金刚区顺序")
    private List<Item> items;

    @Data
    @Schema(description = "单个分类配置项")
    public static class Item {

        @NotNull(message = "分类ID不能为空")
        @Schema(description = "分类ID")
        private Long id;

        @NotNull(message = "是否显示不能为空")
        @Min(value = 0, message = "是否显示取值为0或1")
        @Max(value = 1, message = "是否显示取值为0或1")
        @Schema(description = "是否显示在首页金刚区:0否 1是")
        private Integer isHomeShow;

        @NotNull(message = "排序值不能为空")
        @Min(value = 0, message = "排序值不能为负数")
        @Schema(description = "排序值，越小越靠前")
        private Integer sort;
    }
}
