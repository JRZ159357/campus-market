// 文件路径: src/main/java/com/campus/market/vo/ProductFeedVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 游标分页结果（首页最新发布、分类页瀑布流使用）。
 * <p>
 * 为什么不像列表页那样用 pageNum：瀑布流会滚动几十页，
 * offset 越大 MySQL 需要扫描并丢弃的行越多（深分页问题）。
 * 这里用「上一页最后一条的 id」作游标，查询条件变成 id < cursor，
 * 走 PRIMARY / idx_status_id 索引直接定位，代价与页深无关。
 */
@Data
@Schema(description = "商品游标分页结果")
public class ProductFeedVO {

    @Schema(description = "当前页数据")
    private List<ProductListVO> records;

    @Schema(description = "下一页游标，传回 cursor 参数；null 表示没有更多")
    private Long nextCursor;

    @Schema(description = "是否还有更多")
    private Boolean hasMore;
}
