// 文件路径: src/main/java/com/campus/market/ws/WsSessionManager.java
package com.campus.market.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 连接注册表。
 * <p>
 * 一个用户可能同时有多个连接（例如小程序和网页同时在线），因此 userId → Set&lt;session&gt;。
 * 所有对集合的操作都用 ConcurrentHashMap 提供的原子方法完成，避免自己加锁。
 * <p>
 * 三个职责：
 * <ol>
 *   <li><b>注册 / 注销</b>：连接建立时挂上，断开时摘掉，最后一个连接断开才算离线；</li>
 *   <li><b>推送</b>：给某个用户的所有连接写一帧。对方不在线时直接跳过——消息已经落库，
 *       他下次上线拉历史消息就能看到，这正是「离线消息」的实现方式（存数据库 + 上线拉取），
 *       不需要额外的离线队列；</li>
 *   <li><b>心跳超时清理</b>：客户端每 30 秒 ping 一次，服务端每 30 秒扫一遍，
 *       超过 90 秒没动静的连接主动断开，防止 NAT 超时或小程序切后台留下的僵尸连接占用资源。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsSessionManager {

    /** 心跳超时阈值：客户端 30 秒一次 ping，允许漏掉两次 */
    private static final long IDLE_TIMEOUT_MS = 90_000L;

    private final ObjectMapper objectMapper;

    /** userId -> 该用户的所有连接 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /** sessionId -> 最近一次活动时间，用于心跳超时清理 */
    private final Map<String, Long> lastActive = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        lastActive.put(session.getId(), System.currentTimeMillis());
        log.debug("WebSocket 连接建立 userId={} sessionId={} 当前连接数={}",
                userId, session.getId(), countConnections(userId));
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessions.remove(userId);
            }
        }
        lastActive.remove(session.getId());
        log.debug("WebSocket 连接关闭 userId={} sessionId={}", userId, session.getId());
    }

    /** 心跳续期，由 Handler 在收到任意帧时调用 */
    public void touch(WebSocketSession session) {
        lastActive.put(session.getId(), System.currentTimeMillis());
    }

    public boolean isOnline(Long userId) {
        Set<WebSocketSession> set = sessions.get(userId);
        return set != null && !set.isEmpty();
    }

    public int countConnections(Long userId) {
        Set<WebSocketSession> set = sessions.get(userId);
        return set == null ? 0 : set.size();
    }

    /**
     * 给指定用户推送一帧。返回是否至少有一个连接收到了。
     * 单个连接写失败不影响其他连接，失败即视为该连接已死，顺手摘掉。
     */
    public boolean push(Long userId, WsFrame frame) {
        if (userId == null) {
            return false;
        }
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) {
            return false;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(frame);
        } catch (Exception e) {
            log.warn("WebSocket 帧序列化失败：{}", e.getMessage());
            return false;
        }
        boolean sent = false;
        for (WebSocketSession session : set) {
            if (!session.isOpen()) {
                unregister(userId, session);
                continue;
            }
            try {
                // WebSocketSession 不是线程安全的，同一连接的并发写必须串行
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
                sent = true;
            } catch (IOException e) {
                log.warn("WebSocket 推送失败 userId={}：{}", userId, e.getMessage());
                unregister(userId, session);
            }
        }
        return sent;
    }

    /**
     * 心跳超时清理。每 30 秒执行一次，与客户端 ping 周期一致。
     */
    @Scheduled(fixedDelay = 30_000L)
    public void closeIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.forEach((userId, set) -> {
            for (WebSocketSession session : set) {
                Long last = lastActive.get(session.getId());
                if (last == null) {
                    continue;
                }
                if (now - last > IDLE_TIMEOUT_MS) {
                    log.debug("心跳超时，关闭连接 userId={} 空闲 {}ms", userId, now - last);
                    try {
                        session.close(CloseStatus.SESSION_NOT_RELIABLE);
                    } catch (IOException ignored) {
                        // 关闭失败说明连接已断，下方 unregister 会兜底
                    }
                    unregister(userId, session);
                }
            }
        });
    }
}
