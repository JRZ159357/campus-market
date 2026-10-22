// 文件路径: src/main/java/com/campus/market/common/SensitiveWordLevel.java
package com.campus.market.common;

/**
 * 敏感词等级常量。与 sensitive_word.level 取值一一对应。
 * <p>
 * 只设两级，是刻意的产品决策而不是简化实现：
 * <ul>
 *   <li><b>警告</b>：内容放行，但打上「待人工复核」标记并通知管理员。
 *       适用于「可能是正常需求、也可能违规」的词——二手交易里
 *       「加微信」既可能是引流，也可能是约线下验货，机器判不准，
 *       交给人看一秒就能定。</li>
 *   <li><b>拦截</b>：直接拒绝，不落库。适用于无论什么上下文都违规的词
 *       （枪毒、假币、人身攻击），这类内容一旦发出去就是平台责任，
 *       没有「人工再看看」的余地。</li>
 * </ul>
 * 两级的另一个好处：语义与用户看到的结果一一对应（发得出去 / 发不出去），
 * 用户不会问「为什么我这条被标记了但还能发」——标记只影响审核侧，
 * 用户侧无感。
 */
public final class SensitiveWordLevel {

    /** 警告：放行 + 标记待人工复核 + 通知管理员 */
    public static final int WARN = 1;

    /** 拦截：直接拒绝，提示命中的词 */
    public static final int BLOCK = 2;

    private SensitiveWordLevel() {
    }

    public static boolean isValid(Integer level) {
        return level != null && (level == WARN || level == BLOCK);
    }

    public static String label(Integer level) {
        if (level == null) {
            return "未知";
        }
        return switch (level) {
            case WARN -> "警告";
            case BLOCK -> "拦截";
            default -> "未知";
        };
    }
}
