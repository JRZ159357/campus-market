// 文件路径: src/main/java/com/campus/market/vo/UserInfoVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前登录用户信息（小程序个人中心顶部卡片数据来源）。
 */
@Data
@Schema(description = "用户信息")
public class UserInfoVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录账号，未绑定时为null")
    private String username;

    @Schema(description = "注册方式:1微信 2账号密码")
    private Integer registerType;

    @Schema(description = "注册方式中文名")
    private String registerTypeLabel;

    @Schema(description = "是否已设置账号密码（微信用户绑定后为true）")
    private Boolean hasAccount;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "手机号（未绑定返回null）")
    private String phone;

    @Schema(description = "角色码")
    private String role;

    @Schema(description = "角色中文名")
    private String roleLabel;

    @Schema(description = "学生认证:0未认证 1已认证")
    private Integer studentVerified;

    @Schema(description = "信誉分")
    private Integer creditScore;

    @Schema(description = "在售商品数量上限")
    private Integer publishLimit;

    @Schema(description = "我收藏的商品数")
    private Integer favoriteCount;

    @Schema(description = "我关注的人数")
    private Integer followCount;

    @Schema(description = "我的粉丝数")
    private Integer fansCount;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;
}
