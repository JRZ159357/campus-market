// 文件路径: src/main/java/com/campus/market/vo/NoticeBroadcastVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端「已发布公告」列表项。
 * <p>
 * 通知表是一人一行的，公告因此也是「一次发布 = N 行」。管理端需要看的是「一次发布」这个整体，
 * 所以这里用 MIN(id) 作为分组代表 ID，并统计接收人数与已读人数。
 * 删除时按代表行回查 (title, create_time) 再整组删除，逻辑集中在 Service 里。
 */
@Data
@Schema(description = "已发布公告")
public class NoticeBroadcastVO {

    @Schema(description = "分组代表ID（该组最小通知ID）")
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "接收人数")
    private Integer receiverCount;

    @Schema(description = "已读人数")
    private Integer readCount;

    @Schema(description = "发布时间")
    private LocalDateTime createTime;
}
