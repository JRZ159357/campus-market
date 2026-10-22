// 文件路径: src/main/java/com/campus/market/dto/ChatSessionCreateDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建 / 获取会话参数。
 * <p>
 * 会话的双方固定为「当前登录用户 = 买家」与「sellerId = 卖家」：
 * 谁先发起咨询谁就是买家，之后同一对买卖家 + 同一商品只会有一个会话。
 */
@Data
@Schema(description = "创建会话参数")
public class ChatSessionCreateDTO {

    @NotNull(message = "卖家ID不能为空")
    @Schema(description = "卖家ID")
    private Long sellerId;

    @Schema(description = "关联商品ID，不传表示与商品无关的通用咨询")
    private Long productId;
}
