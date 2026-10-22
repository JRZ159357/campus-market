// 文件路径: src/main/java/com/campus/market/dto/AdminOrderOperateDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员强制取消 / 强制退款参数。原因必填，写入订单与操作日志，便于事后审计。
 */
@Data
@Schema(description = "管理员干预订单参数")
public class AdminOrderOperateDTO {

    @NotBlank(message = "请填写操作原因")
    @Size(min = 2, max = 200, message = "原因需 2-200 个字")
    @Schema(description = "操作原因", example = "客服介入：商品与描述严重不符，判定退款")
    private String reason;
}
