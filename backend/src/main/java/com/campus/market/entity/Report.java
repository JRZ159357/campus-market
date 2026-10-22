// 文件路径: src/main/java/com/campus/market/entity/Report.java
// SQL 路径: sql/V10__report_operation_log.sql
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 举报实体，对应表 report。
 * <p>
 * 一张表承载商品 / 用户 / 评价三类举报，靠 {@code reportType} 区分。
 * {@code targetId} 在三类下分别是商品ID / 用户ID / 评价ID——
 * 这里没有做多态关联（不建 product_report / user_report 等关联表），
 * 因为举报记录是「一次性事件」而不是「长期关系」：
 * 关联表能带来的只有外键约束，而 MyBatis-Plus 生态下没人真的用数据库外键，
 * 换成三张表反而让客服列表查询写成 UNION。
 * <p>
 * {@code targetSnapshot} 是本表的核心字段，见类注释下方说明。
 */
@Data
@TableName("report")
@Schema(description = "举报")
public class Report implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "举报人ID")
    private Long reporterId;

    @Schema(description = "举报类型:1商品 2用户 3评价")
    private Integer reportType;

    @Schema(description = "被举报对象ID（商品ID/用户ID/评价ID）")
    private Long targetId;

    @Schema(description = "被举报人ID（对象归属人，冗余用于统计与通知）")
    private Long targetUserId;

    /**
     * 被举报对象快照（JSON 字符串）。
     * <p>
     * 存的是「举报那一刻对象长什么样」，而不是靠 JOIN 去查当前状态。
     * 必须这么做的三个理由：
     * <ol>
     *   <li>违规商品会被强制下架、违规评价会被逻辑删除、违规用户会改名换头像；
     *       客服过两天处理时，原对象可能已经查不到或完全变了样；</li>
     *   <li>举报成立与否是「当时的判断」，用可变的当前状态举证会失真；</li>
     *   <li>卖家先下架商品再被举报的情况很常见，快递类纠纷里尤其多，
     *       此时 JOIN 查不到任何内容，举报记录就成了空壳。</li>
     * </ol>
     * 代价是 JSON 冗余（本系统单条约 200~500 字节），换来取证闭环。
     */
    @JsonIgnore
    @Schema(description = "被举报对象快照JSON（原始字符串，出参由 VO 解析）")
    private String targetSnapshot;

    @Schema(description = "举报原因:1虚假描述 2违禁品 3欺诈 4辱骂骚扰 5广告 6其他")
    private Integer reason;

    @Schema(description = "详细描述")
    private String content;

    @Schema(description = "证据图片URL，逗号分隔，最多3张")
    private String images;

    @Schema(description = "状态:0待处理 1处理中 2已处理 3已驳回")
    private Integer status;

    @Schema(description = "处理人ID")
    private Long handlerId;

    /**
     * 接单时间。与提交时间分开记，是为了让「平均处理时长」只统计客服实际花掉的时间。
     */
    @Schema(description = "接单时间")
    private LocalDateTime acceptTime;

    @Schema(description = "处理结果描述")
    private String handleResult;

    @Schema(description = "处理动作:0不处理 1下架商品 2封禁用户 3删除评价 4扣除信誉分")
    private Integer handleAction;

    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "提交时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableLogic
    @Schema(description = "逻辑删除:0正常 1删除")
    private Integer deleted;
}