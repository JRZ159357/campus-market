// 文件路径: src/main/java/com/campus/market/vo/UnreadSummaryVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 未读数汇总，用于小程序 tabBar 角标与 WebSocket 的 unread 帧。
 * <p>
 * chatUnread 是「未读消息条数」之和（不是会话数）：角标要显示的是有多少条消息没看，
 * 一个会话里 5 条未读应该显示 5。而会话列表里每个会话的红点用各自的 unread 字段。
 */
@Data
@Schema(description = "未读数汇总")
public class UnreadSummaryVO {

    @Schema(description = "未读聊天消息总数")
    private Long chatUnread;

    @Schema(description = "有未读的会话个数")
    private Long unreadSessions;

    @Schema(description = "未读系统通知数")
    private Long noticeUnread;

    @Schema(description = "tabBar 角标总数 = chatUnread + noticeUnread，超过 99 前端显示 99+")
    private Long total;
}
