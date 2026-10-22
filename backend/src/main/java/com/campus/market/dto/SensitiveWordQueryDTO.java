// 文件路径: src/main/java/com/campus/market/dto/SensitiveWordQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 敏感词列表查询条件。
 */
@Data
@Schema(description = "敏感词查询条件")
public class SensitiveWordQueryDTO {

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页条数，默认20")
    private Long pageSize;

    @Schema(description = "类型筛选:1违禁品 2辱骂 3广告 4其他；不传表示全部")
    private Integer type;

    @Schema(description = "状态筛选:0禁用 1启用；不传表示全部")
    private Integer status;

    /**
     * 关键词按「包含」匹配。
     * <p>
     * 词表规模在千条以内，前端要「输入两个词查有没有收录」这种用法，
     * 所以保留模糊匹配；如果将来词表上万，这里应换成前缀匹配 + 索引。
     */
    @Schema(description = "关键词，按包含匹配")
    private String keyword;
}
