// 文件路径: src/main/java/com/campus/market/common/OrderStatus.java
package com.campus.market.common;

/**
 * 订单状态常量。与 trade_order.status 取值一一对应。
 * <p>
 * 状态机（论文中的状态流转图即按此绘制）：
 * <pre>
 *                 ┌──────────── 买家取消 / 超时未付款 ──────────┐
 *                 ↓                                             │
 * create → 0待付款 ──pay──> 1待发货 ──ship──> 2待收货 ──receive──> 3已完成
 *                            │  ↑               │
 *                            │  └── reject ──┐  │
 *                            └─ apply ─> 5退款中 ┘
 *                                          │
 *                                     agree/admin-refund
 *                                          ↓
 *                                       6已退款
 * </pre>
 * 说明：5退款中的"来源状态"记录在 trade_order.refund_from_status，
 * 卖家拒绝或买家撤销退款时回归来源状态，而不是硬编码回到待发货。
 */
public final class OrderStatus {

    /** 待付款：下单成功，尚未支付 */
    public static final int PENDING_PAY = 0;

    /** 待发货：已付款，商品已锁定，等卖家发货 / 确认交易 */
    public static final int PENDING_SHIP = 1;

    /** 待收货：卖家已发货，等买家确认 */
    public static final int PENDING_RECEIVE = 2;

    /** 已完成：买家确认收货，可进入评价 */
    public static final int FINISHED = 3;

    /** 已取消：买家取消、管理员强制取消或超时未付款 */
    public static final int CANCELED = 4;

    /** 退款中：买家已提交退款申请，等卖家处理 */
    public static final int REFUNDING = 5;

    /** 已退款：退款完成，商品恢复上架 */
    public static final int REFUNDED = 6;

    private OrderStatus() {
    }

    public static String label(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case PENDING_PAY -> "待付款";
            case PENDING_SHIP -> "待发货";
            case PENDING_RECEIVE -> "待收货";
            case FINISHED -> "已完成";
            case CANCELED -> "已取消";
            case REFUNDING -> "退款中";
            case REFUNDED -> "已退款";
            default -> "未知";
        };
    }

    /**
     * 是否已支付。已支付意味着商品已被本单占用（status=3 已售出），
     * 取消 / 退款时必须把商品恢复上架。
     */
    public static boolean isPaid(Integer status) {
        return status != null
                && (status == PENDING_SHIP || status == PENDING_RECEIVE
                || status == REFUNDING || status == REFUNDED);
    }

    /**
     * 是否属于"进行中"订单。同一商品只允许存在一笔进行中的订单，
     * 防止两个买家各下一单最后互相覆盖。
     */
    public static boolean isActive(Integer status) {
        return status != null
                && (status == PENDING_PAY || status == PENDING_SHIP
                || status == PENDING_RECEIVE || status == REFUNDING);
    }
}
