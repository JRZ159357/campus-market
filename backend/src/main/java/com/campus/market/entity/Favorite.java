// 文件路径: src/main/java/com/campus/market/entity/Favorite.java
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
 * 收藏记录，对应表 favorite。
 * <p>
 * 取消收藏即物理删除，因此没有 update_time / deleted 字段：
 * (user_id, product_id) 唯一索引同时承担防重复与幂等职责，
 * 若改为逻辑删除，取消后再次收藏会与唯一索引冲突。
 */
@Data
@TableName("favorite")
@Schema(description = "收藏记录")
public class Favorite implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键，自增顺序即收藏顺序")
    private Long id;

    @Schema(description = "收藏用户ID")
    private Long userId;

    @Schema(description = "被收藏商品ID")
    private Long productId;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "收藏时间")
    private LocalDateTime createTime;
}
