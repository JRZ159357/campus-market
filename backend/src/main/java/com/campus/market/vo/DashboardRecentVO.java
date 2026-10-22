// 文件路径: src/main/java/com/campus/market/vo/DashboardRecentVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 仪表盘「最近动态」条目。
 * <p>
 * 与 {@link OperationLogVO} 的区别是多了 {@code operatorAvatar}：
 * 时间线要显示操作人头像，而操作日志表只冗余了昵称，没有头像，
 * 需要 JOIN 用户表取。昵称仍然以日志里的冗余值为准——
 * 用户改过昵称后，日志要保留当时的名称，否则追溯时会指向一个陌生人。
 */
@Data
@Schema(description = "仪表盘最近动态")
public class DashboardRecentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人昵称")
    private String operatorName;

    @Schema(description = "操作人头像")
    private String operatorAvatar;

    @Schema(description = "操作人角色码")
    private String operatorRole;

    @Schema(description = "业务模块")
    private String module;

    @Schema(description = "模块中文名")
    private String moduleLabel;

    @Schema(description = "动作码")
    private String action;

    @Schema(description = "动作中文名")
    private String actionLabel;

    @Schema(description = "操作目标ID")
    private Long targetId;

    @Schema(description = "操作详情")
    private String detail;

    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}
