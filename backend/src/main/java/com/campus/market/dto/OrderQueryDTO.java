// 文件路径: src/main/java/com/campus/market/dto/OrderQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单列表查询参数。买家页 / 卖家页 / 管理端共用，管理端额外使用订单号、昵称与时间区间。
 */
@Data
@Schema(description = "订单查询参数")
public class OrderQueryDTO {

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页条数，默认10，最大100")
    private Long pageSize;

    @Schema(description = "订单状态，不传为全部")
    private Integer status;

    @Schema(description = "订单号，支持模糊匹配（仅管理端）")
    private String orderNo;

    @Schema(description = "买家昵称关键词（仅管理端）")
    private String buyerKeyword;

    @Schema(description = "卖家昵称关键词（仅管理端）")
    private String sellerKeyword;

    @Schema(description = "下单时间起，格式 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String startTime;

    @Schema(description = "下单时间止，格式 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String endTime;
}
