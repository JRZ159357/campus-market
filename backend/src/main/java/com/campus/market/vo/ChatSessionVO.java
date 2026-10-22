// 文件路径: src/main/java/com/campus/market/vo/ChatSessionVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表项。
 * <p>
 * 不返回 buyerId / sellerId 两个裸 ID，而是给出「对方」的聚合信息（peerXxx）与「我是不是买家」，
 * 前端列表直接渲染即可，不需要自己判断该显示哪一方。管理端客服工作台需要同时看到双方，
 * 因此额外保留了 buyer / seller 的昵称字段。
 */
@Data
@Schema(description = "会话列表项")
public class ChatSessionVO {

    @Schema(description = "会话ID")
    private Long id;

    @Schema(description = "对方用户ID")
    private Long peerId;

    @Schema(description = "对方昵称")
    private String peerNickname;

    @Schema(description = "对方头像")
    private String peerAvatar;

    @Schema(description = "对方是否认证学生")
    private Boolean peerVerified;

    @Schema(description = "关联商品ID")
    private Long productId;

    @Schema(description = "关联商品标题")
    private String productTitle;

    @Schema(description = "关联商品封面")
    private String productCover;

    @Schema(description = "最后一条消息摘要")
    private String lastMessage;

    @Schema(description = "最后消息时间")
    private LocalDateTime lastTime;

    @Schema(description = "我的未读数")
    private Integer unread;

    @Schema(description = "我是否为本会话的买家")
    private Boolean buyer;

    @Schema(description = "买家ID（客服工作台用）")
    private Long buyerId;

    @Schema(description = "买家昵称（客服工作台用）")
    private String buyerNickname;

    @Schema(description = "卖家ID（客服工作台用）")
    private Long sellerId;

    @Schema(description = "卖家昵称（客服工作台用）")
    private String sellerNickname;
}
