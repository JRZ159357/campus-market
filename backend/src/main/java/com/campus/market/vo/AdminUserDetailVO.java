// 文件路径: src/main/java/com/campus/market/vo/AdminUserDetailVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端用户详情。
 * <p>
 * 一个弹窗里要同时回答管理员的五个问题：
 * 这是谁（基本信息）、信誉分怎么变的（变动记录）、
 * 卖过什么（发布商品）、买过什么（订单记录）、口碑如何（评价记录）。
 * 分成五个接口会让弹窗变成五段瀑布流，这里一次返回。
 */
@Data
@Schema(description = "管理端用户详情")
public class AdminUserDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "基本信息")
    private AdminUserVO user;

    @Schema(description = "个人简介")
    private String intro;

    @Schema(description = "性别中文名")
    private String genderLabel;

    @Schema(description = "学校")
    private String school;

    @Schema(description = "学号")
    private String studentNo;

    @Schema(description = "真实姓名（已认证学生）")
    private String realName;

    @Schema(description = "最近 20 条信誉分变动")
    private List<CreditLogVO> creditLogs;

    @Schema(description = "最近 20 个发布商品")
    private List<ProductListVO> products;

    @Schema(description = "最近 20 条订单记录")
    private List<OrderListVO> orders;

    @Schema(description = "最近 20 条收到的评价")
    private List<ReviewVO> reviews;

    @Schema(description = "发布商品总数")
    private Long publishCount;

    @Schema(description = "订单总数（买 + 卖）")
    private Long orderCount;

    @Schema(description = "收到的评价总数")
    private Long reviewCount;

    @Schema(description = "累计成交金额")
    private java.math.BigDecimal tradeAmount;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;
}
