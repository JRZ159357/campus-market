// 文件路径: src/main/java/com/campus/market/vo/ChatSessionDetailVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 会话详情：会话头信息 + 最近一页消息。
 * <p>
 * 进入聊天页只需要这一个接口，避免「先查会话、再查消息」两次请求导致首屏闪动。
 * 消息按时间正序返回（前端列表从旧到新渲染），与分页接口的倒序不同，这里在 Service 里做了翻转。
 */
@Data
@Schema(description = "会话详情")
public class ChatSessionDetailVO {

    @Schema(description = "会话信息")
    private ChatSessionVO session;

    @Schema(description = "最近消息（时间正序）")
    private List<ChatMessageVO> messages;

    @Schema(description = "是否还有更早的消息")
    private Boolean hasMore;
}
