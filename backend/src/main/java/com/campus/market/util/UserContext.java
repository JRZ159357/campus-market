// 文件路径: src/main/java/com/campus/market/util/UserContext.java
package com.campus.market.util;

import com.campus.market.common.BusinessException;
import com.campus.market.common.ResultCode;

/**
 * 当前登录用户上下文。由 AuthInterceptor 写入，请求结束时必须 clear() 防内存泄漏。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 获取当前用户 ID，未登录时抛 401。
     */
    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        if (user == null || user.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return user.getUserId();
    }

    /**
     * 获取当前用户 ID，未登录返回 null。免登录接口（软登录）使用。
     */
    public static Long getUserIdOrNull() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String getRole() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getRole();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
