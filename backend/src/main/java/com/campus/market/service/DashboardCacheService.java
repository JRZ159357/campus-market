// 文件路径: src/main/java/com/campus/market/service/DashboardCacheService.java
package com.campus.market.service;

import com.campus.market.vo.DashboardDistributionVO;
import com.campus.market.vo.DashboardRankingVO;
import com.campus.market.vo.DashboardRecentVO;
import com.campus.market.vo.DashboardSummaryVO;
import com.campus.market.vo.DashboardTrendVO;

import java.util.List;

/**
 * 仪表盘缓存服务（模块 10）。
 * <p>
 * 把「缓存读写 + 写操作主动失效」从仪表盘业务里抽出来单独成服务，
 * 目的是<b>打破依赖环</b>：
 * <blockquote>
 * 商品审核 / 订单完成 / 举报处理要清仪表盘缓存，
 * 而仪表盘本身要读这些表的数据。
 * 如果把失效逻辑写在仪表盘 Service 里，业务 Service 就会依赖仪表盘 Service，
 * 仪表盘 Service 又要依赖业务 Service——Spring 直接启动失败（循环依赖）。
 * </blockquote>
 * 本服务只依赖 Redis 与 Jackson，不依赖任何业务 Service，
 * 因此业务侧注入它是单向依赖，环不存在。
 * <p>
 * <b>分级 TTL</b>（论文「多级缓存策略」一节的核心）：
 * <ul>
 *   <li>核心指标 5 分钟——数字要相对新，又不必精确到秒；</li>
 *   <li>分布统计 5 分钟——与指标卡同口径，同步失效避免两张图打架；</li>
 *   <li>趋势 10 分钟——按天聚合的数据，实时性天然要求低；</li>
 *   <li>排行榜 30 分钟——变化最慢，压最长的 TTL 换取最低的数据库压力；</li>
 *   <li>最近动态 30 秒——操作日志几乎每分钟都在变，但只是一屏只读列表，
 *       短暂延迟完全可接受。</li>
 * </ul>
 * <p>
 * <b>降级策略</b>：Redis 异常时全部直接查库，接口照常返回，只是少了缓存收益。
 * 缓存是优化，不是功能依赖——因为 Redis 挂了导致整个管理端首页打不开，
 * 才是真的事故。
 */
public interface DashboardCacheService {

    /** 核心指标缓存 TTL：5 分钟 */
    long SUMMARY_TTL_SECONDS = 300L;

    /** 分布统计缓存 TTL：5 分钟 */
    long DISTRIBUTION_TTL_SECONDS = 300L;

    /** 趋势缓存 TTL：10 分钟 */
    long TREND_TTL_SECONDS = 600L;

    /** 排行榜缓存 TTL：30 分钟 */
    long RANKING_TTL_SECONDS = 1800L;

    /** 最近动态缓存 TTL：30 秒 */
    long RECENT_TTL_SECONDS = 30L;

    // ==================== 读缓存 ====================

    DashboardSummaryVO getSummary();

    void putSummary(DashboardSummaryVO vo);

    DashboardTrendVO getTrend(Integer days);

    void putTrend(Integer days, DashboardTrendVO vo);

    DashboardDistributionVO getDistribution();

    void putDistribution(DashboardDistributionVO vo);

    DashboardRankingVO getRanking();

    void putRanking(DashboardRankingVO vo);

    List<DashboardRecentVO> getRecent();

    void putRecent(List<DashboardRecentVO> list);

    // ==================== 主动失效 ====================

    /**
     * 商品发生变化（发布 / 审核通过 / 下架 / 强制下架）后清除缓存。
     * <p>
     * 影响面最广：在售数、待审核数、商品状态分布、分类分布、
     * 热门商品榜、热门分类榜、卖家的商品数全部会变。
     */
    void evictOnProductChange();

    /**
     * 订单发生变化（下单 / 完成 / 取消 / 退款）后清除缓存。
     * <p>
     * 影响订单数、成交额、订单状态分布、趋势图的订单与成交额两条线，
     * 以及卖家的已售数与成交额。
     */
    void evictOnOrderChange();

    /**
     * 举报发生变化（提交 / 接单 / 处理）后清除缓存。
     * <p>
     * 只影响待处理举报数与趋势里的举报数，不影响成交与商品口径，
     * 因此不必清排行榜。
     */
    void evictOnReportChange();

    /**
     * 用户发生变化（注册 / 封禁 / 角色变更）后清除缓存。
     */
    void evictOnUserChange();

    /**
     * 评价发生变化（新增 / 删除）后清除缓存。
     * <p>
     * 影响评价星级分布与平均信誉分，不影响排行榜。
     */
    void evictOnReviewChange();

    /**
     * 清空全部仪表盘缓存。管理端「刷新」按钮用。
     */
    void evictAll();
}
