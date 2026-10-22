// 文件路径: src/main/java/com/campus/market/vo/CreditLogVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 信誉分变动记录。
 * <p>
 * 同时给出 changeScore 与 before/after 两个快照：前端既能显示「+2」，
 * 也能显示「100 → 102」，用户能自己算出账对得上，
 * 这比只给一个变动值更容易建立信任。
 */
@Data
@Schema(description = "信誉分变动记录")
public class CreditLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "变动值")
    private Integer changeScore;

    @Schema(description = "变动前")
    private Integer beforeScore;

    @Schema(description = "变动后")
    private Integer afterScore;

    @Schema(description = "类型:1收到评价 2举报扣分 3管理员调整 4评价被删除")
    private Integer type;

    @Schema(description = "类型名称")
    private String typeLabel;

    @Schema(description = "变动原因")
    private String reason;

    @Schema(description = "关联业务ID")
    private Long relatedId;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人昵称")
    private String operatorName;

    @Schema(description = "发生时间")
    private LocalDateTime createTime;
}
