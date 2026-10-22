// 文件路径: src/main/java/com/campus/market/dto/ReviewQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 评价查询参数。
 * <p>
 * 一处 DTO 同时服务四个入口：商品评价列表、用户评价列表、订单评价、管理端全量列表。
 * 传哪个字段就加哪个条件，没传即「不限」。这样做的好处是四处列表的
 * 星级区间、图片过滤、关键词语义完全一致，不会各写一套各有各的 bug。
 * <p>
 * 好评/中评/差评三个 Tab 通过 minStars / maxStars 表达（4-5 / 3 / 1-2），
 * 而不是再定义一个枚举：星级区间是纯粹的数值范围，交给 SQL 比较最直接。
 */
@Data
@Schema(description = "评价查询参数")
public class ReviewQueryDTO {

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "被评价人ID")
    private Long revieweeId;

    @Schema(description = "评价人ID")
    private Long reviewerId;

    @Schema(description = "评价方向:1买家评卖家 2卖家评买家")
    private Integer reviewType;

    @Schema(description = "订单号（模糊匹配）")
    private String orderNo;

    @Schema(description = "评价人昵称（管理端筛选用，Service 转成 ID 集合）")
    private String reviewerKeyword;

    @Schema(description = "被评价人昵称（管理端筛选用）")
    private String revieweeKeyword;

    @Schema(description = "商品标题关键词（管理端筛选用）")
    private String productKeyword;

    /*
     * 下面三个集合由 Service 内部填充，前端不要传：
     * 昵称/标题是模糊匹配，先查一把拿到 ID 集合再 IN 进评价表，
     * 比在评价表上 JOIN 用户表 / 商品表再 LIKE 要快得多，
     * 也不会因为 JOIN 让评价表的索引失效。
     */

    @Schema(hidden = true)
    private java.util.List<Long> reviewerIds;

    @Schema(hidden = true)
    private java.util.List<Long> revieweeIds;

    @Schema(hidden = true)
    private java.util.List<Long> productIds;

    @Schema(description = "最低星级")
    private Integer minStars;

    @Schema(description = "最高星级")
    private Integer maxStars;

    @Schema(description = "只看有图")
    private Boolean hasImage;

    @Schema(description = "只看有追评")
    private Boolean hasFollow;

    @Schema(description = "只看有卖家回复")
    private Boolean hasReply;

    @Schema(description = "内容关键词（匹配评价/追评/回复）")
    private String keyword;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页条数，默认10，最大100")
    private Long pageSize;
}
