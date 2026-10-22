// 文件路径: src/main/java/com/campus/market/entity/SensitiveWord.java
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
 * 敏感词实体，对应表 sensitive_word。
 */
@Data
@TableName("sensitive_word")
@Schema(description = "敏感词")
public class SensitiveWord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "敏感词")
    private String word;

    @Schema(description = "类型:1违禁品 2辱骂 3广告 4其他")
    private Integer type;

    @Schema(description = "等级:1警告 2拦截")
    private Integer level;

    @Schema(description = "状态:0禁用 1启用")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
