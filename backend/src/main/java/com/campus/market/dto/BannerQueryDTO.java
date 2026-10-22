// 文件路径: src/main/java/com/campus/market/dto/BannerQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 轮播图管理端查询参数。
 */
@Data
@Schema(description = "轮播图查询参数")
public class BannerQueryDTO {

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页条数，默认10，最大100")
    private Long pageSize;

    @Schema(description = "状态:0禁用 1启用，不传为全部")
    private Integer status;

    @Schema(description = "标题关键词")
    private String keyword;
}
