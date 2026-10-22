// 文件路径: src/main/java/com/campus/market/dto/ProductQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表查询参数。用户端与管理端共用，管理端额外使用 status / 时间区间。
 */
@Data
@Schema(description = "商品查询参数")
public class ProductQueryDTO {

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页条数，默认10，最大100")
    private Long pageSize;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "关键词，匹配标题与描述")
    private String keyword;

    @Schema(description = "最低价格")
    private BigDecimal minPrice;

    @Schema(description = "最高价格")
    private BigDecimal maxPrice;

    @Schema(description = "排序：default综合(认证学生优先) / new最新 / priceAsc价格升 / priceDesc价格降 / hot最热")
    private String sort;

    @Schema(description = "商品状态，仅管理端使用：0待审核 1已上架 2已下架 3已售出 4违规下架")
    private Integer status;

    /**
     * 待人工复核筛选（模块11）。
     * <p>
     * 传 1 只看「发布时命中警告级敏感词」的商品，对应审核页的
     * 「待人工复核」Tab；不传则不受影响。
     * 与 status 是「与」关系：待审核 + 待人工复核 = 最该先看的那批。
     */
    @Schema(description = "待人工复核:0否 1是，仅管理端使用")
    private Integer needManualReview;

    @Schema(description = "发布时间起，格式 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String startTime;

    @Schema(description = "发布时间止，格式 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String endTime;
}
