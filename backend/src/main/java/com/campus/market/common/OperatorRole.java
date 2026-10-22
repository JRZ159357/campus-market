// 文件路径: src/main/java/com/campus/market/common/OperatorRole.java
package com.campus.market.common;

/**
 * 订单日志中的操作人角色，写入 order_log.operator_role。
 * 与 sys_user.role 的区别：这里更细，区分了"系统自动"与"买家/卖家"视角。
 */
public final class OperatorRole {

    public static final String BUYER = "buyer";
    public static final String SELLER = "seller";
    public static final String ADMIN = "admin";
    public static final String CUSTOMER_SERVICE = "customer_service";
    public static final String SYSTEM = "system";

    private OperatorRole() {
    }

    public static String label(String role) {
        if (role == null) {
            return "系统";
        }
        return switch (role) {
            case BUYER -> "买家";
            case SELLER -> "卖家";
            case ADMIN -> "管理员";
            case CUSTOMER_SERVICE -> "客服";
            case SYSTEM -> "系统";
            default -> role;
        };
    }
}
