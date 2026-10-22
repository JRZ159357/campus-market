// 文件路径: src/main/java/com/campus/market/dto/StudentAuthQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 学生认证列表查询条件。
 */
@Data
@Schema(description = "学生认证查询条件")
public class StudentAuthQueryDTO {

    @Schema(description = "页码，从1开始")
    private Integer pageNum = 1;

    @Schema(description = "每页条数")
    private Integer pageSize = 10;

    @Schema(description = "审核状态:0待审核 1通过 2驳回，不传为全部")
    private Integer status;

    @Schema(description = "关键词：学号 / 姓名 / 昵称")
    private String keyword;
}
