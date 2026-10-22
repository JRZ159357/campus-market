// 文件路径: src/main/java/com/campus/market/entity/Follow.java
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
 * 关注关系，对应表 follow。
 * <p>
 * follower_id 为关注者，following_id 为被关注者（卖家）。
 * 取消关注即物理删除；(follower_id, following_id) 唯一索引防止重复关注。
 */
@Data
@TableName("follow")
@Schema(description = "关注关系")
public class Follow implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键，自增顺序即关注顺序")
    private Long id;

    @Schema(description = "关注者ID")
    private Long followerId;

    @Schema(description = "被关注者ID（卖家）")
    private Long followingId;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "关注时间")
    private LocalDateTime createTime;
}
