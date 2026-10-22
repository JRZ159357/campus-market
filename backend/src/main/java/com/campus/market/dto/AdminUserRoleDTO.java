// 文件路径: src/main/java/com/campus/market/dto/AdminUserRoleDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 分配角色入参。管理端专用。
 */
@Data
@Schema(description = "分配角色入参")
public class AdminUserRoleDTO {

    @NotBlank(message = "请选择要分配的角色")
    @Schema(description = "角色：user普通用户 student认证学生 customer_service客服 admin管理员",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;
}
