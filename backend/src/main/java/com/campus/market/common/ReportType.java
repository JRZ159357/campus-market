// 文件路径: src/main/java/com/campus/market/common/ReportType.java
package com.campus.market.common;

import java.util.Arrays;
import java.util.List;

/**
 * 举报类型，对应 report.report_type。
 * <p>
 * 三类举报共用一张表（字段结构完全一致），用这个枚举统一收敛：
 * 校验时只认 values() 里的值，展示文案与「该类型允许的处理动作」也集中在这里，
 * 避免「前端能选的类型」和「后端会处理的动作」两套口径。
 */
public final class ReportType {

    /** 商品举报：虚假描述、违禁品、价格异常 */
    public static final int PRODUCT = 1;

    /** 用户举报：辱骂、欺诈、骚扰 */
    public static final int USER = 2;

    /** 评价举报：恶意差评、广告、辱骂 */
    public static final int REVIEW = 3;

    /** 全部合法类型 */
    public static final List<Integer> ALL = Arrays.asList(PRODUCT, USER, REVIEW);

    private ReportType() {
    }

    public static boolean isValid(Integer type) {
        return type != null && ALL.contains(type);
    }

    public static String label(Integer type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case PRODUCT -> "商品举报";
            case USER -> "用户举报";
            case REVIEW -> "评价举报";
            default -> "未知";
        };
    }

    /** 简短标签，管理端表格里空间有限 */
    public static String shortLabel(Integer type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case PRODUCT -> "商品";
            case USER -> "用户";
            case REVIEW -> "评价";
            default -> "未知";
        };
    }

    /**
     * 该类型举报成立时的默认处理动作。
     * <p>
     * 这条映射不是「建议」而是「默认」：商品举报只能下架商品，
     * 评价举报只能删评价，允许客服把「商品举报」处理成「封禁卖家」会造成语义错乱
     * （该动作属于用户举报），所以按类型收窄可选项。
     */
    public static List<Integer> allowedActions(Integer type) {
        if (type == null) {
            return List.of();
        }
        return switch (type) {
            case PRODUCT -> List.of(ReportHandleAction.NONE, ReportHandleAction.PRODUCT_OFFLINE,
                    ReportHandleAction.CREDIT_DEDUCT);
            case USER -> List.of(ReportHandleAction.NONE, ReportHandleAction.USER_BAN,
                    ReportHandleAction.CREDIT_DEDUCT);
            case REVIEW -> List.of(ReportHandleAction.REVIEW_DELETE, ReportHandleAction.CREDIT_DEDUCT);
            default -> List.of();
        };
    }
}