// 文件路径: src/main/java/com/campus/market/dto/BannerSortDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 轮播图批量排序入参（模块 12）。
 * <p>
 * 拖拽一次会同时改动多行的 sort，如果沿用「一行一个 PUT」的做法，
 * 拖一次要发 N 个请求：不仅慢，而且中途失败会留下一半新一半旧的顺序，
 * 运营看到的是「拖了但没完全拖」。
 * 批量接口在同一个事务里一次写完，要么全生效要么全不动。
 */
@Data
@Schema(description = "轮播图批量排序参数")
public class BannerSortDTO {

    @NotEmpty(message = "排序列表不能为空")
    @Size(max = 200, message = "一次最多调整200条")
    @Valid
    @Schema(description = "排序项，数组顺序即目标顺序")
    private List<Item> items;

    @Data
    @Schema(description = "单个排序项")
    public static class Item {

        @NotNull(message = "轮播图ID不能为空")
        @Schema(description = "轮播图ID")
        private Long id;

        @NotNull(message = "排序值不能为空")
        @Min(value = 0, message = "排序值不能为负数")
        @Schema(description = "排序值，越小越靠前")
        private Integer sort;
    }
}
