// 文件路径: src/main/java/com/campus/market/vo/ReportStatsVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 举报统计（管理端工作台与统计卡片）。
 * <p>
 * 处理率与平均处理时长的口径在这里说清楚，避免论文里被追问时说不明白：
 * <ul>
 *   <li><b>处理率</b> = (已处理 + 已驳回) / 全部举报。
 *       驳回也算「处理完了」——客服已经把结论告诉举报人了，
 *       把驳回排除在分子外会让客服越认真处理、处理率反而越低。</li>
 *   <li><b>平均处理时长</b> = 已处理与已驳回记录的处理时间 − 接单时间 的均值，
 *       不是「提交时间 → 处理时间」：后者会把用户在待处理队列里的等待时间
 *       算进客服头上，指标失去指导意义。</li>
 * </ul>
 */
@Data
@Schema(description = "举报统计")
public class ReportStatsVO {

    @Schema(description = "举报总数")
    private Long totalCount;

    @Schema(description = "待处理数（顶部红色角标）")
    private Long pendingCount;

    @Schema(description = "处理中数")
    private Long processingCount;

    @Schema(description = "已处理数（举报成立）")
    private Long handledCount;

    @Schema(description = "已驳回数（举报不成立）")
    private Long rejectedCount;

    @Schema(description = "今日新增")
    private Long todayCount;

    @Schema(description = "今日已处理")
    private Long todayHandledCount;

    @Schema(description = "处理率(0~1)")
    private Double handleRate;

    @Schema(description = "平均处理时长（小时，从接单算到处理完成；无数据为0）")
    private Double avgHandleHours;
}