// 文件路径: src/main/java/com/campus/market/entity/Notice.java
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
 * 系统通知实体，对应表 notice。
 * <p>
 * 采用「一人一行」的扇形写入：即使是发给全体用户的系统公告，也会按用户拆成多行。
 * 好处是已读状态天然按人隔离，不需要额外的「公告-用户」已读关联表；
 * 代价是公告的存储量 = 用户数，在本系统的用户量级下完全可接受。
 */
@Data
@TableName("notice")
@Schema(description = "系统通知")
public class Notice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "接收用户ID")
    private Long userId;

    @Schema(description = "类型:1订单通知 2审核通知 3系统公告 4举报处理")
    private Integer type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "关联业务ID（订单ID / 商品ID / 认证ID）")
    private Long relatedId;

    @Schema(description = "是否已读:0未读 1已读")
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
