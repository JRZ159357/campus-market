// 文件路径: src/main/java/com/campus/market/vo/UserProfileVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 个人中心聚合数据。
 * <p>
 * 一次请求返回个人中心首屏需要的全部数据：用户信息、信誉分等级、
 * 六个统计数、各状态订单数。
 * <p>
 * 为什么不拆成「个人信息 + 统计数」两个接口：
 * 个人中心是「一进页面就要全部渲染」的页面，前端串 5~6 个请求
 * （用户信息、信誉分、订单数、发布数、待评价数、收藏数）
 * 会导致首屏出现明显的分块加载与数据不一致窗口。
 * 这里用一次聚合并行查出所有数字，代价是多几个 COUNT，
 * 换来的是首屏一次到位。
 */
@Data
@Schema(description = "个人中心聚合数据")
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "性别:0保密 1男 2女")
    private Integer gender;

    @Schema(description = "性别中文名")
    private String genderLabel;

    @Schema(description = "个人简介")
    private String intro;

    @Schema(description = "手机号（未绑定返回null）")
    private String phone;

    @Schema(description = "登录账号，未绑定时为null")
    private String username;

    @Schema(description = "是否已设置账号密码")
    private Boolean hasAccount;

    @Schema(description = "角色码")
    private String role;

    @Schema(description = "角色中文名")
    private String roleLabel;

    @Schema(description = "学生认证:0未认证 1已认证")
    private Integer studentVerified;

    @Schema(description = "学号，已认证才有")
    private String studentNo;

    @Schema(description = "信誉分")
    private Integer creditScore;

    @Schema(description = "信誉等级名称：差/一般/良好/优秀/极佳")
    private String creditLevelLabel;

    @Schema(description = "信誉等级颜色")
    private String creditLevelColor;

    @Schema(description = "当前等级内进度 0-100")
    private Integer creditProgress;

    @Schema(description = "发布的商品总数")
    private Long publishCount;

    @Schema(description = "已售出商品数")
    private Long soldCount;

    @Schema(description = "在售商品数")
    private Long onSaleCount;

    @Schema(description = "我收藏的商品数")
    private Long favoriteCount;

    @Schema(description = "我关注的人数")
    private Long followCount;

    @Schema(description = "我的粉丝数")
    private Long fansCount;

    @Schema(description = "待评价订单数")
    private Long pendingReviewCount;

    @Schema(description = "各状态订单数（买家侧）")
    private OrderCountVO orderCount;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;
}
