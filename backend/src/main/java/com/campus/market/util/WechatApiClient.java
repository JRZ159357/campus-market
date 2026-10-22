// 文件路径: src/main/java/com/campus/market/util/WechatApiClient.java
package com.campus.market.util;

import com.campus.market.common.BusinessException;
import com.campus.market.common.RedisKey;
import com.campus.market.config.WechatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 微信开放接口客户端：code2session、手机号解密、access_token 缓存。
 * mock 模式（wechat.mock=true）下不发起真实请求，返回确定性模拟数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatApiClient {

    private static final String BASE_URL = "https://api.weixin.qq.com";

    private final WechatProperties properties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder().baseUrl(BASE_URL).build();

    /**
     * 用 wx.login 的 code 换取 openid。
     */
    public String code2OpenId(String code) {
        if (properties.isMock()) {
            log.info("微信登录处于 mock 模式，code={}", code);
            return "mock_openid_" + code;
        }
        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/sns/jscode2session")
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .queryParam("js_code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(String.class);
        JsonNode node = readJson(body);
        int errcode = node.path("errcode").asInt(0);
        if (errcode != 0) {
            log.warn("微信 code2session 失败: {}", body);
            throw new BusinessException("微信登录失败，请重试");
        }
        String openId = node.path("openid").asText(null);
        if (!StringUtils.hasText(openId)) {
            throw new BusinessException("微信登录失败：未获取到 openid");
        }
        return openId;
    }

    /**
     * 用 getPhoneNumber 回调的 code 换取手机号。
     */
    public String getPhoneNumber(String code) {
        if (properties.isMock()) {
            log.info("获取手机号处于 mock 模式，code={}", code);
            // 由 code 派生稳定手机号，便于同一账号多次联调结果一致
            int suffix = Math.abs(code.hashCode()) % 10000;
            return String.format("138%08d", suffix);
        }
        String accessToken = getAccessToken();
        String body = restClient.post()
                .uri("/wxa/business/getuserphonenumber?access_token={token}", accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", code))
                .retrieve()
                .body(String.class);
        JsonNode node = readJson(body);
        if (node.path("errcode").asInt(0) != 0) {
            log.warn("获取手机号失败: {}", body);
            throw new BusinessException("手机号获取失败，请重试");
        }
        String phone = node.path("phone_info").path("phoneNumber").asText(null);
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException("手机号获取失败");
        }
        return phone;
    }

    /**
     * 获取接口调用凭证，缓存至 Redis（提前 5 分钟过期）。
     */
    private String getAccessToken() {
        String cached = redis.opsForValue().get(RedisKey.WECHAT_ACCESS_TOKEN);
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/cgi-bin/token")
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .build())
                .retrieve()
                .body(String.class);
        JsonNode node = readJson(body);
        String token = node.path("access_token").asText(null);
        if (!StringUtils.hasText(token)) {
            log.warn("获取 access_token 失败: {}", body);
            throw new BusinessException("微信服务异常，请稍后重试");
        }
        long expiresIn = node.path("expires_in").asLong(7200);
        redis.opsForValue().set(RedisKey.WECHAT_ACCESS_TOKEN, token, Duration.ofSeconds(Math.max(60, expiresIn - 300)));
        return token;
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new BusinessException("微信接口返回格式异常");
        }
    }
}
