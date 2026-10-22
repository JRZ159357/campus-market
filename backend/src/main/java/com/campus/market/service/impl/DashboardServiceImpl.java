// 文件路径: src/main/java/com/campus/market/service/impl/DashboardServiceImpl.java
package com.campus.market.service.impl;

import com.campus.market.common.CreditLevel;
import com.campus.market.common.OpLogModule;
import com.campus.market.common.OrderStatus;
import com.campus.market.common.ProductStatus;
import com.campus.market.common.RoleConst;
import com.campus.market.mapper.DashboardMapper;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.service.DashboardService;
import com.campus.market.vo.DashboardDistributionVO;
import com.campus.market.vo.DashboardRankingVO;
import com.campus.market.vo.DashboardRecentVO;
import com.campus.market.vo.DashboardSummaryVO;
import com.campus.market.vo.DashboardTrendVO;
import com.campus.market.vo.NameValueVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘聚合实现。
 * <p>
 * 这个类的核心价值不是「把数字查出来」，而是<b>把口径固定下来</b>：
 * 「总用户数」包不包含已注销账号、「成交额」算不算退款中的订单、
 * 「热门商品」包不包含待审核商品——这些口径如果每个页面各写一遍，
 * 迟早会出现「管理端首页说 100 个用户，列表页数出 98 个」的矛盾。
 * 因此所有口径都集中在这一个类里，VO 字段注释里逐条写明。
 * <p>
 * 缓存采用「查缓存 → 未命中查库 → 回填」的经典三段式，
 * 且<b>回填的是最终 VO</b>，因此第二次请求完全不碰数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 趋势图支持的两种窗口 */
    private static final int TREND_DAYS_7 = 7;
    private static final int TREND_DAYS_30 = 30;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 信誉分分布的 5 个区间，与 CreditLevel 的档位一致 */
    private static final String[] CREDIT_BUCKETS = {"0-59", "60-79", "80-99", "100-119", "120-150"};

    private final DashboardMapper dashboardMapper;
    private final DashboardCacheService cacheService;

    // ==================== 核心指标 ====================

    @Override
    public DashboardSummaryVO summary() {
        DashboardSummaryVO cached = cacheService.getSummary();
        if (cached != null) {
            return cached;
        }
        long start = System.currentTimeMillis();
        Map<String, Object> row = dashboardMapper.selectSummary();

        DashboardSummaryVO vo = new DashboardSummaryVO();
        vo.setTotalUsers(longOf(row, "totalUsers"));
        vo.setNewUsersToday(longOf(row, "newUsersToday"));
        vo.setNewUsersWeek(longOf(row, "newUsersWeek"));
        vo.setUserGrowth(growth(longOf(row, "newUsersWeek"), longOf(row, "newUsersPrevWeek")));

        vo.setTotalProducts(longOf(row, "totalProducts"));
        vo.setOnSaleProducts(longOf(row, "onSaleProducts"));
        vo.setPendingAuditProducts(longOf(row, "pendingAuditProducts"));
        vo.setProductGrowth(growth(longOf(row, "newProductsWeek"), longOf(row, "newProductsPrevWeek")));

        vo.setTotalOrders(longOf(row, "totalOrders"));
        vo.setCompletedOrders(longOf(row, "completedOrders"));
        vo.setGmvTotal(decimalOf(row, "gmvTotal"));
        vo.setGmvToday(decimalOf(row, "gmvToday"));
        vo.setOrderGrowth(growth(longOf(row, "newOrdersWeek"), longOf(row, "newOrdersPrevWeek")));
        vo.setGmvGrowth(growth(decimalOf(row, "gmvWeek"), decimalOf(row, "gmvPrevWeek")));

        vo.setPendingReports(longOf(row, "pendingReports"));
        vo.setReportGrowth(growth(longOf(row, "newReportsWeek"), longOf(row, "newReportsPrevWeek")));
        vo.setPendingRefunds(longOf(row, "pendingRefunds"));

        vo.setActiveSessions(longOf(row, "activeSessions"));

        double avgScore = doubleOf(row, "avgCreditScore");
        vo.setAvgCreditScore(avgScore);
        CreditLevel level = CreditLevel.of((int) Math.round(avgScore));
        vo.setAvgCreditLevel(level.getLabel());
        vo.setAvgCreditColor(level.getColor());

        vo.setStatTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        cacheService.putSummary(vo);
        log.debug("仪表盘核心指标聚合耗时 {} ms", System.currentTimeMillis() - start);
        return vo;
    }

    // ==================== 趋势 ====================

    @Override
    public DashboardTrendVO trend(Integer days) {
        int window = normalizeDays(days);
        DashboardTrendVO cached = cacheService.getTrend(window);
        if (cached != null) {
            return cached;
        }
        long start = System.currentTimeMillis();

        // 起始时间取「今天往前 window-1 天的 00:00:00」，
        // 这样窗口里正好是 window 个自然日，含今天
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(window - 1L);
        LocalDateTime since = firstDay.atStartOfDay();

        // 数据库只返回「有数据的那几天」，这里用 Map 把稀疏结果摊平成
        // 「日期 -> 五个指标」，缺指标先按 0 占位
        Map<String, Map<String, Object>> grouped = new HashMap<>();
        for (Map<String, Object> item : dashboardMapper.selectTrend(since)) {
            String metric = str(item.get("metric"));
            String dt = str(item.get("dt"));
            if (metric == null || dt == null) {
                continue;
            }
            grouped.computeIfAbsent(dt, k -> new HashMap<>()).put(metric, item.get("val"));
        }

        // 应用层补全无数据的日期：数据库不做「没有行就没有这天」的处理，
        // 否则前端要自己补零才能画出连续折线，逻辑就散到前端去了
        List<DashboardTrendVO.Point> points = new ArrayList<>(window);
        for (int i = 0; i < window; i++) {
            LocalDate day = firstDay.plusDays(i);
            String dt = day.format(DATE_FMT);
            Map<String, Object> metrics = grouped.get(dt);

            DashboardTrendVO.Point point = new DashboardTrendVO.Point();
            point.setDate(dt);
            point.setNewUsers(longOf(metrics, "newUsers"));
            point.setNewProducts(longOf(metrics, "newProducts"));
            point.setOrders(longOf(metrics, "orders"));
            point.setGmv(decimalOf(metrics, "gmv"));
            point.setReports(longOf(metrics, "reports"));
            points.add(point);
        }

        DashboardTrendVO vo = new DashboardTrendVO();
        vo.setDays(window);
        vo.setPoints(points);
        cacheService.putTrend(window, vo);
        log.debug("仪表盘趋势聚合({}天)耗时 {} ms", window, System.currentTimeMillis() - start);
        return vo;
    }

    // ==================== 分布 ====================

    @Override
    public DashboardDistributionVO distribution() {
        DashboardDistributionVO cached = cacheService.getDistribution();
        if (cached != null) {
            return cached;
        }
        long start = System.currentTimeMillis();

        DashboardDistributionVO vo = new DashboardDistributionVO();
        vo.setUserRoleDist(userRoleDist());
        vo.setProductCategoryDist(productCategoryDist());
        vo.setProductStatusDist(productStatusDist());
        vo.setOrderStatusDist(orderStatusDist());
        vo.setCreditScoreDist(creditScoreDist());
        vo.setReviewStarDist(reviewStarDist());

        cacheService.putDistribution(vo);
        log.debug("仪表盘分布聚合耗时 {} ms", System.currentTimeMillis() - start);
        return vo;
    }

    /** 用户角色分布。角色中文名统一用 RoleConst 映射，不在 SQL 里写死。 */
    private List<NameValueVO> userRoleDist() {
        List<NameValueVO> list = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.selectUserRoleDist()) {
            String role = str(row.get("name"));
            list.add(NameValueVO.of(role, RoleConst.label(role), longOf(row, "val")));
        }
        // 固定把四个角色都列出来（哪怕为 0）：
        // 前端画饼图时图例是固定的，少一个角色会让图例在不同账号下不一致
        for (String role : new String[]{RoleConst.USER, RoleConst.STUDENT,
                RoleConst.CUSTOMER_SERVICE, RoleConst.ADMIN}) {
            boolean exists = list.stream().anyMatch(v -> role.equals(v.getName()));
            if (!exists) {
                list.add(NameValueVO.of(role, RoleConst.label(role), 0L));
            }
        }
        return list;
    }

    /** 商品分类分布。分类名直接取自 category 表。 */
    private List<NameValueVO> productCategoryDist() {
        List<NameValueVO> list = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.selectProductCategoryDist()) {
            String name = str(row.get("name"));
            list.add(NameValueVO.of(name, name, longOf(row, "val")));
        }
        return list;
    }

    /** 商品状态分布。状态码用 ProductStatus 翻译。 */
    private List<NameValueVO> productStatusDist() {
        List<NameValueVO> list = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.selectProductStatusDist()) {
            Integer status = intOf(row, "name");
            list.add(NameValueVO.of(String.valueOf(status), ProductStatus.label(status), longOf(row, "val")));
        }
        return list;
    }

    /** 订单状态分布。状态码用 OrderStatus 翻译。 */
    private List<NameValueVO> orderStatusDist() {
        List<NameValueVO> list = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.selectOrderStatusDist()) {
            Integer status = intOf(row, "name");
            list.add(NameValueVO.of(String.valueOf(status), OrderStatus.label(status), longOf(row, "val")));
        }
        return list;
    }

    /**
     * 信誉分区间分布。
     * <p>
     * 五个区间即使为 0 也全部返回：柱状图的 X 轴要固定，
     * 否则「这段时间没人处在差评区间」时整根柱子会消失，
     * 看图的人会以为这个区间不存在。
     */
    private List<NameValueVO> creditScoreDist() {
        Map<String, Object> row = dashboardMapper.selectCreditScoreDist();
        List<NameValueVO> list = new ArrayList<>(CREDIT_BUCKETS.length);
        for (int i = 0; i < CREDIT_BUCKETS.length; i++) {
            list.add(NameValueVO.of(CREDIT_BUCKETS[i], CREDIT_BUCKETS[i] + " 分",
                    longOf(row, "bucket" + i)));
        }
        return list;
    }

    /** 评价星级分布。1-5 星固定列出，缺失星级补 0。 */
    private List<NameValueVO> reviewStarDist() {
        Map<Integer, Long> counter = new LinkedHashMap<>();
        for (Map<String, Object> row : dashboardMapper.selectReviewStarDist()) {
            counter.put(intOf(row, "name"), longOf(row, "val"));
        }
        List<NameValueVO> list = new ArrayList<>(5);
        for (int star = 1; star <= 5; star++) {
            list.add(NameValueVO.of(String.valueOf(star), star + " 星", counter.getOrDefault(star, 0L)));
        }
        return list;
    }

    // ==================== 排行榜 ====================

    @Override
    public DashboardRankingVO ranking() {
        DashboardRankingVO cached = cacheService.getRanking();
        if (cached != null) {
            return cached;
        }
        long start = System.currentTimeMillis();

        DashboardRankingVO vo = new DashboardRankingVO();
        vo.setTopSellers(topSellers());
        vo.setTopProducts(topProducts());
        vo.setTopCategories(topCategories());

        cacheService.putRanking(vo);
        log.debug("仪表盘排行榜聚合耗时 {} ms", System.currentTimeMillis() - start);
        return vo;
    }

    private List<DashboardRankingVO.SellerItem> topSellers() {
        List<DashboardRankingVO.SellerItem> list = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.selectTopSellers()) {
            DashboardRankingVO.SellerItem item = new DashboardRankingVO.SellerItem();
            item.setSellerId(longOf(row, "sellerId"));
            item.setNickname(str(row.get("nickname")));
            item.setAvatar(str(row.get("avatar")));
            item.setProductCount(longOf(row, "productCount"));
            item.setSoldCount(longOf(row, "soldCount"));
            item.setAmount(decimalOf(row, "amount"));
            Integer score = intOf(row, "creditScore");
            item.setCreditScore(score);
            CreditLevel level = CreditLevel.of(score);
            item.setCreditLevel(level.getLabel());
            item.setCreditColor(level.getColor());
            list.add(item);
        }
        return list;
    }

    private List<DashboardRankingVO.ProductItem> topProducts() {
        List<DashboardRankingVO.ProductItem> list = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.selectTopProducts()) {
            DashboardRankingVO.ProductItem item = new DashboardRankingVO.ProductItem();
            item.setProductId(longOf(row, "productId"));
            item.setTitle(str(row.get("title")));
            item.setPrice(decimalOf(row, "price"));
            item.setViewCount(longOf(row, "viewCount"));
            item.setFavoriteCount(longOf(row, "favoriteCount"));
            item.setCover(str(row.get("cover")));
            list.add(item);
        }
        return list;
    }

    private List<DashboardRankingVO.CategoryItem> topCategories() {
        List<DashboardRankingVO.CategoryItem> list = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.selectTopCategories()) {
            DashboardRankingVO.CategoryItem item = new DashboardRankingVO.CategoryItem();
            item.setCategoryId(longOf(row, "categoryId"));
            item.setName(str(row.get("name")));
            item.setProductCount(longOf(row, "productCount"));
            item.setViewCount(longOf(row, "viewCount"));
            list.add(item);
        }
        return list;
    }

    // ==================== 最近动态 ====================

    @Override
    public List<DashboardRecentVO> recent() {
        List<DashboardRecentVO> cached = cacheService.getRecent();
        if (cached != null) {
            return cached;
        }
        List<DashboardRecentVO> list = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.selectRecentLogs()) {
            DashboardRecentVO item = new DashboardRecentVO();
            item.setId(longOf(row, "id"));
            item.setOperatorId(longOf(row, "operatorId"));
            item.setOperatorName(str(row.get("operatorName")));
            item.setOperatorAvatar(str(row.get("operatorAvatar")));
            item.setOperatorRole(str(row.get("operatorRole")));
            item.setModule(str(row.get("module")));
            item.setModuleLabel(OpLogModule.label(item.getModule()));
            item.setAction(str(row.get("action")));
            item.setActionLabel(OpLogModule.actionLabel(item.getAction()));
            item.setTargetId(longOf(row, "targetId"));
            item.setDetail(str(row.get("detail")));
            Object time = row.get("createTime");
            if (time instanceof LocalDateTime dt) {
                item.setCreateTime(dt);
            }
            list.add(item);
        }
        cacheService.putRecent(list);
        return list;
    }

    // ==================== 刷新 ====================

    @Override
    public void refresh() {
        cacheService.evictAll();
    }

    // ==================== 内部工具 ====================

    /**
     * 归一化趋势窗口：只接受 7 与 30，其余一律按 7 处理。
     * <p>
     * 不做校验直接传给 SQL 的话，{@code days=99999} 会让数据库扫一年的数据，
     * 一个 GET 请求就能把库拖垮。限制成两个固定窗口既是产品需求，
     * 也是对数据库的保护。
     */
    private int normalizeDays(Integer days) {
        if (days == null) {
            return TREND_DAYS_7;
        }
        return days == TREND_DAYS_30 ? TREND_DAYS_30 : TREND_DAYS_7;
    }

    /**
     * 环比增长率。
     * <p>
     * 前期为 0 时返回 null 而不是 0 或无穷大：
     * 「上期为 0，本期 5」在业务上无法定义成百分比，
     * 前端拿到 null 显示「—」比显示「+∞%」诚实。
     */
    private Double growth(Number current, Number previous) {
        if (current == null || previous == null) {
            return null;
        }
        double prev = previous.doubleValue();
        if (prev == 0d) {
            return null;
        }
        double curr = current.doubleValue();
        return BigDecimal.valueOf((curr - prev) / prev * 100d)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Long longOf(Map<String, Object> row, String key) {
        if (row == null) {
            return 0L;
        }
        Object value = row.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Integer intOf(Map<String, Object> row, String key) {
        Long value = longOf(row, key);
        return value == null ? null : value.intValue();
    }

    private Double doubleOf(Map<String, Object> row, String key) {
        if (row == null) {
            return 0d;
        }
        Object value = row.get(key);
        if (value == null) {
            return 0d;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private BigDecimal decimalOf(Map<String, Object> row, String key) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        Object value = row.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal d) {
            return d;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
