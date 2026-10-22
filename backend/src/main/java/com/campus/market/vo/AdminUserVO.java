// 文件路径: src/main/java/com/campus/market/vo/AdminUserVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端用户列表行。
 */
@Data
@Schema(description = "管理端用户列表项")
public class AdminUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "性别:0保密 1男 2女")
    private Integer gender;

    @Schema(description = "个人简介")
    private String intro;

    @Schema(description = "角色码")
    private String role;

    @Schema(description = "角色中文名")
    private String roleLabel;

    @Schema(description = "账号状态:0正常 1封禁")
    private Integer status;

    @Schema(description = "状态文案")
    private String statusLabel;

    @Schema(description = "学生认证:0未认证 1已认证")
    private Integer studentVerified;

    @Schema(description = "认证状态文案")
    private String studentVerifiedLabel;

    @Schema(description = "学号")
    private String studentNo;

    @Schema(description = "信誉分")
    private Integer creditScore;

    @Schema(description = "信誉等级名称")
    private String creditLevelLabel;

    @Schema(description = "信誉等级颜色")
    private String creditLevelColor;

    @Schema(description = "发布的商品数")
    private Long publishCount;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;
}
