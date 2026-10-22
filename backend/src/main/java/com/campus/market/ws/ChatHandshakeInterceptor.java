// 文件路径: src/main/java/com/campus/market/ws/ChatHandshakeInterceptor.java
package com.campus.market.ws;

import com.campus.market.common.RedisKey;
import com.campus.market.entity.SysUser;
import com.campus.market.service.UserService;
import com.campus.market.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权。
 * <p>
 * 为什么不在 {@code /ws/chat} 前面挂 {@code AuthInterceptor}：那是 Spring MVC 的拦截器，
 * 只作用于 DispatcherServlet 处理的 HTTP 请求；WebSocket 握手走的是另一条链路，
 * 升级成功后由 {@code WebSocketHandler} 接管，MVC 拦截器根本不会执行。
 * 因此鉴权必须放在握手阶段（HandshakeInterceptor），一旦放行就建立长连接，
 * 后续每一帧不再重复校验——这也是长连接比轮询省资源的原因，但代价是「token 过期后连接依然有效」，
 * 所以这里额外把 userId 记进 session，业务写操作仍会走 REST 接口做完整校验。
 * <p>
 * token 取参顺序：URL 查询参数 {@code ?token=xxx} 优先，其次请求头。小程序
 * {@code wx.connectSocket} 的 header 在部分基础库版本不可靠，用查询参数兼容性最好。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    /** 握手成功后写入 WebSocketSession.attributes 的 userId key */
    public static final String ATTR_USER_ID = "userId";

    private static final String HEADER_AUTH = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final UserService userService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            log.debug("WebSocket 握手拒绝：缺少 token");
            return false;
        }
        Long userId = jwtUtil.parseUserId(token);
        if (userId == null) {
            log.debug("WebSocket 握手拒绝：token 无法解析");
            return false;
        }
        // 与 HTTP 接口一致的白名单校验，保证「退出登录 / 被顶下线」后长连接也拿不到身份
        String cached = redis.opsForValue().get(RedisKey.LOGIN_TOKEN + userId);
        if (!StringUtils.hasText(cached) || !cached.equals(token)) {
            log.debug("WebSocket 握手拒绝：token 已失效 userId={}", userId);
            return false;
        }
        SysUser user = userService.getCached(userId);
        if (user == null || Integer.valueOf(1).equals(user.getStatus())) {
            log.debug("WebSocket 握手拒绝：用户不可用 userId={}", userId);
            return false;
        }
        attributes.put(ATTR_USER_ID, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    private String resolveToken(ServerHttpRequest request) {
        String fromQuery = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("token");
        if (StringUtils.hasText(fromQuery)) {
            return fromQuery.trim();
        }
        String authorization = request.getHeaders().getFirst(HEADER_AUTH);
        if (StringUtils.hasText(authorization)) {
            return authorization.startsWith(BEARER_PREFIX)
                    ? authorization.substring(BEARER_PREFIX.length()).trim()
                    : authorization.trim();
        }
        String token = request.getHeaders().getFirst("token");
        return StringUtils.hasText(token) ? token.trim() : null;
    }
}
