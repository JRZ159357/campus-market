// 文件路径: src/main/java/com/campus/market/dto/OrderCancelDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 买家取消订单参数。原因可选，不填时服务端写入"买家主动取消"。
 */
@Data
@Schema(description = "取消订单参数")
public class OrderCancelDTO {

    @Size(max = 100, message = "取消原因最多100个字")
    @Schema(description = "取消原因", example = "不想买了")
    private String reason;
}
