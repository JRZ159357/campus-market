// 文件路径: src/main/java/com/campus/market/service/DashboardService.java
package com.campus.market.service;

import com.campus.market.vo.DashboardDistributionVO;
import com.campus.market.vo.DashboardRankingVO;
import com.campus.market.vo.DashboardRecentVO;
import com.campus.market.vo.DashboardSummaryVO;
import com.campus.market.vo.DashboardTrendVO;

import java.util.List;

/**
 * 仪表盘聚合服务（模块 10）。
 * <p>
 * 全部为只读统计方法，不提供任何写操作。
 * <p>
 * 缓存策略统一由 {@link DashboardCacheService} 承担：
 * 本服务只负责「查缓存 → 未命中则查库 → 回填缓存 → 加工返回」这条主干，
 * 缓存键与 TTL 全部收敛在缓存服务里，避免 TTL 散落在各处改不干净。
 */
public interface DashboardService {

    /**
     * 核心指标卡片数据。
     */
    DashboardSummaryVO summary();

    /**
     * 近 N 日趋势。
     *
     * @param days 统计天数，只接受 7 或 30，非法值按 7 处理
     */
    DashboardTrendVO trend(Integer days);

    /**
     * 分布统计。
     */
    DashboardDistributionVO distribution();

    /**
     * 排行榜。
     */
    DashboardRankingVO ranking();

    /**
     * 最近 20 条操作动态。
     */
    List<DashboardRecentVO> recent();

    /**
     * 清除全部仪表盘缓存（管理端手动刷新用）。
     */
    void refresh();
}
