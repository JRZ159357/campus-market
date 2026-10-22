// 文件路径: src/main/java/com/campus/market/common/SensitiveWordType.java
package com.campus.market.common;

import java.util.List;

/**
 * 敏感词类型常量。与 sensitive_word.type 取值一一对应。
 * <p>
 * 需求原文把第 4 类写成「政治」，但预置词里「骗子 / 跑路」这类既不违禁、
 * 也不辱骂、也不像广告，管理端 Tab 又只给了「其他」一个位置。
 * 这里统一收敛为 4=其他，政治类敏感词归入此类（不单列的原因：
 * 单列会让管理端出现一个永远只有个位数词条的 Tab，
 * 而运营真正关心的是「这个词会不会让帖子发不出去」，
 * 那是 level 的事，与 type 无关）。
 */
public final class SensitiveWordType {

    /** 违禁品：枪毒假币窃听器等，命中即拦截 */
    public static final int PROHIBITED = 1;

    /** 辱骂：人身攻击，命中即拦截 */
    public static final int ABUSE = 2;

    /** 广告：站外引流与刷单，默认警告 */
    public static final int AD = 3;

    /** 其他：欺诈、金融违规等归入此类 */
    public static final int OTHER = 4;

    /** 全部合法类型，供参数校验使用 */
    public static final List<Integer> ALL = List.of(PROHIBITED, ABUSE, AD, OTHER);

    private SensitiveWordType() {
    }

    public static boolean isValid(Integer type) {
        return type != null && ALL.contains(type);
    }

    public static String label(Integer type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case PROHIBITED -> "违禁品";
            case ABUSE -> "辱骂";
            case AD -> "广告";
            case OTHER -> "其他";
            default -> "未知";
        };
    }
}
