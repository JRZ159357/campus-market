// 文件路径: src/main/java/com/campus/market/common/RoleConst.java
package com.campus.market.common;

/**
 * 角色常量。与 sys_user.role 字段取值一一对应，避免各处硬编码字符串。
 */
public final class RoleConst {

    /** 普通用户：微信登录默认角色，不可发布商品 */
    public static final String USER = "user";

    /** 认证学生：学生认证通过后升级，可发布商品 */
    public static final String STUDENT = "student";

    /** 客服：由管理员分配，处理咨询与纠纷 */
    public static final String CUSTOMER_SERVICE = "customer_service";

    /** 管理员：全部权限 */
    public static final String ADMIN = "admin";

    private RoleConst() {
    }

    /**
     * 角色中文名，用于日志与前端展示。
     */
    public static String label(String role) {
        if (role == null) {
            return "未知";
        }
        return switch (role) {
            case USER -> "普通用户";
            case STUDENT -> "认证学生";
            case CUSTOMER_SERVICE -> "客服";
            case ADMIN -> "管理员";
            default -> role;
        };
    }
}
