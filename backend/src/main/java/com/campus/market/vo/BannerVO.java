// 文件路径: src/main/java/com/campus/market/vo/BannerVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮播图列表项，管理端表格与小程序首页共用。
 */
@Data
@Schema(description = "轮播图")
public class BannerVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "图片地址")
    private String imageUrl;

    @Schema(description = "跳转类型:1商品详情 2分类页 3外部链接 4无跳转")
    private Integer linkType;

    @Schema(description = "跳转类型文案")
    private String linkTypeLabel;

    @Schema(description = "跳转值：商品ID / 分类ID / 完整URL")
    private String linkValue;

    @Schema(description = "展示位置:1首页轮播 2分类页顶部(预留)")
    private Integer position;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "状态:0禁用 1启用")
    private Integer status;

    @Schema(description = "生效时间")
    private LocalDateTime startTime;

    @Schema(description = "失效时间")
    private LocalDateTime endTime;

    @Schema(description = "当前是否在展示期（启用 + 时间窗口内）")
    private Boolean effective;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
