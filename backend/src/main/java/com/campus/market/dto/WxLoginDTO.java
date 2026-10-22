// 文件路径: src/main/java/com/campus/market/dto/WxLoginDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 小程序微信登录入参。code 由 wx.login() 获取，一次性有效。
 */
@Data
@Schema(description = "微信登录入参")
public class WxLoginDTO {

    @NotBlank(message = "登录code不能为空")
    @Schema(description = "wx.login() 返回的临时登录凭证", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Size(max = 50, message = "昵称不能超过50个字符")
    @Schema(description = "昵称，首次登录可不传")
    private String nickname;

    @Size(max = 500, message = "头像地址过长")
    @Schema(description = "头像地址，首次登录可不传")
    private String avatar;
}
