// 文件路径: src/main/java/com/campus/market/vo/OrderCountVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 订单状态计数。个人中心角标、管理端标签页角标共用。
 * afterSale = 退款中 + 已退款，对应小程序"退款/售后"入口。
 */
@Data
@Schema(description = "订单状态计数")
public class OrderCountVO {

    @Schema(description = "待付款")
    private Long pendingPay;

    @Schema(description = "待发货")
    private Long pendingShip;

    @Schema(description = "待收货")
    private Long pendingReceive;

    @Schema(description = "已完成")
    private Long finished;

    @Schema(description = "已取消")
    private Long canceled;

    @Schema(description = "退款中")
    private Long refunding;

    @Schema(description = "已退款")
    private Long refunded;

    @Schema(description = "退款 / 售后合计")
    private Long afterSale;

    @Schema(description = "全部订单数")
    private Long total;

    /**
     * 把 GROUP BY 的结果装配成 VO，缺失状态补 0，前端无需判空。
     */
    public static OrderCountVO from(List<Map<String, Object>> rows) {
        OrderCountVO vo = new OrderCountVO();
        vo.setPendingPay(0L);
        vo.setPendingShip(0L);
        vo.setPendingReceive(0L);
        vo.setFinished(0L);
        vo.setCanceled(0L);
        vo.setRefunding(0L);
        vo.setRefunded(0L);
        vo.setAfterSale(0L);
        vo.setTotal(0L);
        if (rows == null) {
            return vo;
        }
        long total = 0L;
        for (Map<String, Object> row : rows) {
            Integer status = toInt(row.get("status"));
            long cnt = toLong(row.get("cnt"));
            total += cnt;
            if (status == null) {
                continue;
            }
            switch (status) {
                case 0 -> vo.setPendingPay(cnt);
                case 1 -> vo.setPendingShip(cnt);
                case 2 -> vo.setPendingReceive(cnt);
                case 3 -> vo.setFinished(cnt);
                case 4 -> vo.setCanceled(cnt);
                case 5 -> {
                    vo.setRefunding(cnt);
                    vo.setAfterSale(vo.getAfterSale() + cnt);
                }
                case 6 -> {
                    vo.setRefunded(cnt);
                    vo.setAfterSale(vo.getAfterSale() + cnt);
                }
                default -> {
                }
            }
        }
        vo.setTotal(total);
        return vo;
    }

    private static Integer toInt(Object value) {
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private static long toLong(Object value) {
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }
}
