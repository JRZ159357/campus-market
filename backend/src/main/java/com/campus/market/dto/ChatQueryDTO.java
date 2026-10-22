// 文件路径: src/main/java/com/campus/market/dto/ChatQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 聊天相关查询参数。
 * <p>
 * 小程序侧用游标（cursor）拉历史消息；管理端客服工作台用页码分页查看会话列表，
 * 两套参数放在同一个 DTO 里，避免为每种列表各建一个类。
 */
@Data
@Schema(description = "聊天查询参数")
public class ChatQueryDTO {

    @Schema(description = "游标：上一页最小消息ID，不传表示取最新一页")
    private Long cursor;

    @Schema(description = "每页条数，默认20，最大50")
    private Integer size;

    @Schema(description = "页码，从1开始（管理端会话列表用）")
    private Long pageNum;

    @Schema(description = "每页条数，默认10（管理端会话列表用）")
    private Long pageSize;

    @Schema(description = "关键词：可按用户昵称搜索（管理端客服工作台用）")
    private String keyword;
}
