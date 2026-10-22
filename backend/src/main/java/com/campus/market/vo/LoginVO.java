// 文件路径: src/main/java/com/campus/market/vo/LoginVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录成功返回。前端保存 token，后续请求放入 Authorization: Bearer {token}。
 */
@Data
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "角色码")
    private String role;

    @Schema(description = "角色中文名")
    private String roleLabel;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "学生认证:0未认证 1已认证")
    private Integer studentVerified;

    @Schema(description = "在售商品数量上限")
    private Integer publishLimit;
}
