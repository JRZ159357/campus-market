// 文件路径: src/main/java/com/campus/market/dto/UserAccountDeleteDTO.java
package com.campus.market.dto;

import com.campus.market.common.UserRule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注销账号入参。
 * <p>
 * 注销是不可逆操作，必须三重确认：
 * <ol>
 *   <li>确认口令 confirmText 必须原样等于「注销账号」——防止手滑点了按钮；</li>
 *   <li>密码必须正确——证明是账号本人操作；</li>
 *   <li>服务端会再校验当前角色，普通用户与认证学生才允许注销。</li>
 * </ol>
 */
@Data
@Schema(description = "注销账号入参")
public class UserAccountDeleteDTO {

    @NotBlank(message = "请输入密码以确认注销")
    @Schema(description = "当前登录密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "请输入确认口令")
    @Size(max = 20, message = "确认口令过长")
    @Schema(description = "确认口令，必须输入「注销账号」", requiredMode = Schema.RequiredMode.REQUIRED)
    private String confirmText;
}
