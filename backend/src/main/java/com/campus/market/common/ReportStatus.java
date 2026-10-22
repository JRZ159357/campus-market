// 文件路径: src/main/java/com/campus/market/common/ReportStatus.java
package com.campus.market.common;

/**
 * 举报状态，对应 report.status。
 * <p>
 * 状态流转：
 * <pre>
 *   待处理(0) --客服接单--> 处理中(1) --处理成立--> 已处理(2)
 *                          --处理不成立--> 已驳回(3)
 *   已处理(2) / 已驳回(3) 为终态，不可再流转。
 * </pre>
 * 为什么不允许「已驳回 → 处理中」：如果客服判断错了需要改判，
 * 正确做法是让举报人重新提交（会生成新记录、走新的处理流程），
 * 而不是把历史记录改回去——否则「谁在什么时候撤回了什么结论」就查不到了。
 */
public final class ReportStatus {

    /** 待处理：已提交，尚未有人接单 */
    public static final int PENDING = 0;

    /** 处理中：客服已接单，正在核实 */
    public static final int PROCESSING = 1;

    /** 已处理：举报成立并已执行处置 */
    public static final int HANDLED = 2;

    /** 已驳回：举报不成立 */
    public static final int REJECTED = 3;

    private ReportStatus() {
    }

    /** 终态：已处理与已驳回都不能再流转 */
    public static boolean isFinal(Integer status) {
        return status != null && (status == HANDLED || status == REJECTED);
    }

    public static String label(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case PENDING -> "待处理";
            case PROCESSING -> "处理中";
            case HANDLED -> "已处理";
            case REJECTED -> "已驳回";
            default -> "未知";
        };
    }

    /** Element Plus 标签配色，与小程序端保持同一套语义色 */
    public static String color(Integer status) {
        if (status == null) {
            return "info";
        }
        return switch (status) {
            case PENDING -> "danger";
            case PROCESSING -> "warning";
            case HANDLED -> "success";
            case REJECTED -> "info";
            default -> "info";
        };
    }
}