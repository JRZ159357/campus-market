// 文件路径: src/main/java/com/campus/market/dto/ChatSendDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送消息参数。
 * <p>
 * content 是「多态」的：msgType=1 传文本，=2 传图片 URL，=4/5 传卡片 JSON，
 * =3（系统消息）不允许客户端发送，由服务端在订单状态变更时写入。
 */
@Data
@Schema(description = "发送消息参数")
public class ChatSendDTO {

    @NotNull(message = "会话ID不能为空")
    @Schema(description = "会话ID")
    private Long sessionId;

    @Min(value = 1, message = "消息类型不合法")
    @Schema(description = "消息类型:1文本 2图片 4商品卡片 5订单卡片；系统消息由服务端写入")
    private Integer msgType = 1;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 1000, message = "消息内容不能超过1000字")
    @Schema(description = "文本内容 / 图片URL / 卡片JSON")
    private String content;
}
