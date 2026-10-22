// 文件路径: src/main/java/com/campus/market/vo/HomeNoticeVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 首页公告栏数据（模块 12）。
 * <p>
 * 即使公告关闭也返回对象（enabled=0），而不是返回 null：
 * 小程序端拿到 null 时容易写成「公告不存在 → 不渲染」，
 * 而这个字段少了以后判断「是否关闭」就没有依据了。
 * 显式的 enabled 让「没配」和「关了」在数据上是一回事，在前端也能一眼分辨。
 */
@Data
@Schema(description = "首页公告")
public class HomeNoticeVO {

    @Schema(description = "公告文字")
    private String text;

    @Schema(description = "点击跳转地址，可为空")
    private String link;

    @Schema(description = "是否展示:0否 1是")
    private Integer enabled;
}
