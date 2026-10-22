// 文件路径: src/main/java/com/campus/market/common/ReportReason.java
package com.campus.market.common;

import java.util.Arrays;
import java.util.List;

/**
 * 举报原因枚举，对应 report.reason。
 * <p>
 * 用 tinyint 而不是字符串：管理端要按原因做分布统计，
 * 字符串列里 '虚假描述' 与 '虚假描述 '（多一个空格）会被 GROUP BY 算成两类，
 * 统计出来的数字对不上。展示文案由 label() 统一输出，改文案不用洗数据。
 * <p>
 * 最后一档「其他」是必须的：如果原因只允许从固定选项里选，
 * 遇到选项没覆盖的情况（如「卖家拒绝发货」）用户就只能乱选一个，
 * 分类数据反而被污染；允许兜底 + 必填的文字描述才能接住真实诉求。
 */
public final class ReportReason {

    /** 虚假描述：商品与实际情况不符 */
    public static final int FALSE_DESC = 1;

    /** 违禁品：禁止在校园内交易的东西 */
    public static final int PROHIBITED = 2;

    /** 欺诈：以交易为名的诈骗 */
    public static final int FRAUD = 3;

    /** 辱骂骚扰：不文明言行 */
    public static final int ABUSE = 4;

    /** 广告导流：微信号、二维码、外链等 */
    public static final int AD = 5;

    /** 其他（配合必填的文字描述使用） */
    public static final int OTHER = 6;

    /** 全部合法原因 */
    public static final List<Integer> ALL = Arrays.asList(FALSE_DESC, PROHIBITED, FRAUD, ABUSE, AD, OTHER);

    private ReportReason() {
    }

    public static boolean isValid(Integer reason) {
        return reason != null && ALL.contains(reason);
    }

    public static String label(Integer reason) {
        if (reason == null) {
            return "未选择";
        }
        return switch (reason) {
            case FALSE_DESC -> "虚假描述";
            case PROHIBITED -> "违禁品";
            case FRAUD -> "欺诈";
            case ABUSE -> "辱骂骚扰";
            case AD -> "广告导流";
            case OTHER -> "其他";
            default -> "其他";
        };
    }
}