// 文件路径: src/main/java/com/campus/market/dto/OperationLogQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 操作日志查询条件。
 * <p>
 * 刻意不提供 {@code keyword} 模糊搜索全文：
 * detail 是自然语言描述，全表 LIKE '%xx%' 在日志表这种「只增不改、量级增长快」
 * 的表上代价太高，而且管理员真正想查的是「谁做了什么」，
 * 按操作人 + 模块 + 时间三维筛选已经够用。
 */
@Data
@Schema(description = "操作日志查询条件")
public class OperationLogQueryDTO {

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页条数")
    private Long pageSize;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人昵称关键词")
    private String operatorName;

    @Schema(description = "业务模块")
    private String module;

    @Schema(description = "动作")
    private String action;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "操作时间起")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "操作时间止")
    private LocalDateTime endTime;
}