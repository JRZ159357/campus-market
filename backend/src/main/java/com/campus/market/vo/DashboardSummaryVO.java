// 文件路径: src/main/java/com/campus/market/vo/DashboardSummaryVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 仪表盘核心指标。
 * <p>
 * 每一个数字都在这里有明确的口径说明，答辩时「这个统计怎么算的」不需要临场编。
 * <p>
 * 环比（growthXxx）统一采用「近 7 日 vs 前 7 日」的百分比：
 * <ul>
 *   <li>大于 0 表示增长，前端显示绿色向上箭头；</li>
 *   <li>小于 0 表示下降，显示红色向下箭头；</li>
 *   <li>前 7 日为 0 时无法计算百分比，此时返回 null，前端显示「—」而不是「+∞%」。</li>
 * </ul>
 */
@Data
@Schema(description = "仪表盘核心指标")
public class DashboardSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 用户 ====================

    @Schema(description = "总用户数（不含已注销）")
    private Long totalUsers;

    @Schema(description = "今日新增用户数")
    private Long newUsersToday;

    @Schema(description = "近7日新增用户数")
    private Long newUsersWeek;

    @Schema(description = "用户数环比百分比(近7日 vs 前7日)，前期为0时为null")
    private Double userGrowth;

    // ==================== 商品 ====================

    @Schema(description = "总商品数")
    private Long totalProducts;

    @Schema(description = "在售商品数")
    private Long onSaleProducts;

    @Schema(description = "待审核商品数")
    private Long pendingAuditProducts;

    @Schema(description = "商品数环比百分比(近7日 vs 前7日)")
    private Double productGrowth;

    // ==================== 订单 ====================

    @Schema(description = "总订单数")
    private Long totalOrders;

    @Schema(description = "已完成订单数")
    private Long completedOrders;

    @Schema(description = "累计成交额(元)，仅统计已完成订单")
    private BigDecimal gmvTotal;

    @Schema(description = "今日成交额(元)，按完成时间归集")
    private BigDecimal gmvToday;

    @Schema(description = "订单数环比百分比(近7日 vs 前7日)")
    private Double orderGrowth;

    @Schema(description = "成交额环比百分比(近7日 vs 前7日)")
    private Double gmvGrowth;

    // ==================== 举报与退款 ====================

    @Schema(description = "待处理举报数(待处理+处理中)")
    private Long pendingReports;

    @Schema(description = "举报数环比百分比(近7日 vs 前7日)")
    private Double reportGrowth;

    @Schema(description = "待处理退款数(退款中)")
    private Long pendingRefunds;

    // ==================== 其他 ====================

    @Schema(description = "活跃会话数(近24小时有消息往来的聊天会话)")
    private Long activeSessions;

    @Schema(description = "平均信誉分，保留1位小数")
    private Double avgCreditScore;

    @Schema(description = "平均信誉分等级名(差/一般/良好/优秀/极佳)")
    private String avgCreditLevel;

    @Schema(description = "平均信誉分等级配色，与小程序端保持一致")
    private String avgCreditColor;

    @Schema(description = "数据统计时间")
    private String statTime;
}
