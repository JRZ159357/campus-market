// 文件路径: src/main/java/com/campus/market/vo/HomeConfigVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首页配置回显（模块 12）。
 * <p>
 * 字段与 {@link com.campus.market.dto.HomeConfigSaveDTO} 一一对应，
 * 前端拿到的永远是一个可以直接双向绑定的对象，
 * 不需要知道这些值在库里是 7 行 key-value。
 */
@Data
@Schema(description = "首页配置")
public class HomeConfigVO {

    @Schema(description = "公告文字")
    private String noticeText;

    @Schema(description = "公告点击跳转地址")
    private String noticeLink;

    @Schema(description = "公告是否展示:0否 1是")
    private Integer noticeEnabled;

    @Schema(description = "热门推荐模式:manual手动选品 auto按热度")
    private String hotMode;

    @Schema(description = "手动模式选中的商品ID，顺序即展示顺序")
    private List<Long> hotProductIds;

    @Schema(description = "自动模式取前N件")
    private Integer hotCount;

    @Schema(description = "新品推荐取最新N件")
    private Integer newCount;

    @Schema(description = "配置最后更新时间，取所有配置项里最新的一个")
    private LocalDateTime updateTime;
}
