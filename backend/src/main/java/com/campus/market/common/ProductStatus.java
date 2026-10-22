// 文件路径: src/main/java/com/campus/market/common/ProductStatus.java
package com.campus.market.common;

/**
 * 商品状态常量。与 product.status 取值一一对应。
 * 状态流转：待审核(0) --审核通过--> 已上架(1) --卖家下架--> 已下架(2)
 * 待审核(0) --审核驳回--> 已下架(2)（卖家可修改后重新提交）
 * 已上架(1) --管理员强制下架--> 违规下架(4)
 */
public final class ProductStatus {

    /** 待审核：发布后的默认状态，仅卖家与管理员可见 */
    public static final int PENDING = 0;

    /** 已上架：审核通过，所有用户可见 */
    public static final int ON_SALE = 1;

    /** 已下架：卖家主动下架或审核驳回 */
    public static final int OFFLINE = 2;

    /** 已售出：订单模块流转，本模块不主动写入 */
    public static final int SOLD = 3;

    /** 违规下架：管理员强制下架 */
    public static final int VIOLATION = 4;

    /**
     * 需要在「账号注销 / 管理员封禁」时被强制下架的状态集合。
     * <p>
     * 只包含待审核与已上架：这两类是「还在平台上露出、别人能点」的；
     * 已售出要保留交易痕迹，已下架与违规下架本来就不对外展示。
     */
    public static final java.util.List<Integer> OFFLINEABLE_STATUSES = java.util.List.of(PENDING, ON_SALE);

    private ProductStatus() {
    }

    public static String label(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case PENDING -> "待审核";
            case ON_SALE -> "已上架";
            case OFFLINE -> "已下架";
            case SOLD -> "已售出";
            case VIOLATION -> "违规下架";
            default -> "未知";
        };
    }

    /**
     * 是否占用卖家的"同时在售额度"。待审核也占额度，
     * 否则用户可以无限提交待审核商品绕过上限。
     */
    public static boolean occupiesQuota(Integer status) {
        return status != null && (status == PENDING || status == ON_SALE);
    }
}
