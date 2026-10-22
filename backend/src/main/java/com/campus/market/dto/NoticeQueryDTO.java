// 文件路径: src/main/java/com/campus/market/dto/NoticeQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通知列表查询参数。
 */
@Data
@Schema(description = "通知查询参数")
public class NoticeQueryDTO {

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页条数，默认10，最大100")
    private Long pageSize;

    @Schema(description = "通知类型:1订单 2审核 3公告 4举报，不传为全部")
    private Integer type;

    @Schema(description = "只看未读:true/false")
    private Boolean onlyUnread;
}
