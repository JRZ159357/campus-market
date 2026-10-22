// 文件路径: src/main/java/com/campus/market/dto/ProductSaveDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品发布 / 编辑入参。
 * 图片按顺序提交，第 1 张为主图，最多 9 张。
 */
@Data
@Schema(description = "商品保存参数")
public class ProductSaveDTO {

    @NotBlank(message = "商品标题不能为空")
    @Size(max = 30, message = "商品标题不能超过30个字")
    @Schema(description = "标题，最多30字", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotNull(message = "请选择商品分类")
    @Schema(description = "分类ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoryId;

    @NotNull(message = "请填写商品价格")
    @DecimalMin(value = "0.01", message = "商品价格必须大于0")
    @DecimalMax(value = "999999.99", message = "商品价格超出允许范围")
    @Digits(integer = 6, fraction = 2, message = "商品价格最多保留两位小数")
    @Schema(description = "售价", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "原价必须大于0")
    @DecimalMax(value = "999999.99", message = "原价超出允许范围")
    @Digits(integer = 6, fraction = 2, message = "原价最多保留两位小数")
    @Schema(description = "原价，选填")
    private BigDecimal originalPrice;

    @Size(max = 500, message = "商品描述不能超过500个字")
    @Schema(description = "商品描述，最多500字")
    private String description;

    @NotEmpty(message = "请至少上传一张商品图片")
    @Size(max = 9, message = "商品图片最多9张")
    @Schema(description = "图片地址列表，第1张为主图", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@NotBlank(message = "图片地址不能为空") String> images;
}
