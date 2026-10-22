// 文件路径: src/main/java/com/campus/market/common/OrderAction.java
package com.campus.market.common;

/**
 * 订单动作常量，写入 order_log.action。
 * 取值全部小写下划线，前端与文档共用同一套字面量。
 */
public final class OrderAction {

    public static final String CREATE = "create";
    public static final String PAY = "pay";
    public static final String SHIP = "ship";
    public static final String RECEIVE = "receive";
    public static final String CANCEL = "cancel";
    public static final String APPLY_REFUND = "apply_refund";
    public static final String AGREE_REFUND = "agree_refund";
    public static final String REJECT_REFUND = "reject_refund";
    public static final String CANCEL_REFUND = "cancel_refund";
    public static final String TIMEOUT_CANCEL = "timeout_cancel";
    public static final String ADMIN_CANCEL = "admin_cancel";
    public static final String ADMIN_REFUND = "admin_refund";

    private OrderAction() {
    }

    /**
     * 动作中文名，用于订单详情时间线与操作日志展示。
     */
    public static String label(String action) {
        if (action == null) {
            return "未知操作";
        }
        return switch (action) {
            case CREATE -> "提交订单";
            case PAY -> "买家付款";
            case SHIP -> "卖家发货";
            case RECEIVE -> "买家确认收货";
            case CANCEL -> "买家取消订单";
            case APPLY_REFUND -> "买家申请退款";
            case AGREE_REFUND -> "卖家同意退款";
            case REJECT_REFUND -> "卖家拒绝退款";
            case CANCEL_REFUND -> "买家撤销退款申请";
            case TIMEOUT_CANCEL -> "超时未付款自动取消";
            case ADMIN_CANCEL -> "管理员强制取消";
            case ADMIN_REFUND -> "管理员强制退款";
            default -> action;
        };
    }
}
