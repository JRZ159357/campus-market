// 文件路径: src/main/java/com/campus/market/dto/AccountLoginDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 账号密码登录入参。全部角色通用，与微信登录签发同一套 token。
 */
@Data
@Schema(description = "账号登录入参")
public class AccountLoginDTO {

    @NotBlank(message = "账号不能为空")
    @Schema(description = "登录账号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "登录密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
