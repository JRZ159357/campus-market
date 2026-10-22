// 文件路径: src/main/java/com/campus/market/entity/SysUser.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体，对应表 sys_user。小程序用户与后台账号共用，通过 role 区分。
 */
@Data
@TableName("sys_user")
@Schema(description = "用户")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "微信openid")
    private String openId;

    @Schema(description = "微信unionid")
    private String unionId;

    @Schema(description = "后台登录账号")
    private String username;

    @JsonIgnore
    @Schema(description = "登录密码(BCrypt)，不对外输出")
    private String password;

    @Schema(description = "注册方式:1微信 2账号密码")
    private Integer registerType;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "性别:0保密 1男 2女")
    private Integer gender;

    @Schema(description = "个人简介")
    private String intro;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "角色:user/student/customer_service/admin")
    private String role;

    @Schema(description = "账号状态:0正常 1封禁")
    private Integer status;

    @Schema(description = "学生认证:0未认证 1已认证")
    private Integer studentVerified;

    @Schema(description = "信誉分")
    private Integer creditScore;

    @Schema(description = "在售商品数量上限")
    private Integer publishLimit;

    @Schema(description = "我收藏的商品数（冗余计数，随收藏/取消收藏增减）")
    private Integer favoriteCount;

    @Schema(description = "我关注的人数（冗余计数）")
    private Integer followCount;

    @Schema(description = "粉丝数，即关注我的人数（冗余计数）")
    private Integer fansCount;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableLogic
    @Schema(description = "逻辑删除:0未删 1已删")
    private Integer deleted;
}
