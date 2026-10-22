// 文件路径: src/main/java/com/campus/market/common/Gender.java
package com.campus.market.common;

/**
 * 性别常量。与 sys_user.gender 字段取值一一对应。
 * <p>
 * 用 0/1/2 三个整型而不是字符串：数据库层判空与范围校验都走整数，
 * 也省掉了「男/女/保密」三种中文在传输过程中的编码问题。
 */
public final class Gender {

    /** 保密（默认，也是未填写时的兜底） */
    public static final int SECRET = 0;

    /** 男 */
    public static final int MALE = 1;

    /** 女 */
    public static final int FEMALE = 2;

    private Gender() {
    }

    /**
     * 是否为合法取值。
     */
    public static boolean isValid(Integer value) {
        return value != null && value >= SECRET && value <= FEMALE;
    }

    /**
     * 性别中文名。非法值按「保密」处理，前端永远不会拿到空字符串。
     */
    public static String label(Integer value) {
        if (value == null) {
            return "保密";
        }
        return switch (value) {
            case MALE -> "男";
            case FEMALE -> "女";
            case SECRET -> "保密";
            default -> "保密";
        };
    }
}
