// 文件路径: src/main/java/com/campus/market/vo/NoticeVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统通知（小程序「通知」Tab 与通知详情页共用）。
 * <p>
 * 除了原始 type，还给出 typeLabel 与 target（跳转目标），
 * 前端点击时按 target 决定跳订单、跳商品还是只展开正文：
 * <ul>
 *   <li>order：跳订单详情，用 relatedId 作为订单 ID；</li>
 *   <li>product：跳商品详情，用 relatedId 作为商品 ID；</li>
 *   <li>none：无跳转，展示纯文本公告。</li>
 * </ul>
 */
@Data
@Schema(description = "系统通知")
public class NoticeVO {

    @Schema(description = "通知ID")
    private Long id;

    @Schema(description = "类型:1订单通知 2审核通知 3系统公告 4举报处理")
    private Integer type;

    @Schema(description = "类型名称")
    private String typeLabel;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "关联业务ID")
    private Long relatedId;

    @Schema(description = "关联业务单号（订单通知返回订单号，前端跳转用）")
    private String relatedNo;

    @Schema(description = "跳转目标:order / product / none")
    private String target;

    @Schema(description = "是否已读:0未读 1已读")
    private Integer isRead;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
