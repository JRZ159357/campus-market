// 文件路径: src/main/java/com/campus/market/dto/HomeConfigSaveDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 首页配置保存入参（模块 12）。
 * <p>
 * 与表结构刻意不一致：表是 key-value，这里是结构化的字段。
 * 理由见 {@link com.campus.market.common.HomeConfigKey} 的类注释——
 * 让前端拼 key 等于把数据库设计直接暴露成接口契约。
 * <p>
 * 这里只做「形态」校验（长度、范围），跨字段的规则放在 Service：
 * 比如「手动模式下必须至少选一件商品」「公告关闭时链接无意义但不报错」，
 * 这类规则需要看多个字段的组合，用注解表达会变成一堆自定义校验器。
 */
@Data
@Schema(description = "首页配置保存参数")
public class HomeConfigSaveDTO {

    @Size(max = 100, message = "公告最多100字")
    @Schema(description = "公告文字，留空表示不展示")
    private String noticeText;

    @Size(max = 500, message = "公告链接过长")
    @Schema(description = "公告点击跳转地址，可空")
    private String noticeLink;

    @Min(value = 0, message = "公告开关取值为0或1")
    @Max(value = 1, message = "公告开关取值为0或1")
    @Schema(description = "公告是否展示:0否 1是")
    private Integer noticeEnabled;

    @Schema(description = "热门推荐模式:manual手动选品 auto按热度")
    private String hotMode;

    @Size(max = 20, message = "手动选品最多20件")
    @Schema(description = "手动模式选中的商品ID，顺序即展示顺序")
    private List<Long> hotProductIds;

    @Min(value = 1, message = "自动推荐数量为1-20")
    @Max(value = 20, message = "自动推荐数量为1-20")
    @Schema(description = "自动模式取前N件")
    private Integer hotCount;

    @Min(value = 1, message = "新品数量为1-20")
    @Max(value = 20, message = "新品数量为1-20")
    @Schema(description = "新品推荐取最新N件")
    private Integer newCount;
}
