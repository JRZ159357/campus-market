// 文件路径: src/main/java/com/campus/market/entity/ProductImage.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品图片实体，对应表 product_image。sort=1 为主图。
 */
@Data
@TableName("product_image")
@Schema(description = "商品图片")
public class ProductImage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "图片地址")
    private String imageUrl;

    @Schema(description = "排序，1为主图")
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableLogic
    @Schema(description = "逻辑删除:0未删 1已删")
    private Integer deleted;
}
