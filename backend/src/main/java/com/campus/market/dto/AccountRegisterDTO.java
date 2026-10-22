// 文件路径: src/main/java/com/campus/market/dto/AccountRegisterDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账号密码注册入参。注册后默认角色为普通用户（user），需通过学生认证才能发布商品。
 */
@Data
@Schema(description = "账号注册入参")
public class AccountRegisterDTO {

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "账号需为4-20位字母或数字")
    @Schema(description = "登录账号，4-20位字母或数字", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需为6-20位")
    @Schema(description = "登录密码，6-20位", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Size(max = 20, message = "昵称不能超过20个字")
    @Schema(description = "昵称，留空则自动生成")
    private String nickname;
}
