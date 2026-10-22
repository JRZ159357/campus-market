// 文件路径: src/main/java/com/campus/market/vo/CategoryVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类列表项，含该分类下商品数量（管理端展示用）。
 */
@Data
@Schema(description = "商品分类列表项")
public class CategoryVO {

    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "图标：图片地址或emoji文本")
    private String icon;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "是否显示在首页金刚区:0否 1是")
    private Integer isHomeShow;

    @Schema(description = "状态:0禁用 1启用")
    private Integer status;

    @Schema(description = "该分类下商品数量")
    private Long productCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
