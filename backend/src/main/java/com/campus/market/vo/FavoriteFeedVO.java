// 文件路径: src/main/java/com/campus/market/vo/FavoriteFeedVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 收藏列表的游标分页结果。
 * <p>
 * 与首页瀑布流（ProductFeedVO）结构一致：多取一条判断 hasMore，
 * nextCursor 传下一页即可，无需总条数，避免深分页 COUNT 与 OFFSET。
 */
@Data
@Schema(description = "收藏列表游标分页结果")
public class FavoriteFeedVO {

    @Schema(description = "当前页数据")
    private List<FavoriteProductVO> records;

    @Schema(description = "下一页游标：本页最后一条收藏记录ID，无下一页时为 null")
    private Long nextCursor;

    @Schema(description = "是否还有下一页")
    private Boolean hasMore;
}
