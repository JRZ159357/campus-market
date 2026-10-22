// 文件路径: src/main/java/com/campus/market/vo/ChatMessageVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 聊天消息。
 * <p>
 * {@code self} 表示「这条是不是我发的」，由服务端按当前登录用户计算：
 * 前端渲染气泡时只看这个字段决定左右布局，不必比较 senderId，客服查看记录时也不会错位。
 * <p>
 * 卡片类消息（商品 / 订单）的 content 是 JSON，直接给前端解析容易随字段改名而崩，
 * 因此服务端额外解析成 {@code card} 这个 Map 返回；content 仍保留原文，便于排查历史数据。
 */
@Data
@Schema(description = "聊天消息")
public class ChatMessageVO {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "发送者ID")
    private Long senderId;

    @Schema(description = "发送者昵称")
    private String senderNickname;

    @Schema(description = "发送者头像")
    private String senderAvatar;

    @Schema(description = "接收者ID")
    private Long receiverId;

    @Schema(description = "消息类型:1文本 2图片 3系统消息 4商品卡片 5订单卡片")
    private Integer msgType;

    @Schema(description = "消息类型名称")
    private String msgTypeLabel;

    @Schema(description = "文本内容 / 图片URL / 卡片JSON原文")
    private String content;

    @Schema(description = "卡片内容（仅商品/订单卡片有值）")
    private Map<String, Object> card;

    @Schema(description = "是否已读:0未读 1已读")
    private Integer isRead;

    @Schema(description = "是否为当前登录用户发送")
    private Boolean self;

    @Schema(description = "是否由买家发出（客服监控时用于区分气泡颜色）")
    private Boolean fromBuyer;

    @Schema(description = "发送时间")
    private LocalDateTime createTime;
}
