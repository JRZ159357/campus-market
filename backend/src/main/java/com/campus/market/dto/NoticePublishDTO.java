// 文件路径: src/main/java/com/campus/market/dto/NoticePublishDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布系统公告参数。
 * <p>
 * 广播范围二选一：scope=1 发给全部正常用户，scope=2 只发给 userIds 里的指定用户。
 * 采用「按用户拆行」写入，因此「全部用户」会生成 N 条通知记录（N=用户数）。
 */
@Data
@Schema(description = "发布系统公告参数")
public class NoticePublishDTO {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 60, message = "公告标题不能超过60字")
    @Schema(description = "标题")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 500, message = "公告内容不能超过500字")
    @Schema(description = "正文")
    private String content;

    @Min(1)
    @Max(2)
    @Schema(description = "发布范围:1全部用户 2指定用户")
    private Integer scope = 1;

    @Schema(description = "指定用户ID列表，scope=2 时必填")
    private List<Long> userIds;
}
