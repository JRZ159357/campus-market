// 文件路径: src/main/java/com/campus/market/common/RegisterType.java
package com.campus.market.common;

/**
 * 注册方式常量，对应 sys_user.register_type。
 */
public final class RegisterType {

    /** 微信注册：通过 openid 登录，可在个人中心绑定账号密码 */
    public static final Integer WECHAT = 1;

    /** 账号密码注册：通过 username + password 登录 */
    public static final Integer ACCOUNT = 2;

    private RegisterType() {
    }

    public static String label(Integer registerType) {
        return ACCOUNT.equals(registerType) ? "账号密码注册" : "微信注册";
    }
}
