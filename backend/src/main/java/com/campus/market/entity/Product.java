// 文件路径: src/main/java/com/campus/market/entity/Product.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体，对应表 product。
 */
@Data
@TableName("product")
@Schema(description = "商品")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "卖家用户ID")
    private Long sellerId;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "商品描述")
    private String description;

    @Schema(description = "售价")
    private BigDecimal price;

    @Schema(description = "原价")
    private BigDecimal originalPrice;

    @Schema(description = "状态:0待审核 1已上架 2已下架 3已售出 4违规下架")
    private Integer status;

    @Schema(description = "浏览数")
    private Integer viewCount;

    @Schema(description = "收藏数")
    private Integer favoriteCount;

    @Schema(description = "卖家是否认证学生:0否 1是")
    private Integer isVerifiedSeller;

    /**
     * 待人工复核标记（模块11）。
     * <p>
     * 商品发布时命中了「警告级」敏感词就置 1：商品照常进入待审核队列，
     * 但审核页会把它单独列在一个 Tab 里。为什么不用 status 表达：
     * status 是商品的对外生命周期（待审核/上架/下架），
     * 「需要重点看」是审核侧的工作标记，两者维度不同，
     * 塞进同一个字段会导致审核通过后必须二选一地丢掉其中一个语义。
     */
    @Schema(description = "待人工复核:0否 1是（命中警告级敏感词）")
    private Integer needManualReview;

    @Schema(description = "命中的敏感词，逗号分隔（审核时的重点提示）")
    private String sensitiveHit;

    @Schema(description = "审核驳回原因 / 强制下架原因")
    private String auditReason;

    @Schema(description = "审核人ID")
    private Long auditorId;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "发布时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableLogic
    @Schema(description = "逻辑删除:0未删 1已删")
    private Integer deleted;
}
