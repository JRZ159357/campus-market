// 文件路径: src/main/java/com/campus/market/util/JwtUtil.java
package com.campus.market.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具。token 只承载 userId，角色与账号状态由拦截器从缓存/数据库读取，
 * 这样管理员改角色、封禁账号后立即生效，不必等 token 过期。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-hours:168}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 60L * 60L * 1000L;
    }

    /**
     * 签发 token。
     */
    public String create(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析 userId，失败返回 null（过期、篡改、格式错误统一视为未登录）。
     */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * token 有效期（秒），用于写入 Redis 的 TTL。
     */
    public long getExpireSeconds() {
        return expireMillis / 1000;
    }
}
