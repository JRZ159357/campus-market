// 文件路径: src/main/java/com/campus/market/entity/Category.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品分类实体，对应表 category。
 */
@Data
@TableName("category")
@Schema(description = "商品分类")
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "图标：图片地址或emoji文本")
    private String icon;

    @Schema(description = "排序值，越小越靠前")
    private Integer sort;

    @Schema(description = "是否显示在首页金刚区:0否 1是")
    private Integer isHomeShow;

    @Schema(description = "状态:0禁用 1启用")
    private Integer status;

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
