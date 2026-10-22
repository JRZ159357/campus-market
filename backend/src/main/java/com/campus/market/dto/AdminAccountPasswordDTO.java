// 文件路径: src/main/java/com/campus/market/dto/AdminAccountPasswordDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置后台账号密码入参。
 * <p>
 * 只收新密码，不收旧密码：这是「管理员替别人重置」的场景，
 * 管理员本来就不该知道别人的旧密码。因此这个接口是高危操作，
 * 权限只给管理员，且强制写操作日志。
 */
@Data
@Schema(description = "重置密码参数")
public class AdminAccountPasswordDTO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需为6-32位")
    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
