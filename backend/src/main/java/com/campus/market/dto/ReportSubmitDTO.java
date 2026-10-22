// 文件路径: src/main/java/com/campus/market/dto/ReportSubmitDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 举报提交入参。
 * <p>
 * 注意 {@code targetId} 不做「正整数」级别的强约束之外的校验——
 * 「目标是否存在」「目标是否是自己」必须在 Service 里查库判断，
 * 因为三个类型的 targetId 分别落在三张表上，注解层无法表达。
 */
@Data
@Schema(description = "举报提交参数")
public class ReportSubmitDTO {

    @NotNull(message = "请选择举报类型")
    @Min(value = 1, message = "举报类型不合法")
    @Max(value = 3, message = "举报类型不合法")
    @Schema(description = "举报类型:1商品 2用户 3评价")
    private Integer reportType;

    @NotNull(message = "缺少被举报对象")
    @Min(value = 1, message = "被举报对象不合法")
    @Schema(description = "被举报对象ID（商品ID/用户ID/评价ID）")
    private Long targetId;

    @Min(value = 1, message = "举报原因不合法")
    @Max(value = 6, message = "举报原因不合法")
    @Schema(description = "举报原因:1虚假描述 2违禁品 3欺诈 4辱骂骚扰 5广告 6其他")
    private Integer reason;

    @NotBlank(message = "请填写详细描述")
    @Size(min = 5, max = 500, message = "详细描述需5~500字")
    @Schema(description = "详细描述，5~500字")
    private String content;

    /**
     * 证据图片URL列表，最多3张。
     * <p>
     * 用 {@code @Size(max=3)} 显式拒绝而不是静默截断：
     * 静默截断会让用户以为 5 张都提交成功了，实际只存了 3 张，
     * 举报人说「我已经把证据都传上去了」而客服只看到 3 张，这种分歧更难处理。
     * 宁可当场报错让用户回去删两张。
     */
    @Size(max = 3, message = "证据图片最多3张")
    @Schema(description = "证据图片URL列表，最多3张")
    private List<String> images;
}