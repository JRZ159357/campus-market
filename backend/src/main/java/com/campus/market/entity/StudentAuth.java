// 文件路径: src/main/java/com/campus/market/entity/StudentAuth.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学生认证申请实体，对应表 student_auth。
 * 一名用户可有多条记录（被驳回后可重新提交），但同一时刻只允许一条待审核。
 */
@Data
@TableName("student_auth")
@Schema(description = "学生认证申请")
public class StudentAuth implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "申请用户ID")
    private Long userId;

    @Schema(description = "学号")
    private String studentNo;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "学校名称")
    private String school;

    @Schema(description = "学生证照片地址")
    private String certImg;

    @Schema(description = "审核状态:0待审核 1通过 2驳回")
    private Integer status;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "审核人ID")
    private Long auditorId;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "申请时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableLogic
    @Schema(description = "逻辑删除:0未删 1已删")
    private Integer deleted;
}
