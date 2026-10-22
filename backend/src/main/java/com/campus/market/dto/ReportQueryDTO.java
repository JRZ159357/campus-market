// 文件路径: src/main/java/com/campus/market/dto/ReportQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 管理端举报列表查询条件。
 * <p>
 * 与其他管理端查询 DTO 一致：不加 {@code @Min/@Max} 之类的强校验，
 * 因为这里的值来自 el-select / el-date-picker，非法值交给 Service 兜底为「不过滤」，
 * 比返回 400 更符合管理端「筛选条件随便改」的使用习惯。
 */
@Data
@Schema(description = "举报列表查询条件")
public class ReportQueryDTO {

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页条数")
    private Long pageSize;

    @Schema(description = "举报类型:1商品 2用户 3评价")
    private Integer reportType;

    @Schema(description = "状态:0待处理 1处理中 2已处理 3已驳回")
    private Integer status;

    @Schema(description = "关键词（举报人昵称 / 被举报对象标题或昵称 / 举报内容）")
    private String keyword;

    @Schema(description = "只看待处理（用于顶部角标与工作台快捷入口）")
    private Boolean pendingOnly;

    @Schema(description = "处理人ID")
    private Long handlerId;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "提交时间起")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "提交时间止")
    private LocalDateTime endTime;
}