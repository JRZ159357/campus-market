// 文件路径: src/main/java/com/campus/market/vo/HomeIndexVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 小程序首页聚合返回（模块 12 改版）。
 * <p>
 * 设计目的没变：首屏只发一个请求，公告、轮播图、金刚区、热门、新品一次取回，
 * 避免弱网下 5 个串行请求造成的白屏叠加。
 * <p>
 * 与模块 3 的版本相比，两个字段替换了语义：
 * <ul>
 *   <li>{@code guessYouLike} → {@code hotProducts}：从「认证学生优先」的固定算法，
 *       变成运营可配（手动选品 / 按热度自动）；</li>
 *   <li>{@code latest} → {@code newProducts}：数量从写死 4 件变成配置项（默认 10）。</li>
 * </ul>
 * 刻意<b>不</b>保留旧字段做兼容：字段名就是契约，
 * 同时返回 hotProducts 和 guessYouLike 会让调用方不知道该信哪个，
 * 也让「首页到底推的是哪批商品」这个问题出现两种答案。
 */
@Data
@Schema(description = "首页聚合数据")
public class HomeIndexVO {

    @Schema(description = "公告（enabled=0 表示运营关闭了公告）")
    private HomeNoticeVO notice;

    @Schema(description = "轮播图（启用且在展示期内，按 sort 升序）")
    private List<BannerVO> banners;

    @Schema(description = "金刚区（is_home_show=1 且启用的分类，按 sort 升序）")
    private List<CategoryVO> categories;

    @Schema(description = "热门推荐（手动选品则保持运营顺序，自动模式按浏览量+收藏量排序）")
    private List<ProductListVO> hotProducts;

    @Schema(description = "新品推荐（已上架，按发布时间倒序，数量取首页配置）")
    private List<ProductListVO> newProducts;

    @Schema(description = "热门搜索词")
    private List<String> hotKeywords;
}
