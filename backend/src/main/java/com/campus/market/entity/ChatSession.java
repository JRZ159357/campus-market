// 文件路径: src/main/java/com/campus/market/entity/ChatSession.java
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
 * 聊天会话实体，对应表 chat_session。
 * <p>
 * 唯一性由数据库的 (buyer_id, seller_id, product_key) 唯一索引保证，其中 product_key 是
 * IFNULL(product_id, 0) 的生成列——因为 MySQL 唯一索引把多个 NULL 视为互不相等，
 * 直接对可空的 product_id 建唯一索引无法约束「通用会话」。
 * <p>
 * buyerUnread / sellerUnread 是冗余计数：会话列表要显示未读数，如果每次都去 chat_message 里
 * COUNT(*) 未读，会话多了就是 N+1 查询；这里在发送消息与标记已读时同步维护，列表查询零额外开销。
 */
@Data
@TableName("chat_session")
@Schema(description = "聊天会话")
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "买家ID")
    private Long buyerId;

    @Schema(description = "卖家ID")
    private Long sellerId;

    @Schema(description = "关联商品ID，空表示通用咨询")
    private Long productId;

    @Schema(description = "最后一条消息摘要")
    private String lastMessage;

    @Schema(description = "最后消息时间")
    private LocalDateTime lastTime;

    @Schema(description = "买家未读数")
    private Integer buyerUnread;

    @Schema(description = "卖家未读数")
    private Integer sellerUnread;

    @Schema(description = "买家是否已从自己列表删除:0否 1是")
    private Integer buyerDeleted;

    @Schema(description = "卖家是否已从自己列表删除:0否 1是")
    private Integer sellerDeleted;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
