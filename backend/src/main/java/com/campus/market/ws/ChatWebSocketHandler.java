// 文件路径: src/main/java/com/campus/market/ws/ChatWebSocketHandler.java
package com.campus.market.ws;

import com.campus.market.dto.ChatSendDTO;
import com.campus.market.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 聊天 WebSocket 处理器。
 * <p>
 * 上下行分工：
 * <ul>
 *   <li><b>下行（服务端 → 客户端）</b>：新消息、已读回执、系统通知、未读数变化，全部由
 *       {@link WsSessionManager#push} 主动推送；</li>
 *   <li><b>上行（客户端 → 服务端）</b>：只处理 ping / read / typing 三类轻量帧。
 *       真正「发消息」仍走 REST 接口 {@code POST /api/chat/message}，
 *       因为发消息需要落库 + 参数校验 + 返回消息 ID，走 HTTP 能拿到明确的成功/失败结果；
 *       塞进 WebSocket 就只能靠自定义 ack 来确认，反而更复杂。</li>
 * </ul>
 * 「read」上行帧是为了让已读回执实时到达对方：用户打开聊天页时前端发一帧 read，
 * 服务端清未读并给对方的在线连接推一帧 read，对方界面上的「未读」立刻消失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WsSessionManager sessionManager;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userIdOf(session);
        if (userId == null) {
            closeQuietly(session);
            return;
        }
        sessionManager.register(userId, session);
        // 连接建立后立刻推一次未读数，前端角标无需额外请求
        sessionManager.push(userId, WsFrame.of(WsFrame.TYPE_UNREAD, chatService.unreadSummary(userId)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = userIdOf(session);
        if (userId == null) {
            closeQuietly(session);
            return;
        }
        // 收到任意帧都算活跃，用于心跳续期
        sessionManager.touch(session);

        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sessionManager.push(userId, WsFrame.error("消息格式错误，应为 JSON"));
            return;
        }
        String type = root.path("type").asText("");
        JsonNode data = root.path("data");

        try {
            switch (type) {
                case WsFrame.TYPE_PING -> sessionManager.push(userId, WsFrame.pong());
                case WsFrame.TYPE_READ -> handleRead(userId, data);
                case WsFrame.TYPE_TYPING -> handleTyping(userId, data);
                case WsFrame.TYPE_MESSAGE -> handleSend(userId, data);
                default -> sessionManager.push(userId, WsFrame.error("不支持的帧类型：" + type));
            }
        } catch (Exception e) {
            log.warn("处理 WebSocket 帧失败 userId={} type={}：{}", userId, type, e.getMessage());
            sessionManager.push(userId, WsFrame.error(e.getMessage()));
        }
    }

    /**
     * 已读回执：清掉自己的未读，并把回执推给对方。
     */
    private void handleRead(Long userId, JsonNode data) {
        Long sessionId = data.path("sessionId").asLong(0);
        if (sessionId <= 0) {
            return;
        }
        Long peerId = chatService.markRead(userId, sessionId);
        if (peerId != null) {
            sessionManager.push(peerId, WsFrame.of(WsFrame.TYPE_READ,
                    java.util.Map.of("sessionId", sessionId, "readerId", userId)));
        }
        sessionManager.push(userId, WsFrame.of(WsFrame.TYPE_UNREAD, chatService.unreadSummary(userId)));
    }

    /**
     * 正在输入：纯转发，不落库。对方不在线就丢弃——没有必要为瞬时状态做离线补偿。
     */
    private void handleTyping(Long userId, JsonNode data) {
        Long sessionId = data.path("sessionId").asLong(0);
        if (sessionId <= 0) {
            return;
        }
        Long peerId = chatService.peerOf(userId, sessionId);
        if (peerId != null) {
            sessionManager.push(peerId, WsFrame.of(WsFrame.TYPE_TYPING,
                    java.util.Map.of("sessionId", sessionId, "userId", userId)));
        }
    }

    /**
     * 兼容通过 WebSocket 发消息的客户端（H5 端可能更愿意只用一条连接）。
     * 与 REST 接口走同一个 Service 方法，保证两条路径的校验与落库逻辑完全一致。
     */
    private void handleSend(Long userId, JsonNode data) {
        ChatSendDTO dto = new ChatSendDTO();
        dto.setSessionId(data.path("sessionId").asLong(0));
        dto.setMsgType(data.path("msgType").asInt(1));
        dto.setContent(data.path("content").asText(""));
        chatService.sendMessage(userId, dto);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket 传输异常：{}", exception.getMessage());
        Long userId = userIdOf(session);
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
        closeQuietly(session);
    }

    private Long userIdOf(WebSocketSession session) {
        Object value = session.getAttributes().get(ChatHandshakeInterceptor.ATTR_USER_ID);
        return value instanceof Long id ? id : null;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        } catch (Exception ignored) {
            // 连接已断，忽略
        }
    }
}
