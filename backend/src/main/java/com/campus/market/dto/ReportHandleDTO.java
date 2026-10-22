// 文件路径: src/main/java/com/campus/market/dto/ReportHandleDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 举报处理入参（客服 / 管理员）。
 * <p>
 * {@code status} 由处理人显式选择「已处理」还是「已驳回」，
 * 而不是服务端根据 handleAction 推断：
 * 「举报成立，但只做警告不做处置」也是一种合法结论（handleAction=NONE + status=已处理），
 * 若用动作反推状态就会把这种情况错误地标成驳回。
 */
@Data
@Schema(description = "举报处理参数")
public class ReportHandleDTO {

    @NotNull(message = "请选择处理结论")
    @Min(value = 2, message = "处理结论只能是已处理(2)或已驳回(3)")
    @Max(value = 3, message = "处理结论只能是已处理(2)或已驳回(3)")
    @Schema(description = "处理结论:2已处理 3已驳回")
    private Integer status;

    @NotNull(message = "请选择处理动作")
    @Min(value = 0, message = "处理动作不合法")
    @Max(value = 4, message = "处理动作不合法")
    @Schema(description = "处理动作:0不处理 1下架商品 2封禁用户 3删除评价 4扣除信誉分")
    private Integer handleAction;

    @NotBlank(message = "请填写处理结果")
    @Size(max = 500, message = "处理结果最多500字")
    @Schema(description = "处理结果描述（举报人与被举报人都会看到）")
    private String handleResult;
}