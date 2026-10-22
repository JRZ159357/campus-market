// 文件路径: src/main/java/com/campus/market/config/WechatProperties.java
package com.campus.market.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置。mock=true 时不调用真实微信接口，返回模拟 openid/手机号，
 * 便于在没有企业主体小程序账号时完成本地联调与答辩演示。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    /** 小程序 AppID */
    private String appId;

    /** 小程序 AppSecret */
    private String appSecret;

    /** 是否启用模拟模式 */
    private boolean mock = true;
}
