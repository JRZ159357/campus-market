// 文件路径: src/main/java/com/campus/market/dto/BannerSaveDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮播图新增/编辑入参。
 * link_value 与 link_type 的联动校验放在 Service，便于同时覆盖新增与编辑。
 */
@Data
@Schema(description = "轮播图保存参数")
public class BannerSaveDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 80, message = "标题不能超过80个字")
    @Schema(description = "标题")
    private String title;

    @NotBlank(message = "请上传轮播图图片")
    @Size(max = 500, message = "图片地址过长")
    @Schema(description = "图片地址")
    private String imageUrl;

    @NotNull(message = "请选择跳转类型")
    @Min(value = 1, message = "跳转类型取值为1商品 2分类 3外部链接 4无跳转")
    @Max(value = 4, message = "跳转类型取值为1商品 2分类 3外部链接 4无跳转")
    @Schema(description = "跳转类型:1商品详情 2分类页 3外部链接 4无跳转")
    private Integer linkType;

    @Size(max = 500, message = "跳转值过长")
    @Schema(description = "跳转值：商品ID / 分类ID / 完整URL")
    private String linkValue;

    @Schema(description = "排序值，越小越靠前；不传则排到末尾")
    private Integer sort;

    @Schema(description = "状态:0禁用 1启用，默认1")
    private Integer status;

    @Schema(description = "生效时间，格式 yyyy-MM-dd HH:mm:ss，为空表示立即生效")
    private LocalDateTime startTime;

    @Schema(description = "失效时间，格式 yyyy-MM-dd HH:mm:ss，为空表示长期有效")
    private LocalDateTime endTime;
}
