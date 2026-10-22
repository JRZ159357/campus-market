// 文件路径: src/main/java/com/campus/market/entity/Address.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收货 / 见面地址实体，对应表 address。
 * 校园场景下"地址"允许填宿舍楼、教学楼或约定见面点。
 */
@Data
@TableName("address")
@Schema(description = "收货地址")
public class Address implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所属用户ID")
    private Long userId;

    @Schema(description = "收货人")
    private String receiverName;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "详细地址")
    private String address;

    @Schema(description = "是否默认:0否 1是")
    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableLogic
    @Schema(description = "逻辑删除:0未删 1已删")
    private Integer deleted;
}
