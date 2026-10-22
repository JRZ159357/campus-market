// 文件路径: src/main/java/com/campus/market/dto/StudentAuthApplyDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生认证申请入参。
 */
@Data
@Schema(description = "学生认证申请入参")
public class StudentAuthApplyDTO {

    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^[0-9A-Za-z]{6,30}$", message = "学号格式不正确（6-30位数字或字母）")
    @Schema(description = "学号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String studentNo;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 30, message = "姓名不能超过30个字符")
    @Schema(description = "真实姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String realName;

    @Size(max = 100, message = "学校名称不能超过100个字符")
    @Schema(description = "学校名称")
    private String school;

    @NotBlank(message = "请上传学生证照片")
    @Schema(description = "学生证照片地址（先调用文件上传接口获得）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String certImg;
}
