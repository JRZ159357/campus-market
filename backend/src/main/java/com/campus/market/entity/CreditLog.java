// 文件路径: src/main/java/com/campus/market/entity/CreditLog.java
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
 * 信誉分变动流水，对应表 credit_log。
 * <p>
 * 只追加、不修改、不逻辑删除：流水是审计凭证，一旦写入就代表
 * 「某个时刻分数从 before 变成 after，原因是 reason」，事后改它等于篡改账本。
 */
@Data
@TableName("credit_log")
@Schema(description = "信誉分变动流水")
public class CreditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "被调整的用户ID")
    private Long userId;

    @Schema(description = "本次变动值，正加负减（已按实际生效值计算）")
    private Integer changeScore;

    @Schema(description = "变动前分数")
    private Integer beforeScore;

    @Schema(description = "变动后分数")
    private Integer afterScore;

    @Schema(description = "类型:1收到评价 2举报扣分 3管理员调整 4评价被删除")
    private Integer type;

    @Schema(description = "变动原因")
    private String reason;

    @Schema(description = "关联业务ID（评价ID / 举报ID）")
    private Long relatedId;

    @Schema(description = "操作人ID，系统自动为NULL")
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "发生时间")
    private LocalDateTime createTime;
}
