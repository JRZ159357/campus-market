// 文件路径: src/main/java/com/campus/market/common/CreditLevel.java
package com.campus.market.common;

/**
 * 信誉等级。区间左闭右闭，五档覆盖 0-150 全部取值。
 * <p>
 * 等级不落库，始终由分数实时推导：一旦把等级存成字段，就会出现
 * 「分数改了但等级忘了更新」的不一致，而等级只是分数的展示形态，
 * 没有独立生命周期，没有必要持久化。
 * <p>
 * color / icon 一并放在枚举里，管理端与小程序各自取用，
 * 两端颜色不会因为各写一套而漂移。
 */
public enum CreditLevel {

    /** 差 */
    POOR("差", 0, 59, "#F56C6C", "WarningFilled"),

    /** 一般 */
    NORMAL("一般", 60, 79, "#E6A23C", "InfoFilled"),

    /** 良好 */
    GOOD("良好", 80, 99, "#409EFF", "CircleCheckFilled"),

    /** 优秀 */
    EXCELLENT("优秀", 100, 119, "#67C23A", "SuccessFilled"),

    /** 极佳 */
    PERFECT("极佳", 120, 150, "#D4A017", "TrophyBase");

    private final String label;

    private final int min;

    private final int max;

    private final String color;

    private final String icon;

    CreditLevel(String label, int min, int max, String color, String icon) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.color = color;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * 按分数取等级。分数为空或越界时兜底到「差」，保证前端永远有标签可渲染。
     */
    public static CreditLevel of(Integer score) {
        if (score == null) {
            return POOR;
        }
        for (CreditLevel level : values()) {
            if (score >= level.min && score <= level.max) {
                return level;
            }
        }
        return score < 0 ? POOR : PERFECT;
    }

    /**
     * 当前等级内的进度（0-100），用于个人中心的等级进度条。
     * 极佳档到顶后固定 100。
     */
    public static int progressOf(Integer score) {
        if (score == null) {
            return 0;
        }
        CreditLevel level = of(score);
        int span = level.max - level.min;
        if (span <= 0) {
            return 100;
        }
        int offset = Math.min(Math.max(score - level.min, 0), span);
        return (int) Math.round(offset * 100.0 / span);
    }
}
