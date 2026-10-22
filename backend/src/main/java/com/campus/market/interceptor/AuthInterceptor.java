// 文件路径: src/main/java/com/campus/market/interceptor/AuthInterceptor.java
package com.campus.market.interceptor;

import com.campus.market.common.RedisKey;
import com.campus.market.common.Result;
import com.campus.market.common.ResultCode;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.PublicApi;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.entity.SysUser;
import com.campus.market.service.UserService;
import com.campus.market.util.JwtUtil;
import com.campus.market.util.LoginUser;
import com.campus.market.util.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 统一鉴权拦截器。
 * <p>
 * 校验顺序：token 存在 → JWT 可解析 → Redis 白名单一致（单端登录）→ 账号未封禁
 * → 客服只读校验 → @RequireRole 角色匹配。
 * 任一环节失败直接返回统一 Result，HTTP 状态码保持 200，前端只判断 body.code。
 * <p>
 * 权限模型（模块 3.5）：
 * <ul>
 *   <li>admin：全部放行；</li>
 *   <li>customer_service：只读，仅 GET 放行，写操作返回 403，登录/登出/个人信息/绑定账号/改密除外；</li>
 *   <li>user / student：按各业务接口的 @RequireRole 控制。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTH = "Authorization";
    private static final String HEADER_TOKEN = "token";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 客服账号允许调用的写接口（账号自身的登录态维护，不涉及平台内容变更）。
     * 需求给定的白名单为 login / logout / me / bind-account，
     * 另补 admin-login、wx-login（登录入口）与 change-password（允许客服自助改密）。
     * <p>
     * 模块9 的「举报接单 / 处理」不在这里：它们的路径含 {@code {id}} 变量，
     * 精确匹配永远匹配不上，统一由下面的前缀规则处理。
     */
    private static final Set<String> CUSTOMER_SERVICE_WRITE_WHITELIST = Set.of(
            "/api/auth/login",
            "/api/auth/admin-login",
            "/api/auth/wx-login",
            "/api/auth/register",
            "/api/auth/logout",
            "/api/auth/me",
            "/api/auth/bind-account",
            "/api/auth/change-password"
    );

/**
 * 客服允许写操作的路径<b>前缀</b>。
 * <p>
 * 为什么必须用前缀：接单与处理的实际路径是 {@code /api/report/admin/{id}/accept}
 * 与 {@code /api/report/admin/{id}/handle}，含路径变量，精确匹配永远匹配不上。
 * 宁可放宽到前缀，也不用把 id 塞进白名单——白名单里出现具体 id
 * 意味着每接一单都要改代码重启服务。
 * <p>
 * <b>权限边界说明</b>：前缀只解决「路径变量匹配不上」这一个问题，
 * 它本身不构成授权。真正的授权边界仍然是 Controller 上的
 * {@code @RequireRole({ADMIN, CUSTOMER_SERVICE})}——将来若在该前缀下
 * 新增仅管理员可写的接口（如「批量处理」），前缀会放行、注解会拒绝。
 * 两层职责不同：白名单管「客服能不能调」，注解管「谁有资格调」。
 */
private static final List<String> CUSTOMER_SERVICE_WRITE_PREFIXES = List.of(
        "/api/report/admin/"
);

/**
 * 判断某路径是否在客服写白名单内。
 */
private boolean allowedForCustomerServiceWrite(String servletPath) {
        if (CUSTOMER_SERVICE_WRITE_WHITELIST.contains(servletPath)) {
            return true;
        }
        return CUSTOMER_SERVICE_WRITE_PREFIXES.stream().anyMatch(servletPath::startsWith);
    }

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 免登录接口：不强制 token，但携带有效 token 时仍解析出当前用户（软登录），
        // 用于商品详情判断访问者是否为卖家本人
        PublicApi publicApi = handlerMethod.getMethodAnnotation(PublicApi.class);
        if (publicApi == null) {
            publicApi = handlerMethod.getBeanType().getAnnotation(PublicApi.class);
        }
        if (publicApi != null) {
            softLogin(request);
            return true;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return reject(response, ResultCode.UNAUTHORIZED, "未登录，请先登录");
        }

        Long userId = jwtUtil.parseUserId(token);
        if (userId == null) {
            return reject(response, ResultCode.UNAUTHORIZED, "登录状态无效，请重新登录");
        }

        String cachedToken = redis.opsForValue().get(RedisKey.LOGIN_TOKEN + userId);
        if (!StringUtils.hasText(cachedToken) || !cachedToken.equals(token)) {
            return reject(response, ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        SysUser user = userService.getCached(userId);
        if (user == null) {
            return reject(response, ResultCode.UNAUTHORIZED, "用户不存在，请重新登录");
        }
        if (Integer.valueOf(1).equals(user.getStatus())) {
            return reject(response, ResultCode.FORBIDDEN, "账号已被封禁，请联系客服");
        }

        // 客服只读：除白名单外，一切非 GET 请求一律拒绝
        if (RoleConst.CUSTOMER_SERVICE.equals(user.getRole())
                && !HttpMethod.GET.matches(request.getMethod())
                && !allowedForCustomerServiceWrite(request.getServletPath())) {
            return reject(response, ResultCode.FORBIDDEN, "客服账号无操作权限，请联系管理员");
        }

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null) {
            String role = user.getRole();
            boolean allowed = Arrays.stream(requireRole.value()).anyMatch(r -> r.equals(role));
            if (!allowed) {
                return reject(response, ResultCode.FORBIDDEN, "无访问权限");
            }
        }

        UserContext.set(new LoginUser(userId, user.getRole()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 软登录：token 有效则写入上下文，无效则静默忽略，绝不阻断请求。
     */
    private void softLogin(HttpServletRequest request) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return;
        }
        Long userId = jwtUtil.parseUserId(token);
        if (userId == null) {
            return;
        }
        String cachedToken = redis.opsForValue().get(RedisKey.LOGIN_TOKEN + userId);
        if (!StringUtils.hasText(cachedToken) || !cachedToken.equals(token)) {
            return;
        }
        SysUser user = userService.getCached(userId);
        if (user == null || Integer.valueOf(1).equals(user.getStatus())) {
            return;
        }
        UserContext.set(new LoginUser(userId, user.getRole()));
    }

    /**
     * 兼容两种传参：Authorization: Bearer xxx 与 token: xxx（小程序端）。
     */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HEADER_AUTH);
        if (StringUtils.hasText(authorization)) {
            return authorization.startsWith(BEARER_PREFIX)
                    ? authorization.substring(BEARER_PREFIX.length()).trim()
                    : authorization.trim();
        }
        String token = request.getHeader(HEADER_TOKEN);
        return StringUtils.hasText(token) ? token.trim() : null;
    }

    private boolean reject(HttpServletResponse response, ResultCode resultCode, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(resultCode.getCode(), msg)));
        return false;
    }
}
