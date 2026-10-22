// 文件路径: src/main/java/com/campus/market/common/CreditRule.java
package com.campus.market.common;

/**
 * 信誉分规则常量。
 * <p>
 * 把「几分对应多少信誉分」「上下限」集中在这里，而不是散落在 Service 的 if-else 里：
 * 这些数值是论文里要单独成表的业务规则，集中存放才能保证「评价加一次分」和
 * 「管理端手动调整」用的是同一套边界，不会出现一边 100 封顶一边 150 封顶。
 * <p>
 * 规则表：
 * <pre>
 *   收到 5 星  +2      被举报核实  -10
 *   收到 4 星  +1      初始分      100
 *   收到 3 星   0      上限        150
 *   收到 2 星  -2      下限          0
 *   收到 1 星  -5
 * </pre>
 */
public final class CreditRule {

    /** 初始信誉分 */
    public static final int INITIAL_SCORE = 100;

    /** 信誉分下限 */
    public static final int MIN_SCORE = 0;

    /** 信誉分上限 */
    public static final int MAX_SCORE = 150;

    /** 举报核实违规扣分 */
    public static final int REPORT_PENALTY = -10;

    private CreditRule() {
    }

    /**
     * 收到一条评价时被评价人的信誉分变化。
     * <p>
     * 用 switch 表达式而非 Map：星数是 1-5 的封闭取值，switch 能让编译器
     * 在遗漏分支时报错，也避免每次调用都查一次哈希表。
     *
     * @param stars 综合星级 1-5
     * @return 变动值，正数加分、负数减分
     */
    public static int deltaOfStars(int stars) {
        return switch (stars) {
            case 5 -> 2;
            case 4 -> 1;
            case 3 -> 0;
            case 2 -> -2;
            case 1 -> -5;
            default -> 0;
        };
    }

    /**
     * 把分数截断到 [0, 150]。
     * <p>
     * 截断必须基于「变动后的绝对分数」而不是变动值：用户当前 149 分再收一个 5 星，
     * 实际只加 1 分而不是 2 分，写入流水的 changeScore 也应该是真实的 1。
     * 所以 CreditService 会先算 after = clamp(before + delta)，再用 after - before 反推变动值。
     */
    public static int clamp(int score) {
        if (score < MIN_SCORE) {
            return MIN_SCORE;
        }
        return Math.min(score, MAX_SCORE);
    }
}
