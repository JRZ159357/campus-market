// 文件路径: src/main/java/com/campus/market/config/WebSocketConfig.java
package com.campus.market.config;

import com.campus.market.ws.ChatHandshakeInterceptor;
import com.campus.market.ws.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 配置。
 * <p>
 * 端点：{@code ws://host:8080/ws/chat?token=xxx}。
 * <ul>
 *   <li>路径刻意放在 {@code /api/**} 之外：{@code AuthInterceptor} 只拦 {@code /api/**}，
 *       而 WebSocket 握手本来也不经过 MVC 拦截器，鉴权由 HandshakeInterceptor 负责；</li>
 *   <li>{@code setAllowedOriginPatterns("*")} 仅用于开发期（管理端 5173 与小程序都要连），
 *       生产应改为具体域名，或由 Nginx 同源代理后收紧；</li>
 *   <li>容器级超时设为 5 分钟，配合应用层 30 秒心跳：心跳保证连接活跃，
 *       这个超时只是兜底，防止异常客户端长期占用线程。</li>
 * </ul>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /**
     * 单帧最大 64KB（足够发图片 URL 与卡片 JSON）、空闲超时 5 分钟、发送缓冲 512KB。
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        container.setMaxSessionIdleTimeout(300_000L);
        return container;
    }
}
