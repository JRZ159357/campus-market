// 文件路径: src/main/java/com/campus/market/vo/HomeCategorySortVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 金刚区配置项（模块 12）：一个分类在首页的展示状态与顺序。
 * <p>
 * 复用 category 表而不另建「金刚区表」：金刚区是分类的一个视图属性。
 * 拆表的话分类改名要同步两处，分类删除要级联清理，收益为负。
 */
@Data
@Schema(description = "金刚区配置项")
public class HomeCategorySortVO {

    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "图标：图片地址或emoji文本")
    private String icon;

    @Schema(description = "排序值，越小越靠前")
    private Integer sort;

    @Schema(description = "是否显示在首页金刚区:0否 1是")
    private Integer isHomeShow;

    @Schema(description = "分类自身状态:0禁用 1启用")
    private Integer status;

    @Schema(description = "该分类下已上架商品数，用于判断「空分类是否值得放首页」")
    private Long productCount;
}
