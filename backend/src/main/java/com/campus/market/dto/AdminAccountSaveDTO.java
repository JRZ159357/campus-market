// 文件路径: src/main/java/com/campus/market/dto/AdminAccountSaveDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台账号新增 / 编辑入参。
 * <p>
 * 新增与编辑共用一个 DTO，靠 {@code password} 是否为空区分场景：
 * 新增必须给密码，编辑时为空表示不改密码（改密码走单独的重置接口，
 * 那条路径有独立的权限判断与操作日志）。
 */
@Data
@Schema(description = "后台账号保存参数")
public class AdminAccountSaveDTO {

    /**
     * 账号。只在新增时生效：账号是登录凭证与审计主键的一部分，
     * 允许改账号会让「操作日志里那个人」和历史记录对不上。
     */
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "账号需为4-20位字母或数字")
    @Schema(description = "登录账号，新增必填；编辑时忽略")
    private String username;

    @Size(min = 6, max = 32, message = "密码长度需为6-32位")
    @Schema(description = "登录密码，新增必填；编辑时留空表示不修改")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称不能超过50个字")
    @Schema(description = "昵称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;

    @NotBlank(message = "请选择角色")
    @Schema(description = "角色:admin / customer_service", requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;

    @Schema(description = "状态:0正常 1封禁；新增默认0正常")
    private Integer status;
}
