// 文件路径: src/main/java/com/campus/market/vo/OperationLogVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志出参（管理端只读）。
 * <p>
 * 相比实体多了 {@code operatorRoleLabel}：角色码是英文（admin），
 * 管理端表格要显示中文（管理员），这个映射放在后端而不是前端，
 * 是为了让「新增一个角色」时只要改 RoleConst 一处。
 */
@Data
@Schema(description = "操作日志")
public class OperationLogVO {

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人昵称")
    private String operatorName;

    @Schema(description = "操作人角色码")
    private String operatorRole;

    @Schema(description = "操作人角色名")
    private String operatorRoleLabel;

    @Schema(description = "业务模块")
    private String module;

    @Schema(description = "模块中文名")
    private String moduleLabel;

    @Schema(description = "动作码")
    private String action;

    @Schema(description = "动作中文名")
    private String actionLabel;

    @Schema(description = "操作目标ID")
    private Long targetId;

    @Schema(description = "操作详情")
    private String detail;

    @Schema(description = "操作来源IP")
    private String ip;

    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}