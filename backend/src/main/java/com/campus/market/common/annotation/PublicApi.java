// 文件路径: src/main/java/com/campus/market/common/annotation/PublicApi.java
package com.campus.market.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 免登录接口标记。标注后 AuthInterceptor 不再强制校验 token。
 * <p>
 * 与"直接放进拦截器白名单"的区别：这里会尝试解析 token（软登录），
 * 解析成功则写入 UserContext，让商品详情等接口能识别"当前访问者是不是卖家本人"，
 * 解析失败或未携带 token 也照常放行。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
