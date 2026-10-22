// 文件路径: src/main/java/com/campus/market/dto/UserPhoneUpdateDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改手机号入参。
 * <p>
 * 手机号不能由前端直接提交就改：任何人只要知道一个手机号，就能把别人的账号
 * 手机号换成自己的，用来接验证码、找回密码。这里必须由微信侧下发
 * getPhoneNumber 授权码，服务端用它换取真实手机号——
 * 授权动作发生在新手机号的微信账号上，等于「持有该手机号」的证明。
 */
@Data
@Schema(description = "修改手机号入参")
public class UserPhoneUpdateDTO {

    @NotBlank(message = "手机号授权code不能为空")
    @Schema(description = "getPhoneNumber 回调的 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
