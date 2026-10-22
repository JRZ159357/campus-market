// 文件路径: src/main/java/com/campus/market/common/CreditLogType.java
package com.campus.market.common;

/**
 * 信誉分变动类型，对应 credit_log.type。
 * <p>
 * 评价加分与管理员调整必须能区分开：管理端「调整历史」只展示人工干预，
 * 而用户侧的「分数明细」要展示全部来源。靠 type 过滤即可，不需要两张表。
 */
public final class CreditLogType {

    /** 收到评价（含追评不重复加分，只有首次评价计分） */
    public static final int REVIEW = 1;

    /** 举报核实违规扣分（模块 8 举报功能接入） */
    public static final int REPORT = 2;

    /** 管理员手动调整 */
    public static final int ADMIN_ADJUST = 3;

    /** 评价被删除（审计留痕，分数不回退） */
    public static final int REVIEW_DELETED = 4;

    private CreditLogType() {
    }

    public static String label(Integer type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case REVIEW -> "收到评价";
            case REPORT -> "举报扣分";
            case ADMIN_ADJUST -> "管理员调整";
            case REVIEW_DELETED -> "评价被删除";
            default -> "未知";
        };
    }
}
