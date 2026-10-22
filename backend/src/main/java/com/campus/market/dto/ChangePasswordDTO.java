// 文件路径: src/main/java/com/campus/market/dto/ChangePasswordDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码入参。管理端与小程序共用。
 */
@Data
@Schema(description = "修改密码入参")
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    @Schema(description = "原密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度需为6-20位")
    @Schema(description = "新密码，6-20位", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
