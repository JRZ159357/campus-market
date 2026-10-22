// 文件路径: src/main/java/com/campus/market/entity/HomeConfig.java
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首页配置实体，对应表 home_config。
 * <p>
 * 表结构是 key-value，一行一项配置；实体只做透传，
 * 业务语义（哪个 key、什么格式、默认值多少）全部收敛在
 * {@link com.campus.market.common.HomeConfigKey} 与 HomeConfigServiceImpl 里。
 * 这样将来加配置项不需要动实体与表结构。
 */
@Data
@TableName("home_config")
@Schema(description = "首页配置项")
public class HomeConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "配置值：纯文本或 JSON 字符串")
    private String configValue;

    @Schema(description = "配置说明")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
