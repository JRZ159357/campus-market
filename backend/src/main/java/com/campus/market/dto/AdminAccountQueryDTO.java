// 文件路径: src/main/java/com/campus/market/dto/AdminAccountQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台账号列表查询条件。
 */
@Data
@Schema(description = "后台账号查询条件")
public class AdminAccountQueryDTO {

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页条数，默认10")
    private Long pageSize;

    @Schema(description = "关键词，匹配账号或昵称")
    private String keyword;

    @Schema(description = "角色:admin / customer_service；不传表示全部")
    private String role;

    @Schema(description = "状态:0正常 1封禁；不传表示全部")
    private Integer status;
}
