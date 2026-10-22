// 文件路径: src/main/java/com/campus/market/service/impl/HomeServiceImpl.java
package com.campus.market.service.impl;

import com.campus.market.common.HomeConfigKey;
import com.campus.market.service.BannerService;
import com.campus.market.service.CategoryService;
import com.campus.market.service.HomeCacheService;
import com.campus.market.service.HomeConfigService;
import com.campus.market.service.HomeService;
import com.campus.market.service.ProductService;
import com.campus.market.vo.HomeConfigVO;
import com.campus.market.vo.HomeIndexVO;
import com.campus.market.vo.HomeNoticeVO;
import com.campus.market.vo.ProductListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 首页聚合实现（模块 12 改版）。
 * <p>
 * 首页仍然不直接查表，全部复用各模块已有的 Service：
 * 轮播图走 banner 的时间窗口规则，分类走「只取启用 + 金刚区开关」规则，
 * 商品走「只取已上架」规则，避免首页另写一套状态判断导致规则漂移。
 * <p>
 * 新增的「可配置」只体现在<b>取哪些数据</b>上（选品、数量、公告），
 * 不体现在<b>什么数据能上首页</b>上——后者始终是「已上架」，
 * 这条底线不能交给配置：运营可以决定推哪件商品，但不能决定
 * 「一件待审核的商品也能出现在首页」。
 * <p>
 * 缓存：整个返回结果缓存 5 分钟（见 {@link HomeCacheService}），
 * 命中时完全不碰数据库。运营改配置/轮播图/金刚区是主动失效的，不受 TTL 影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    /**
     * 热门搜索词。当前为固定词表：平台冷启动阶段没有足够搜索日志，
     * 由运营按校园场景预置；后续「AI 智能推荐」模块可替换为按搜索次数统计。
     */
    private static final List<String> HOT_KEYWORDS = List.of(
            "自行车", "教材", "考研资料", "耳机", "显示器",
            "宿舍神器", "球鞋", "吉他", "吹风机", "行李箱");

    private final BannerService bannerService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final HomeConfigService homeConfigService;
    private final HomeCacheService homeCacheService;

    @Override
    public HomeIndexVO index() {
        HomeIndexVO cached = homeCacheService.getIndex();
        if (cached != null) {
            return cached;
        }

        long start = System.currentTimeMillis();
        HomeConfigVO config = homeConfigService.get();

        HomeIndexVO vo = new HomeIndexVO();
        vo.setNotice(buildNotice(config));
        vo.setBanners(bannerService.listActive());
        vo.setCategories(categoryService.listHomeCategories());
        vo.setHotProducts(buildHotProducts(config));
        vo.setNewProducts(productService.latest(config.getNewCount()));
        vo.setHotKeywords(HOT_KEYWORDS);

        homeCacheService.putIndex(vo);
        log.debug("首页聚合耗时 {} ms", System.currentTimeMillis() - start);
        return vo;
    }

    @Override
    public List<String> hotKeywords() {
        return HOT_KEYWORDS;
    }

    // ==================== 内部方法 ====================

    /**
     * 公告：开关打开且文字非空才算「展示」。
     * <p>
     * 两个条件缺一不可——运营清空文案但忘了关开关是很常见的操作，
     * 这时如果只看开关，首页会出现一条空白公告栏。
     */
    private HomeNoticeVO buildNotice(HomeConfigVO config) {
        boolean enabled = Integer.valueOf(1).equals(config.getNoticeEnabled())
                && StringUtils.hasText(config.getNoticeText());

        HomeNoticeVO notice = new HomeNoticeVO();
        notice.setEnabled(enabled ? 1 : 0);
        notice.setText(config.getNoticeText());
        notice.setLink(StringUtils.hasText(config.getNoticeLink()) ? config.getNoticeLink() : null);
        return notice;
    }

    /**
     * 热门推荐：手动选品优先，选品全部失效时回落自动模式。
     * <p>
     * 回落的判定是「结果为空」而不是「配置为空」：
     * 运营选了 20 件商品，几个月后它们陆续卖掉下架，
     * 此时首页开一个空白区块比「推几件当前最热的」更糟——
     * 前者是明确的 bug，后者只是推荐策略退化。
     */
    private List<ProductListVO> buildHotProducts(HomeConfigVO config) {
        if (HomeConfigKey.MODE_MANUAL.equals(config.getHotMode())) {
            List<ProductListVO> picked = productService.listOnSaleByIds(config.getHotProductIds());
            if (!picked.isEmpty()) {
                return picked;
            }
            log.warn("首页热门推荐为手动模式，但所选商品均已下架或删除，回落为按热度自动取 {} 件",
                    config.getHotCount());
        }
        return productService.hotTop(config.getHotCount());
    }
}
