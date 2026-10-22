// 文件路径: src/main/java/com/campus/market/entity/OrderLog.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单操作日志实体，对应表 order_log。
 * 每次状态流转写一条，既用于详情页时间线，也用于纠纷追溯。
 */
@Data
@TableName("order_log")
@Schema(description = "订单操作日志")
public class OrderLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "操作人ID，系统自动操作为空")
    private Long operatorId;

    @Schema(description = "操作人角色:buyer/seller/admin/customer_service/system")
    private String operatorRole;

    @Schema(description = "动作")
    private String action;

    @Schema(description = "变更前状态")
    private Integer fromStatus;

    @Schema(description = "变更后状态")
    private Integer toStatus;

    @Schema(description = "备注")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}
