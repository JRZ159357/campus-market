// 文件路径: src/main/java/com/campus/market/dto/BindAccountDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 微信登录用户绑定账号密码入参。绑定后既可用微信登录，也可用账号密码登录。
 */
@Data
@Schema(description = "绑定账号入参")
public class BindAccountDTO {

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "账号需为4-20位字母或数字")
    @Schema(description = "待绑定的登录账号，4-20位字母或数字", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为6-20位")
    @Schema(description = "登录密码，6-20位", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
