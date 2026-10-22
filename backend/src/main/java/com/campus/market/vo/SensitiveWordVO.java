// 文件路径: src/main/java/com/campus/market/vo/SensitiveWordVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 敏感词列表项。
 * <p>
 * 同时返回码值与中文名：码值给前端做筛选与配色分支用（type === 1 才用红色），
 * 中文名给表格直接展示。只给码值会让前端各自维护一份映射表，
 * 后端加一类词就要改一次前端；只给中文名则前端没法做条件判断。
 */
@Data
@Schema(description = "敏感词列表项")
public class SensitiveWordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "敏感词")
    private String word;

    @Schema(description = "类型码:1违禁品 2辱骂 3广告 4其他")
    private Integer type;

    @Schema(description = "类型中文名")
    private String typeLabel;

    @Schema(description = "等级码:1警告 2拦截")
    private Integer level;

    @Schema(description = "等级中文名")
    private String levelLabel;

    @Schema(description = "状态:0禁用 1启用")
    private Integer status;

    @Schema(description = "状态中文名")
    private String statusLabel;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
