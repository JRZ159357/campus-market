// 文件路径: src/main/java/com/campus/market/entity/ChatMessage.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息实体，对应表 chat_message。
 * <p>
 * content 是「多态」的：文本消息存纯文本，图片消息存 /uploads/xxx.jpg，商品/订单卡片存序列化后的 JSON。
 * 因此该列用 TEXT 而不做拆表——消息类型少、结构稳定，拆成多表反而让时间线查询变复杂。
 * <p>
 * 消息不做逻辑删除：聊天记录是纠纷处理与客服监控的依据，任一方删除会话只是从自己列表隐藏，
 * 不会删掉对方看到的消息。
 */
@Data
@TableName("chat_message")
@Schema(description = "聊天消息")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所属会话ID")
    private Long sessionId;

    @Schema(description = "发送者ID")
    private Long senderId;

    @Schema(description = "接收者ID")
    private Long receiverId;

    @Schema(description = "消息类型:1文本 2图片 3系统消息 4商品卡片 5订单卡片")
    private Integer msgType;

    @Schema(description = "文本内容 / 图片URL / 卡片JSON")
    private String content;

    @Schema(description = "是否已读:0未读 1已读")
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "发送时间")
    private LocalDateTime createTime;
}
