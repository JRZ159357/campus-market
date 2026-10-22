// 文件路径: src/main/java/com/campus/market/vo/UserHomeVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 卖家主页数据（免登录可看）。
 * <p>
 * 与个人中心的区别：
 * <ul>
 *   <li>个人中心是「我的」，什么功能都能点；卖家主页是「别人的」，
 *       只能看 + 关注 + 私信，所以不含订单数、收藏数这类私有数据；</li>
 *   <li>卖家主页要展示「好评率」，这是促成交易的信任指标，
 *       因此必须给出评价总数与好评率，而不是只有信誉分。</li>
 * </ul>
 */
@Data
@Schema(description = "卖家主页数据")
public class UserHomeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "性别中文名")
    private String genderLabel;

    @Schema(description = "个人简介")
    private String intro;

    @Schema(description = "角色中文名")
    private String roleLabel;

    @Schema(description = "是否认证学生")
    private Boolean studentVerified;

    @Schema(description = "信誉分")
    private Integer creditScore;

    @Schema(description = "信誉等级名称")
    private String creditLevelLabel;

    @Schema(description = "信誉等级颜色")
    private String creditLevelColor;

    @Schema(description = "在售商品数")
    private Long onSaleCount;

    @Schema(description = "已售出商品数")
    private Long soldCount;

    @Schema(description = "收到的评价总数")
    private Long reviewCount;

    @Schema(description = "好评率（百分比，保留一位小数）")
    private Double goodRate;

    @Schema(description = "平均星级")
    private Double avgStars;

    @Schema(description = "粉丝数")
    private Long fansCount;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;

    @Schema(description = "当前查看者是否已关注该用户")
    private Boolean followed;

    @Schema(description = "当前查看者是不是本人")
    private Boolean isSelf;

    @Schema(description = "最近收到的评价")
    private List<ReviewVO> recentReviews;
}
