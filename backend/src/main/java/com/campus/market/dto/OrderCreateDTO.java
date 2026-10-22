// 文件路径: src/main/java/com/campus/market/dto/OrderCreateDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建订单参数。校园二手交易为"一件一拍"，数量固定为 1，故不设 quantity 字段。
 */
@Data
@Schema(description = "创建订单参数")
public class OrderCreateDTO {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID")
    private Long productId;

    @NotNull(message = "请选择收货 / 交易地址")
    @Schema(description = "地址ID")
    private Long addressId;
}
