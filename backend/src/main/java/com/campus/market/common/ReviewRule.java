// 文件路径: src/main/java/com/campus/market/common/ReviewRule.java
package com.campus.market.common;

/**
 * 评价业务规则常量。
 * <p>
 * 追评窗口、图片张数上限这类数字，前端提示文案、后端校验、测试用例
 * 三处都要引用。写在常量里，改需求时只改一个地方；
 * 如果各自硬编码 7 和 3，很容易出现「后端放开到 6 张、前端还卡在 3 张」。
 */
public final class ReviewRule {

    /** 追评窗口：交易完成后 7 天内 */
    public static final int FOLLOW_WINDOW_DAYS = 7;

    /** 单次评价最多图片数 */
    public static final int MAX_IMAGES = 3;

    /** 文字评价最大长度 */
    public static final int MAX_CONTENT_LENGTH = 500;

    /** 匿名评价的展示昵称 */
    public static final String ANONYMOUS_NICKNAME = "匿名用户";

    private ReviewRule() {
    }
}
