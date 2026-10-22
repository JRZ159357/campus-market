// 文件路径: src/main/java/com/campus/market/ws/WsFrame.java
package com.campus.market.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

/**
 * WebSocket 帧的统一信封。
 * <p>
 * 所有下行消息都是 {@code {"type": "...", "data": {...}, "timestamp": 1690000000000}}，
 * 前端只按 type 分发，业务字段一律放在 data 里。好处是协议只有一层，新增消息种类不用改解析逻辑。
 * <p>
 * 协议类型：
 * <ul>
 *   <li>{@code message}：新聊天消息，data 是一条 ChatMessageVO；</li>
 *   <li>{@code read}：对方已读回执，data = { sessionId, readerId }；</li>
 *   <li>{@code typing}：对方正在输入，data = { sessionId, userId }；</li>
 *   <li>{@code notice}：新系统通知，data 是一条 NoticeVO；</li>
 *   <li>{@code unread}：未读数变化，data = { chatUnread, noticeUnread }；</li>
 *   <li>{@code pong}：心跳响应；</li>
 *   <li>{@code error}：鉴权或参数错误提示。</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class WsFrame {

    public static final String TYPE_MESSAGE = "message";
    public static final String TYPE_READ = "read";
    public static final String TYPE_TYPING = "typing";
    public static final String TYPE_NOTICE = "notice";
    public static final String TYPE_UNREAD = "unread";
    public static final String TYPE_PING = "ping";
    public static final String TYPE_PONG = "pong";
    public static final String TYPE_ERROR = "error";

    private String type;

    private Object data;

    private Long timestamp;

    public static WsFrame of(String type, Object data) {
        WsFrame frame = new WsFrame();
        frame.type = type;
        frame.data = data;
        frame.timestamp = System.currentTimeMillis();
        return frame;
    }

    public static WsFrame pong() {
        return of(TYPE_PONG, Map.of("serverTime", System.currentTimeMillis()));
    }

    public static WsFrame error(String msg) {
        return of(TYPE_ERROR, Map.of("msg", msg == null ? "未知错误" : msg));
    }
}
