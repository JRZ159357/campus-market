// 文件路径: src/main/java/com/campus/market/dto/AddressSaveDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 地址新增 / 修改参数。
 */
@Data
@Schema(description = "地址保存参数")
public class AddressSaveDTO {

    @NotBlank(message = "收货人不能为空")
    @Size(max = 20, message = "收货人姓名最多20个字")
    @Schema(description = "收货人", example = "张同学")
    private String receiverName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "联系电话", example = "13800138000")
    private String phone;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 120, message = "详细地址最多120个字")
    @Schema(description = "详细地址，可填宿舍楼 / 教学楼 / 约定见面点", example = "3号宿舍楼 502")
    private String address;

    @Schema(description = "是否设为默认地址")
    private Boolean isDefault;
}
