// 文件路径: src/main/java/com/campus/market/common/annotation/RequireRole.java
package com.campus.market.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解。标注在方法或类上，由 AuthInterceptor 统一校验。
 * 例：@RequireRole(RoleConst.ADMIN) 或 @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色码，命中任意一个即放行 */
    String[] value();
}
