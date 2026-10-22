// 文件路径: src/main/java/com/campus/market/vo/StudentAuthVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生认证列表项（管理端审核页使用），已拼装申请人昵称与手机号。
 */
@Data
@Schema(description = "学生认证申请列表项")
public class StudentAuthVO {

    @Schema(description = "申请ID")
    private Long id;

    @Schema(description = "申请人ID")
    private Long userId;

    @Schema(description = "申请人昵称")
    private String nickname;

    @Schema(description = "申请人头像")
    private String avatar;

    @Schema(description = "申请人手机号")
    private String phone;

    @Schema(description = "学号")
    private String studentNo;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "学校")
    private String school;

    @Schema(description = "学生证照片")
    private String certImg;

    @Schema(description = "审核状态:0待审核 1通过 2驳回")
    private Integer status;

    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;
}
