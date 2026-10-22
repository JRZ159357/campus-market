// 文件路径: src/main/java/com/campus/market/entity/Banner.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首页轮播图实体，对应表 banner。
 */
@Data
@TableName("banner")
@Schema(description = "首页轮播图")
public class Banner implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "图片地址")
    private String imageUrl;

    @Schema(description = "跳转类型:1商品详情 2分类页 3外部链接 4无跳转")
    private Integer linkType;

    @Schema(description = "跳转值：商品ID / 分类ID / 完整URL")
    private String linkValue;

    @Schema(description = "展示位置:1首页轮播 2分类页顶部(预留)")
    private Integer position;

    @Schema(description = "排序值，越小越靠前")
    private Integer sort;

    @Schema(description = "状态:0禁用 1启用")
    private Integer status;

    @Schema(description = "生效时间，为空表示立即生效")
    private LocalDateTime startTime;

    @Schema(description = "失效时间，为空表示长期有效")
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableLogic
    @Schema(description = "逻辑删除:0未删 1已删")
    private Integer deleted;
}
