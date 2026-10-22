// 文件路径: src/main/java/com/campus/market/common/ReportHandleAction.java
package com.campus.market.common;

/**
 * 举报处理动作，对应 report.handle_action。
 * <p>
 * 为什么不用「一个布尔字段 is_violation」：举报成立的处置有四种，
 * 分别是作用于四种不同的对象（商品 / 用户 / 评价 / 信誉分），
 * 用布尔表达就不得不再加三个布尔，而三个布尔的组合里有一半是非法的。
 * 拆成单值枚举后，「处理动作」天然互斥，「没做过任何动作」= NONE 也很明确。
 */
public final class ReportHandleAction {

    /** 不处理（举报不成立，走已驳回） */
    public static final int NONE = 0;

    /** 强制下架被举报商品 */
    public static final int PRODUCT_OFFLINE = 1;

    /** 封禁被举报用户 */
    public static final int USER_BAN = 2;

    /** 删除被举报评价 */
    public static final int REVIEW_DELETE = 3;

    /** 仅扣除信誉分（可与下架/删除叠加使用） */
    public static final int CREDIT_DEDUCT = 4;

    private ReportHandleAction() {
    }

    public static boolean isValid(Integer action) {
        return action != null && action >= NONE && action <= CREDIT_DEDUCT;
    }

    public static String label(Integer action) {
        if (action == null) {
            return "未处理";
        }
        return switch (action) {
            case NONE -> "不处理（举报不成立）";
            case PRODUCT_OFFLINE -> "强制下架商品";
            case USER_BAN -> "封禁用户";
            case REVIEW_DELETE -> "删除违规评价";
            case CREDIT_DEDUCT -> "扣除信誉分";
            default -> "未知";
        };
    }

    /** 简短标签，用在表格与处理记录里 */
    public static String shortLabel(Integer action) {
        if (action == null) {
            return "未处理";
        }
        return switch (action) {
            case NONE -> "不处理";
            case PRODUCT_OFFLINE -> "下架商品";
            case USER_BAN -> "封禁用户";
            case REVIEW_DELETE -> "删除评价";
            case CREDIT_DEDUCT -> "扣除信誉分";
            default -> "未知";
        };
    }

    /** 是否属于「举报成立」的动作 */
    public static boolean isViolation(Integer action) {
        return action != null && action != NONE;
    }
}