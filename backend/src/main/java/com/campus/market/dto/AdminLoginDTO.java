// 文件路径: src/main/java/com/campus/market/dto/AdminLoginDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端账号密码登录入参。
 */
@Data
@Schema(description = "管理端登录入参")
public class AdminLoginDTO {

    @NotBlank(message = "账号不能为空")
    @Schema(description = "登录账号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "登录密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
