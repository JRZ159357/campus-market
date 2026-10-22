// 文件路径: src/main/java/com/campus/market/vo/OrderCreateVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 下单结果。前端拿 orderNo 直接跳订单详情页继续支付，
 * payDeadline 用于页面上的付款倒计时。
 */
@Data
@Schema(description = "下单结果")
public class OrderCreateVO {

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "成交金额")
    private BigDecimal price;

    @Schema(description = "支付截止时间")
    private LocalDateTime payDeadline;
}
