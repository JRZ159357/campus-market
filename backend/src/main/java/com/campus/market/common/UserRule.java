// 文件路径: src/main/java/com/campus/market/common/UserRule.java
package com.campus.market.common;

/**
 * 个人中心与卖家主页的取值约束。
 * <p>
 * 把这些数字集中在一个类里，是为了让「前后端各写一份」这种漂移无处可藏：
 * 前端 limits.maxLength 直接引用同一套数值，后端 @Size 也用同一套数值，
 * 改一处必然要改两处。
 */
public final class UserRule {

    /** 昵称最大长度 */
    public static final int NICKNAME_MAX_LENGTH = 20;

    /** 个人简介最大长度 */
    public static final int INTRO_MAX_LENGTH = 100;

    /** 个人简介最大存储长度（数据库列宽，留有余量） */
    public static final int INTRO_DB_MAX_LENGTH = 200;

    /** 头像最大长度（URL 长度上限） */
    public static final int AVATAR_MAX_LENGTH = 500;

    /** 注销账号二次确认的固定口令 */
    public static final String ACCOUNT_DELETE_CONFIRM = "注销账号";

    /** 注销账号时密码的最小长度（仅在用户已设置密码时校验） */
    public static final int PASSWORD_MIN_LENGTH = 6;

    private UserRule() {
    }
}
