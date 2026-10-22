// 文件路径: src/main/java/com/campus/market/vo/OrderLogVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单操作日志项，订单详情的时间线直接用它渲染。
 */
@Data
@Schema(description = "订单操作日志")
public class OrderLogVO {

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "动作码")
    private String action;

    @Schema(description = "动作名称")
    private String actionLabel;

    @Schema(description = "操作人角色")
    private String operatorRole;

    @Schema(description = "操作人角色名称")
    private String operatorRoleLabel;

    @Schema(description = "操作人ID，系统操作时为空")
    private Long operatorId;

    @Schema(description = "操作人昵称")
    private String operatorName;

    @Schema(description = "变更前状态")
    private Integer fromStatus;

    @Schema(description = "变更后状态")
    private Integer toStatus;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}
