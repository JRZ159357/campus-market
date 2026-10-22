// 文件路径: src/main/java/com/campus/market/vo/UserFollowVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注/粉丝列表项。
 * <p>
 * 列表页需要展示「已关注 / 回关」按钮，因此 followed 表示
 * <b>当前登录用户是否已关注该用户</b>，而不是列表本身的归属关系。
 */
@Data
@Schema(description = "关注/粉丝用户卡片")
public class UserFollowVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "是否认证学生")
    private Boolean studentVerified;

    @Schema(description = "信誉分")
    private Integer creditScore;

    @Schema(description = "在售商品数")
    private Long onSaleCount;

    @Schema(description = "粉丝数")
    private Integer fansCount;

    @Schema(description = "当前登录用户是否已关注该用户")
    private Boolean followed;

    @Schema(description = "关注发生时间：关注列表为关注TA的时间，粉丝列表为TA关注我的时间")
    private LocalDateTime followTime;
}
