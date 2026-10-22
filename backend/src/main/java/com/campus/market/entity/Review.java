// 文件路径: src/main/java/com/campus/market/entity/Review.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单评价实体，对应表 review。
 * <p>
 * 一条记录代表「一个方向」的评价：review_type=1 是买家评卖家，=2 是卖家评买家。
 * 同一笔订单最多两条记录，由 uk_order_type(order_id, review_type) 在数据库层保证。
 * <p>
 * 维度列允许为 NULL 而不是填 0：买家评卖家没有「付款速度」，填 0 会让
 * 平均星级被拉低；NULL 参与 AVG 时会被自动忽略，语义上也更准确。
 * <p>
 * images / followImages 存 JSON 数组字符串（如 ["/uploads/a.png"]），
 * 不单独建图片表：评价图片最多 3 张，且永远整体读写，拆表只会多一次 JOIN。
 */
@Data
@TableName("review")
@Schema(description = "订单评价")
public class Review implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号（冗余）")
    private String orderNo;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "评价人ID")
    private Long reviewerId;

    @Schema(description = "被评价人ID")
    private Long revieweeId;

    @Schema(description = "评价方向:1买家评卖家 2卖家评买家")
    private Integer reviewType;

    @Schema(description = "综合星级1-5，各维度平均四舍五入")
    private Integer stars;

    @Schema(description = "描述相符1-5，仅买家评卖家")
    private Integer descStars;

    @Schema(description = "发货速度1-5，仅买家评卖家")
    private Integer shipStars;

    @Schema(description = "沟通态度1-5，两个方向都有")
    private Integer attitudeStars;

    @Schema(description = "付款速度1-5，仅卖家评买家")
    private Integer payStars;

    @Schema(description = "文字评价")
    private String content;

    @Schema(description = "图片JSON数组，最多3张")
    private String images;

    @Schema(description = "是否匿名:0实名 1匿名")
    private Integer isAnonymous;

    @Schema(description = "卖家回复内容")
    private String replyContent;

    @Schema(description = "卖家回复时间")
    private LocalDateTime replyTime;

    @Schema(description = "追评内容")
    private String followContent;

    @Schema(description = "追评图片JSON数组")
    private String followImages;

    @Schema(description = "追评时间")
    private LocalDateTime followTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "评价时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableLogic
    @Schema(description = "逻辑删除:0正常 1已删除")
    private Integer deleted;
}
