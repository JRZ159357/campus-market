// 文件路径: src/main/java/com/campus/market/dto/BindPhoneDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 手机号绑定入参。code 由小程序 button open-type="getPhoneNumber" 回调获取。
 */
@Data
@Schema(description = "手机号绑定入参")
public class BindPhoneDTO {

    @NotBlank(message = "手机号授权code不能为空")
    @Schema(description = "getPhoneNumber 回调的 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
