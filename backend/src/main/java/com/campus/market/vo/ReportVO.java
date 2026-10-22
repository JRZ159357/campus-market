// 文件路径: src/main/java/com/campus/market/vo/ReportVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 举报出参。
 * <p>
 * 刻意把「快照 JSON」拆成 {@code snapshot} 这个 Map 字段，
 * 而不是把原始 JSON 字符串丢给前端自己 parse：
 * 前端三个页面（举报详情、客服工作台、我的举报）都要读快照，
 * 让它们各写一遍 JSON.parse 迟早出现某一个页面解析字段名写错的情况。
 */
@Data
@Schema(description = "举报信息")
public class ReportVO {

    @Schema(description = "举报ID")
    private Long id;

    @Schema(description = "举报类型:1商品 2用户 3评价")
    private Integer reportType;

    @Schema(description = "举报类型名")
    private String reportTypeLabel;

    @Schema(description = "被举报对象ID")
    private Long targetId;

    @Schema(description = "被举报对象摘要（标题/昵称/评价摘要）")
    private String targetTitle;

    @Schema(description = "被举报对象封面（商品主图 / 用户头像 / 评价配图首张）")
    private String targetCover;

    @Schema(description = "被举报人ID")
    private Long targetUserId;

    @Schema(description = "被举报人昵称")
    private String targetUserName;

    @Schema(description = "被举报人当前状态描述（如「已封禁」「账号已注销」）")
    private String targetUserStatus;

    @Schema(description = "被举报对象是否已不存在（快照仍有内容，据此提示取证依据快照）")
    private Boolean targetMissing;

    @Schema(description = "举报时刻的对象快照，字段随类型不同")
    private Map<String, Object> snapshot;

    @Schema(description = "举报原因")
    private Integer reason;

    @Schema(description = "举报原因名")
    private String reasonLabel;

    @Schema(description = "详细描述")
    private String content;

    @Schema(description = "证据图片URL列表")
    private List<String> images;

    @Schema(description = "状态:0待处理 1处理中 2已处理 3已驳回")
    private Integer status;

    @Schema(description = "状态名")
    private String statusLabel;

    @Schema(description = "状态配色，与小程序端一致")
    private String statusColor;

    @Schema(description = "处理人ID")
    private Long handlerId;

    @Schema(description = "处理人昵称")
    private String handlerName;

    @Schema(description = "处理结果描述")
    private String handleResult;

    @Schema(description = "接单时间")
    private java.time.LocalDateTime acceptTime;

    @Schema(description = "从接单到处理完成的时长（小时），用于详情页展示处理效率")
    private Double handleHours;

    @Schema(description = "处理动作")
    private Integer handleAction;

    @Schema(description = "处理动作名")
    private String handleActionLabel;

    @Schema(description = "处理时间")
    private LocalDateTime handleTime;

    @Schema(description = "举报人ID")
    private Long reporterId;

    @Schema(description = "举报人昵称")
    private String reporterName;

    @Schema(description = "举报人头像")
    private String reporterAvatar;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;

    @Schema(description = "距今小时数（管理端列表用，避免前端各写一遍日期差计算）")
    private Long hoursAgo;

    @Schema(description = "当前登录者是否已接单，用于列表页按钮态")
    private Boolean acceptedByMe;

    /**
     * 原始快照 JSON 字符串，仅供服务端内部装配使用，不返回给前端。
     * <p>
     * 管理端列表走的是「JOIN + 投影」的 SQL（一次查出 10 条并带出三个昵称），
     * 这条路径拿不到实体，只能把原始 JSON 字符串带回来，
     * 再交给 Service 里的统一装配逻辑转成 {@code snapshot} Map。
     * <p>
     * 加 {@code @JsonIgnore} 而不是另开一个 DTO：这份数据用完即弃，
     * 为它单独建一个类只会让「列表接口」和「详情接口」出现两套返回结构。
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String targetSnapshotRaw;

    /**
     * 原始证据图串（逗号分隔），仅供服务端内部装配使用。
     * <p>
     * 同 {@link #targetSnapshotRaw}：SQL 投影出来的是字符串，
     * 而对外的 {@code images} 是数组，必须在 Service 里拆一次。
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String imagesRaw;
}