// 文件路径: src/main/java/com/campus/market/common/ReviewType.java
package com.campus.market.common;

/**
 * 评价方向常量，对应 review.review_type。
 * <p>
 * 两个方向的维度不同，Service 在提交评价时会按方向校验对应维度是否齐全：
 * 买家评卖家需 desc/ship/attitude，卖家评买家需 pay/attitude。
 */
public final class ReviewType {

    /** 买家评卖家 */
    public static final int BUYER_TO_SELLER = 1;

    /** 卖家评买家 */
    public static final int SELLER_TO_BUYER = 2;

    private ReviewType() {
    }

    public static String label(Integer type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case BUYER_TO_SELLER -> "买家评卖家";
            case SELLER_TO_BUYER -> "卖家评买家";
            default -> "未知";
        };
    }

    /**
     * 该方向是否使用「描述相符 / 发货速度」两个维度。
     */
    public static boolean hasProductDimensions(Integer type) {
        return type != null && type == BUYER_TO_SELLER;
    }
}
