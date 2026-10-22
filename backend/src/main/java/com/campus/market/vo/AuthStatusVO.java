// 文件路径: src/main/java/com/campus/market/vo/AuthStatusVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生认证状态，用于小程序"认证状态查询"与个人中心徽章展示。
 */
@Data
@Schema(description = "学生认证状态")
public class AuthStatusVO {

    @Schema(description = "是否已认证:true已认证 false未认证")
    private Boolean verified;

    @Schema(description = "最近一次申请状态:0待审核 1通过 2驳回；无申请记录返回null")
    private Integer status;

    @Schema(description = "状态文案")
    private String statusLabel;

    @Schema(description = "驳回原因，仅驳回时有值")
    private String rejectReason;

    @Schema(description = "学号")
    private String studentNo;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "学校")
    private String school;

    @Schema(description = "提交时间")
    private LocalDateTime applyTime;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;
}
