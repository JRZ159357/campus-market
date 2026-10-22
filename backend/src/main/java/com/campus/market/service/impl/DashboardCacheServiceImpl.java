// 文件路径: src/main/java/com/campus/market/service/impl/DashboardCacheServiceImpl.java
package com.campus.market.service.impl;

import com.campus.market.common.RedisKey;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.vo.DashboardDistributionVO;
import com.campus.market.vo.DashboardRankingVO;
import com.campus.market.vo.DashboardRecentVO;
import com.campus.market.vo.DashboardSummaryVO;
import com.campus.market.vo.DashboardTrendVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 仪表盘缓存服务实现。
 * <p>
 * 三条贯穿全类的原则：
 * <ol>
 *   <li><b>Redis 异常一律降级查库</b>：所有 redis 调用都包在 try-catch 里，
 *       异常时返回 null（读）或静默跳过（写），业务层照常走查库路径。
 *       缓存是优化，不是功能依赖；</li>
 *   <li><b>失效用「删除」而不是「更新」</b>：删除是幂等的，
 *       而在缓存上做加减在并发下极易算错（两个写操作同时读到旧值再各自 -1）；</li>
 *   <li><b>只缓存原始统计，不缓存加工结果</b>：缓存里放的是可以直接返回给前端的 VO，
 *       省掉一次序列化往返；代价是缓存内容与数据库结构强相关，
 *       因此 VO 字段变更时必须同步 evict——这一点在类注释里明确标注。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardCacheServiceImpl implements DashboardCacheService {

    /** 仪表盘是全平台统计，不区分调用者，key 里不需要用户维度 */
    private static final String KEY_SUFFIX_ALL = "all";

    /** 趋势 key 的天数后缀 */
    private static final String KEY_SUFFIX_TREND = "trend:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    // ==================== 读缓存 ====================

    @Override
    public DashboardSummaryVO getSummary() {
        return read(RedisKey.DASHBOARD_SUMMARY + KEY_SUFFIX_ALL, new TypeReference<DashboardSummaryVO>() {
        });
    }

    @Override
    public void putSummary(DashboardSummaryVO vo) {
        write(RedisKey.DASHBOARD_SUMMARY + KEY_SUFFIX_ALL, vo, SUMMARY_TTL_SECONDS);
    }

    @Override
    public DashboardTrendVO getTrend(Integer days) {
        return read(RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + days, new TypeReference<DashboardTrendVO>() {
        });
    }

    @Override
    public void putTrend(Integer days, DashboardTrendVO vo) {
        write(RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + days, vo, TREND_TTL_SECONDS);
    }

    @Override
    public DashboardDistributionVO getDistribution() {
        return read(RedisKey.DASHBOARD_DISTRIBUTION + KEY_SUFFIX_ALL, new TypeReference<DashboardDistributionVO>() {
        });
    }

    @Override
    public void putDistribution(DashboardDistributionVO vo) {
        write(RedisKey.DASHBOARD_DISTRIBUTION + KEY_SUFFIX_ALL, vo, DISTRIBUTION_TTL_SECONDS);
    }

    @Override
    public DashboardRankingVO getRanking() {
        return read(RedisKey.DASHBOARD_RANKING + KEY_SUFFIX_ALL, new TypeReference<DashboardRankingVO>() {
        });
    }

    @Override
    public void putRanking(DashboardRankingVO vo) {
        write(RedisKey.DASHBOARD_RANKING + KEY_SUFFIX_ALL, vo, RANKING_TTL_SECONDS);
    }

    @Override
    public List<DashboardRecentVO> getRecent() {
        return read(RedisKey.DASHBOARD_RECENT + KEY_SUFFIX_ALL, new TypeReference<List<DashboardRecentVO>>() {
        });
    }

    @Override
    public void putRecent(List<DashboardRecentVO> list) {
        write(RedisKey.DASHBOARD_RECENT + KEY_SUFFIX_ALL, list, RECENT_TTL_SECONDS);
    }

    // ==================== 主动失效 ====================

    @Override
    public void evictOnProductChange() {
        // 商品口径散布在四张图里：指标卡、分布图、热门商品榜、热门分类榜，
        // 趋势图的新增商品数也会变——所以除了排行榜全清，还要清趋势。
        evict(RedisKey.DASHBOARD_SUMMARY + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_DISTRIBUTION + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_RANKING + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + 7,
                RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + 30);
    }

    @Override
    public void evictOnOrderChange() {
        // 订单影响成交额、订单状态分布、卖家榜单与趋势图的订单/成交额两条线。
        evict(RedisKey.DASHBOARD_SUMMARY + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_DISTRIBUTION + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_RANKING + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + 7,
                RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + 30);
    }

    @Override
    public void evictOnReportChange() {
        // 举报只影响待处理数与趋势里的举报数，不影响成交与商品口径。
        evict(RedisKey.DASHBOARD_SUMMARY + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + 7,
                RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + 30);
    }

    @Override
    public void evictOnUserChange() {
        // 用户影响总用户数、角色分布、信誉分分布与趋势的新增用户数。
        evict(RedisKey.DASHBOARD_SUMMARY + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_DISTRIBUTION + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + 7,
                RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + 30);
    }

    @Override
    public void evictOnReviewChange() {
        // 评价影响星级分布与平均信誉分，不影响排行榜与趋势。
        evict(RedisKey.DASHBOARD_SUMMARY + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_DISTRIBUTION + KEY_SUFFIX_ALL);
    }

    @Override
    public void evictAll() {
        evict(RedisKey.DASHBOARD_SUMMARY + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_DISTRIBUTION + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_RANKING + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_RECENT + KEY_SUFFIX_ALL,
                RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + 7,
                RedisKey.DASHBOARD_TREND + KEY_SUFFIX_TREND + 30);
    }

    // ==================== 内部工具 ====================

    /**
     * 读缓存并反序列化。
     * <p>
     * 任何一步失败都返回 null，调用方据此走查库路径。
     * 反序列化失败（缓存里是旧版本 VO 的 JSON）同样降级为未命中：
     * 这比抛异常让整个首页 500 要好得多。
     */
    private <T> T read(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.debug("读取仪表盘缓存失败 key={}：{}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 写缓存。失败只记日志不抛异常：写缓存失败只影响下次是否命中，
     * 不该让当前这次业务请求失败。
     */
    private void write(String key, Object value, long ttlSeconds) {
        if (value == null) {
            return;
        }
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.debug("写入仪表盘缓存失败 key={}：{}", key, e.getMessage());
        }
    }

    /**
     * 删除若干 key。失败只记日志——缓存删不掉最坏结果是数据晚几秒才更新，
     * 不影响正确性。
     * <p>
     * <b>事务感知</b>：如果当前处于事务中，删除动作会被推迟到<b>事务提交之后</b>执行。
     * 原因是事务内先删缓存、事务后提交，中间存在一个窗口：
     * 此刻若有读请求进来，会看到「未提交」的数据并把它回填进缓存，
     * 于是这笔被回滚的变更就会被缓存下来，直到 TTL 过期才消失。
     * 推迟到提交后执行就没有这个窗口：读请求要么在提交前读到旧值并回填旧值
     * （正确，旧值就是提交前的真实状态），要么在提交后读到新值。
     * 非事务场景（定时任务、直接调用）则立即执行。
     */
    private void evict(String... keys) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doDelete(keys);
                }
            });
            return;
        }
        doDelete(keys);
    }

    /** 真正执行删除。抽出来是为了让「立即执行」与「提交后执行」共用同一段逻辑。 */
    private void doDelete(String... keys) {
        try {
            // RedisTemplate.delete 只接受 Collection 或单个 String，
            // 传数组会编译不过——这里统一转成 List
            redis.delete(Arrays.asList(keys));
        } catch (Exception e) {
            log.debug("清除仪表盘缓存失败：{}", e.getMessage());
        }
    }
}
