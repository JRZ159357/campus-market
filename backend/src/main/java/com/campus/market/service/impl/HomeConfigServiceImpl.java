// 文件路径: src/main/java/com/campus/market/service/impl/HomeConfigServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.market.common.BusinessException;
import com.campus.market.common.HomeConfigKey;
import com.campus.market.common.ProductStatus;
import com.campus.market.common.ResultCode;
import com.campus.market.common.annotation.OpLog;
import com.campus.market.common.OpLogModule;
import com.campus.market.dto.HomeCategorySortDTO;
import com.campus.market.dto.HomeConfigSaveDTO;
import com.campus.market.entity.Category;
import com.campus.market.entity.HomeConfig;
import com.campus.market.entity.Product;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.HomeConfigMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.service.HomeCacheService;
import com.campus.market.service.HomeConfigService;
import com.campus.market.service.ProductService;
import com.campus.market.vo.HomeCategorySortVO;
import com.campus.market.vo.HomeConfigVO;
import com.campus.market.vo.ProductListVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页配置业务实现（模块 12）。
 * <p>
 * 设计上「库松口紧」：数据库是 key-value（加配置项不改表），
 * 接口与内部全是结构化对象（前端不拼 key、代码不拼字符串）。
 * 铺平与收拢都发生在这一层。
 * <p>
 * 三处刻意的取舍：
 * <ol>
 *   <li><b>默认值写在代码里</b>，SQL 里的预置只是「初始文案」。
 *       表被清空、脚本没跑、运维误删一行，首页都必须能打开；
 *       首页打不开是最贵的事故，严重程度远高于公告文案不对。</li>
 *   <li><b>读配置时做防御性收敛</b>（数量截断到 1-20、非法模式回落 auto、
 *       坏 JSON 当空数组）。因为这个表是「人可以直接改」的，
 *       而一个人把 hot_count 写成 999 就能让首页去捞全表。</li>
 *   <li><b>手动选品允许「选了但商品已下架」</b>：读取时按已上架过滤，
 *       全部失效则回落自动模式。商品生命周期不受配置控制，
 *       在保存时报错只会让运营反复改配置。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeConfigServiceImpl implements HomeConfigService {

    /** 数据库里存着全部配置项，一次查出来比逐项查 7 次更划算 */
    private static final String HOT_IDS_EMPTY = "[]";

    private final HomeConfigMapper homeConfigMapper;
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final HomeCacheService homeCacheService;
    private final ProductService productService;
    private final ObjectMapper objectMapper;

    // ==================== 配置读写 ====================

    @Override
    public HomeConfigVO get() {
        RawConfig raw = loadRaw();

        HomeConfigVO vo = new HomeConfigVO();
        vo.setNoticeText(trimToEmpty(raw.value(HomeConfigKey.NOTICE_TEXT)));
        vo.setNoticeLink(emptyToNull(raw.value(HomeConfigKey.NOTICE_LINK)));
        vo.setNoticeEnabled(raw.intValue(HomeConfigKey.NOTICE_ENABLED, 1, 0, 1));
        vo.setHotMode(normalizeMode(raw.value(HomeConfigKey.HOT_MODE)));
        vo.setHotProductIds(parseIds(raw.value(HomeConfigKey.HOT_PRODUCT_IDS)));
        vo.setHotCount(raw.intValue(HomeConfigKey.HOT_COUNT,
                HomeConfigKey.RECOMMEND_COUNT_DEFAULT, 1, HomeConfigKey.RECOMMEND_COUNT_MAX));
        vo.setNewCount(raw.intValue(HomeConfigKey.NEW_COUNT,
                HomeConfigKey.RECOMMEND_COUNT_DEFAULT, 1, HomeConfigKey.RECOMMEND_COUNT_MAX));
        vo.setUpdateTime(raw.updateTime());
        return vo;
    }

    @Override
    @OpLog(module = OpLogModule.HOME, action = OpLogModule.ACTION_UPDATE, desc = "保存首页配置")
    @Transactional(rollbackFor = Exception.class)
    public void save(HomeConfigSaveDTO dto) {
        String noticeText = trimToEmpty(dto.getNoticeText());
        if (noticeText.length() > HomeConfigKey.NOTICE_TEXT_MAX) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "公告最多" + HomeConfigKey.NOTICE_TEXT_MAX + "字");
        }

        String noticeLink = trimToEmpty(dto.getNoticeLink());
        if (!noticeLink.isEmpty()
                && !(noticeLink.startsWith("http://") || noticeLink.startsWith("https://"))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "公告链接需以 http:// 或 https:// 开头");
        }

        String hotMode = StringUtils.hasText(dto.getHotMode())
                ? dto.getHotMode().trim().toLowerCase(Locale.ROOT)
                : HomeConfigKey.MODE_AUTO;
        if (!HomeConfigKey.MODE_MANUAL.equals(hotMode) && !HomeConfigKey.MODE_AUTO.equals(hotMode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "热门推荐模式取值为 manual 或 auto");
        }

        List<Long> hotIds = sanitizeIds(dto.getHotProductIds());
        if (HomeConfigKey.MODE_MANUAL.equals(hotMode) && hotIds.isEmpty()) {
            // 手动模式却没选品，首页会开出一个空白区块；
            // 与其静默回落自动模式让运营以为「选了但没生效」，不如直接报错
            throw new BusinessException(ResultCode.PARAM_ERROR, "手动选品模式下至少选择一件商品");
        }
        if (hotIds.size() > HomeConfigKey.HOT_PICK_MAX) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "手动选品最多" + HomeConfigKey.HOT_PICK_MAX + "件");
        }

        int hotCount = clamp(dto.getHotCount(), HomeConfigKey.RECOMMEND_COUNT_DEFAULT,
                1, HomeConfigKey.RECOMMEND_COUNT_MAX);
        int newCount = clamp(dto.getNewCount(), HomeConfigKey.RECOMMEND_COUNT_DEFAULT,
                1, HomeConfigKey.RECOMMEND_COUNT_MAX);
        int noticeEnabled = dto.getNoticeEnabled() == null ? 1 : (dto.getNoticeEnabled() == 1 ? 1 : 0);

        upsert(HomeConfigKey.NOTICE_TEXT, noticeText, "首页公告文字，空则不显示");
        upsert(HomeConfigKey.NOTICE_LINK, noticeLink, "公告点击跳转地址，可空");
        upsert(HomeConfigKey.NOTICE_ENABLED, String.valueOf(noticeEnabled), "公告是否显示:0否 1是");
        upsert(HomeConfigKey.HOT_MODE, hotMode, "热门推荐模式:manual手动选品 auto按热度");
        upsert(HomeConfigKey.HOT_PRODUCT_IDS, writeIds(hotIds),
                "手动模式选中的商品ID（JSON数组，有序，最多20个）");
        upsert(HomeConfigKey.HOT_COUNT, String.valueOf(hotCount), "自动模式取前N件（1-20）");
        upsert(HomeConfigKey.NEW_COUNT, String.valueOf(newCount), "新品推荐取最新N件（1-20）");

        homeCacheService.evictIndex();
        log.info("保存首页配置: noticeEnabled={}, hotMode={}, hotIds={}, hotCount={}, newCount={}",
                noticeEnabled, hotMode, hotIds, hotCount, newCount);
    }

    // ==================== 金刚区 ====================

    @Override
    public List<HomeCategorySortVO> listCategorySort() {
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        Map<Long, Long> countMap = onSaleCountByCategory();
        return categories.stream().map(c -> {
            HomeCategorySortVO vo = new HomeCategorySortVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setIcon(c.getIcon());
            vo.setSort(c.getSort());
            vo.setIsHomeShow(c.getIsHomeShow() == null ? 1 : c.getIsHomeShow());
            vo.setStatus(c.getStatus());
            vo.setProductCount(countMap.getOrDefault(c.getId(), 0L));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @OpLog(module = OpLogModule.HOME, action = OpLogModule.ACTION_SORT, desc = "调整金刚区分类")
    @Transactional(rollbackFor = Exception.class)
    public void saveCategorySort(HomeCategorySortDTO dto) {
        List<HomeCategorySortDTO.Item> items = dto.getItems();
        List<Long> ids = items.stream().map(HomeCategorySortDTO.Item::getId).distinct().toList();

        // 全量覆盖前先确认每个 ID 都还在：前端拖拽期间管理员可能刚好把某个分类删了，
        // 这时静默跳过会让「保存成功」变成一句假话
        long exists = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery().in(Category::getId, ids));
        if (exists != ids.size()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "存在已被删除的分类，请刷新后重试");
        }

        int shown = 0;
        for (HomeCategorySortDTO.Item item : items) {
            Category update = new Category();
            update.setId(item.getId());
            update.setSort(item.getSort());
            update.setIsHomeShow(item.getIsHomeShow());
            categoryMapper.updateById(update);
            if (item.getIsHomeShow() == 1) {
                shown++;
            }
        }
        homeCacheService.evictIndex();
        log.info("保存金刚区配置: 共{}个分类，首页显示{}个", items.size(), shown);
    }

    @Override
    public List<ProductListVO> previewProducts(List<Long> ids) {
        // 与首页走同一个取数方法，保证「后台看到的就是用户看到的」
        return productService.listOnSaleByIds(sanitizeIds(ids));
    }

    // ==================== 内部方法 ====================

    /**
     * 一次性读出全部配置行，并补齐缺失项的默认值。
     * 用 record 而不是返回 Map：调用方还要知道「最后更新时间」，
     * 再查一次 max(update_time) 属于多一次往返。
     */
    private RawConfig loadRaw() {
        List<HomeConfig> rows;
        try {
            rows = homeConfigMapper.selectList(null);
        } catch (Exception e) {
            // 表不存在（新环境还没跑 V12）时首页仍然要能打开
            log.warn("读取首页配置失败，全部使用默认值: {}", e.getMessage());
            return new RawConfig(Collections.emptyMap(), null);
        }

        Map<String, String> map = new HashMap<>();
        LocalDateTime latest = null;
        for (HomeConfig row : rows) {
            if (row.getConfigKey() == null) {
                continue;
            }
            map.put(row.getConfigKey(), row.getConfigValue());
            if (row.getUpdateTime() != null && (latest == null || row.getUpdateTime().isAfter(latest))) {
                latest = row.getUpdateTime();
            }
        }
        return new RawConfig(map, latest);
    }

    private void upsert(String key, String value, String remark) {
        int updated = homeConfigMapper.update(null, Wrappers.<HomeConfig>lambdaUpdate()
                .eq(HomeConfig::getConfigKey, key)
                .set(HomeConfig::getConfigValue, value));
        if (updated > 0) {
            return;
        }
        HomeConfig row = new HomeConfig();
        row.setConfigKey(key);
        row.setConfigValue(value);
        row.setRemark(remark);
        try {
            homeConfigMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 两个管理员同时保存时可能都判定「不存在」而同时插入，
            // 唯一索引会挡住第二个：此时改成更新即可，不必把错误抛给用户
            homeConfigMapper.update(null, Wrappers.<HomeConfig>lambdaUpdate()
                    .eq(HomeConfig::getConfigKey, key)
                    .set(HomeConfig::getConfigValue, value));
        }
    }

    /** 各分类下已上架商品数，一次分组查询代替 N 次 count */
    private Map<Long, Long> onSaleCountByCategory() {
        List<Map<String, Object>> rows = productMapper.selectMaps(Wrappers.<Product>query()
                .select("category_id AS categoryId", "COUNT(*) AS cnt")
                .eq("status", ProductStatus.ON_SALE)
                .groupBy("category_id"));
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object idObj = row.get("categoryId");
            Object cntObj = row.get("cnt");
            if (idObj instanceof Number id && cntObj instanceof Number cnt) {
                map.put(id.longValue(), cnt.longValue());
            }
        }
        return map;
    }

    private List<Long> parseIds(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
            return sanitizeIds(ids);
        } catch (Exception e) {
            // 配置值被人手工改坏时不能连带首页一起挂掉
            log.warn("解析热门商品ID失败，按空处理: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String writeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return HOT_IDS_EMPTY;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return HOT_IDS_EMPTY;
        }
    }

    /** 去重保序 + 过滤非正数。顺序就是首页展示顺序，不能排序 */
    private List<Long> sanitizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                set.add(id);
            }
        }
        return new ArrayList<>(set);
    }

    private String normalizeMode(String value) {
        if (!StringUtils.hasText(value)) {
            return HomeConfigKey.MODE_AUTO;
        }
        String mode = value.trim().toLowerCase(Locale.ROOT);
        return HomeConfigKey.MODE_MANUAL.equals(mode) ? HomeConfigKey.MODE_MANUAL : HomeConfigKey.MODE_AUTO;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static int clamp(Integer value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 原始配置快照：键值表 + 最后更新时间。
     * 把「默认值兜底」放进 {@link #intValue} 等方法里，避免每处调用都写一遍三元表达式。
     */
    private record RawConfig(Map<String, String> values, LocalDateTime updateTime) {

        String value(String key) {
            String value = values.get(key);
            return value != null ? value : HomeConfigKey.defaultValue(key);
        }

        int intValue(String key, int fallback, int min, int max) {
            String raw = value(key);
            try {
                return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
            } catch (Exception e) {
                return fallback;
            }
        }
    }
}
