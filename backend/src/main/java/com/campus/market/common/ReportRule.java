// 文件路径: src/main/java/com/campus/market/common/ReportRule.java
package com.campus.market.common;

/**
 * 举报相关业务规则常量。
 * <p>
 * 把阈值集中在这里而不是散在 Service 的 if 里：这些数字会在
 * 「连续举报限制」「处理时限统计」等处被多处引用，改一个值时
 * 只改一处可以避免「限 1 次但别处按 2 次算」的不一致。
 */
public final class ReportRule {

    /** 详细描述最少字数：太短的描述无法支撑客服判断，等于让客服猜 */
    public static final int CONTENT_MIN_LENGTH = 5;

    /** 详细描述最多字数，与数据库 varchar(500) 保持一致 */
    public static final int CONTENT_MAX_LENGTH = 500;

    /** 证据图片最多张数 */
    public static final int IMAGE_MAX_COUNT = 3;

    /**
     * 同一用户对同一对象的重复举报冷却时间（小时）。
     * <p>
     * 为什么必须限：举报会触发人工核实，没有节流的话，
     * 一条商品被同一用户刷 100 条举报，客服的待处理列表就被淹没了，
     * 其他真实举报反而没人管。
     */
    public static final int DUP_COOLDOWN_HOURS = 24;

    /** 商品举报成立：卖家信誉分扣分 */
    public static final int PRODUCT_VIOLATION_CREDIT = -10;

    /** 用户举报成立：被举报用户信誉分扣分 */
    public static final int USER_VIOLATION_CREDIT = -10;

    /** 评价举报成立：评价人信誉分扣分 */
    public static final int REVIEW_VIOLATION_CREDIT = -5;

    private ReportRule() {
    }
}