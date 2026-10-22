// 文件路径: src/main/java/com/campus/market/vo/FavoriteStateVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 收藏切换结果。前端据此直接更新按钮状态与收藏数，无需再发一次详情请求。
 */
@Data
@Schema(description = "收藏状态")
public class FavoriteStateVO {

    @Schema(description = "本次操作后是否已收藏：true 已收藏，false 已取消")
    private Boolean favorited;

    @Schema(description = "商品最新收藏数")
    private Integer favoriteCount;

    public static FavoriteStateVO of(boolean favorited, Integer favoriteCount) {
        FavoriteStateVO vo = new FavoriteStateVO();
        vo.setFavorited(favorited);
        vo.setFavoriteCount(favoriteCount);
        return vo;
    }
}
