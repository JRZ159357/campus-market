// 文件路径: src/main/java/com/campus/market/service/impl/CreditServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BusinessException;
import com.campus.market.common.CreditLevel;
import com.campus.market.common.CreditLogType;
import com.campus.market.common.CreditRule;
import com.campus.market.dto.CreditAdjustDTO;
import com.campus.market.dto.ReviewQueryDTO;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.Review;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.CreditLogMapper;
import com.campus.market.mapper.ReviewMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.service.CreditService;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.service.UserService;
import com.campus.market.service.support.ReviewAssembler;
import com.campus.market.vo.CreditLogVO;
import com.campus.market.vo.CreditUserVO;
import com.campus.market.vo.CreditVO;
import com.campus.market.vo.ReviewStatVO;
import com.campus.market.vo.ReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 信誉分服务实现。
 * <p>
 * 核心是 {@link #addScore}：它是全系统唯一修改 credit_score 的地方。
 * 之所以坚持「唯一入口」，是因为信誉分有三条变动来源（评价、举报、管理员调整），
 * 如果三条链路各自 UPDATE，就会出现有的地方忘了截断 0-150、
 * 有的地方忘了写流水，最后数据对不上还查不出是谁改的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    /** 详情页展示的最近评价条数 */
    private static final int RECENT_REVIEW_SIZE = 5;

    /** 详情页展示的最近变动条数 */
    private static final int RECENT_LOG_SIZE = 5;

    private final SysUserMapper sysUserMapper;

    private final ReviewMapper reviewMapper;

    private final CreditLogMapper creditLogMapper;

    private final ReviewAssembler reviewAssembler;

    private final UserService userService;

    private final DashboardCacheService dashboardCacheService;

    @Override
    public CreditVO detail(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        int score = user.getCreditScore() == null ? CreditRule.INITIAL_SCORE : user.getCreditScore();
        CreditLevel level = CreditLevel.of(score);

        CreditVO vo = new CreditVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setScore(score);
        vo.setLevel(level.name());
        vo.setLevelLabel(level.getLabel());
        vo.setLevelColor(level.getColor());
        vo.setLevelIcon(level.getIcon());
        vo.setProgress(CreditLevel.progressOf(score));
        vo.setToMaxScore(CreditRule.MAX_SCORE - score);

        // 下一档：找到 min 大于当前分的最近一档
        CreditLevel next = null;
        for (CreditLevel item : CreditLevel.values()) {
            if (item.getMin() > score && (next == null || item.getMin() < next.getMin())) {
                next = item;
            }
        }
        if (next != null) {
            vo.setNextLevelLabel(next.getLabel());
            vo.setNextLevelScore(next.getMin());
        }
        vo.setLevels(buildLevels());
        vo.setStat(statOf(userId));
        vo.setRecentReviews(recentReviews(userId));
        vo.setRecentLogs(toLogVOList(creditLogMapper.selectRecent(userId, RECENT_LOG_SIZE)));
        return vo;
    }

    @Override
    public IPage<CreditUserVO> pageUsers(String keyword, Long pageNum, Long pageSize) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getNickname, SysUser::getAvatar,
                        SysUser::getUsername, SysUser::getRole, SysUser::getStatus, SysUser::getCreditScore)
                .orderByDesc(SysUser::getId);
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(SysUser::getNickname, trimmed)
                    .or().like(SysUser::getUsername, trimmed)
                    .or().like(SysUser::getPhone, trimmed)
                    .or().eq(SysUser::getId, trimmed.matches("\\d+") ? Long.valueOf(trimmed) : -1L));
        }
        Page<SysUser> page = new Page<>(current, size);
        IPage<SysUser> result = sysUserMapper.selectPage(page, wrapper);

        Page<CreditUserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<CreditUserVO> records = new ArrayList<>(result.getRecords().size());
        for (SysUser user : result.getRecords()) {
            CreditUserVO vo = new CreditUserVO();
            vo.setUserId(user.getId());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setUsername(user.getUsername());
            vo.setRole(user.getRole());
            vo.setStatus(user.getStatus());
            int score = user.getCreditScore() == null ? CreditRule.INITIAL_SCORE : user.getCreditScore();
            CreditLevel level = CreditLevel.of(score);
            vo.setScore(score);
            vo.setLevel(level.name());
            vo.setLevelLabel(level.getLabel());
            vo.setLevelColor(level.getColor());
            records.add(vo);
        }
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public IPage<CreditLogVO> logs(Long userId, Integer type, Long pageNum, Long pageSize) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Page<CreditLog> page = new Page<>(current, size);
        IPage<CreditLog> result = creditLogMapper.selectPageByUser(page, userId, type);
        Page<CreditLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(toLogVOList(result.getRecords()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreditVO adjust(Long userId, CreditAdjustDTO dto, Long operatorId) {
        if (dto.getChangeScore() == null || dto.getChangeScore() == 0) {
            throw new BusinessException("调整分值不能为 0");
        }
        int actual = addScore(userId, dto.getChangeScore(), CreditLogType.ADMIN_ADJUST,
                dto.getReason(), null, operatorId);
        if (actual == 0) {
            throw new BusinessException("该用户信誉分已在边界值上，本次调整未生效");
        }
        return detail(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addScore(Long userId, int delta, int type, String reason, Long relatedId, Long operatorId) {
        // 行锁读：保证流水里的 before 就是这次真正生效的起点，
        // 并发两条评价不会各自基于同一个旧值算分
        Integer before = sysUserMapper.selectScoreForUpdate(userId);
        if (before == null) {
            throw new BusinessException("用户不存在");
        }
        int after = CreditRule.clamp(before + delta);
        int actual = after - before;

        // 变动为 0 时：delta 本身为 0（如 3 星评价）不记流水，避免刷屏；
        // delta 非 0 但被上下限截断则记一条 0 变动，让"为什么没加分"有据可查
        if (actual != 0) {
            sysUserMapper.updateCreditScore(userId, after);
            // 用户信息有 30 分钟缓存，分数变了必须失效，否则个人中心还显示旧分
            userService.evictCache(userId);
            // 信誉分变了，仪表盘的「平均信誉分」与「信誉分区间分布」都得重算。
            // 挂在 addScore 上是因为它是全系统唯一改分数的地方——
            // 评价加分、举报扣分、管理员调整三条链路都经过它，一处失效即可全覆盖
            dashboardCacheService.evictOnUserChange();
        }
        if (actual != 0 || delta != 0) {
            String finalReason = reason;
            if (actual == 0) {
                finalReason = reason + (delta > 0
                        ? "（分数已达上限 " + CreditRule.MAX_SCORE + "，未加分）"
                        : "（分数已为下限 " + CreditRule.MIN_SCORE + "，未扣分）");
            }
            CreditLog log = new CreditLog();
            log.setUserId(userId);
            log.setChangeScore(actual);
            log.setBeforeScore(before);
            log.setAfterScore(after);
            log.setType(type);
            log.setReason(finalReason);
            log.setRelatedId(relatedId);
            log.setOperatorId(operatorId);
            creditLogMapper.insert(log);
        }
        return actual;
    }

    @Override
    public void audit(Long userId, int type, String reason, Long relatedId, Long operatorId) {
        Integer score = sysUserMapper.selectScoreForUpdate(userId);
        if (score == null) {
            return;
        }
        CreditLog log = new CreditLog();
        log.setUserId(userId);
        log.setChangeScore(0);
        log.setBeforeScore(score);
        log.setAfterScore(score);
        log.setType(type);
        log.setReason(reason);
        log.setRelatedId(relatedId);
        log.setOperatorId(operatorId);
        creditLogMapper.insert(log);
    }

    /**
     * 统计某用户收到的评价。
     * <p>
     * Mapper 返回的是 Map（SUM 出来的类型由驱动决定，可能是 Long 也可能是 BigDecimal），
     * 统一在这里做一次数值转换，上层拿到的就是确定的 Long/Double。
     */
    public ReviewStatVO statOf(Long userId) {
        Map<String, Object> row = reviewMapper.selectStatOfUser(userId);
        ReviewStatVO vo = new ReviewStatVO();
        long total = toLong(row, "total");
        vo.setTotal(total);
        vo.setGoodCount(toLong(row, "goodCount"));
        vo.setMidCount(toLong(row, "midCount"));
        vo.setBadCount(toLong(row, "badCount"));
        vo.setStar5(toLong(row, "star5"));
        vo.setStar4(toLong(row, "star4"));
        vo.setStar3(toLong(row, "star3"));
        vo.setStar2(toLong(row, "star2"));
        vo.setStar1(toLong(row, "star1"));
        vo.setImageCount(toLong(row, "imageCount"));
        vo.setAvgStars(toDouble(row, "avgStars"));
        vo.setGoodRate(total == 0 ? 0.0 : round1(vo.getGoodCount() * 100.0 / total));
        return vo;
    }

    /**
     * 商品评价统计，供商品详情页展示。商品维度没有「信誉等级」，
     * 所以复用同一个 VO 但不填等级字段。
     */
    @Override
    public ReviewStatVO statOfProduct(Long productId) {
        Map<String, Object> row = reviewMapper.selectStatOfProduct(productId);
        ReviewStatVO vo = new ReviewStatVO();
        long total = toLong(row, "total");
        vo.setTotal(total);
        vo.setGoodCount(toLong(row, "goodCount"));
        vo.setAvgStars(toDouble(row, "avgStars"));
        vo.setMidCount(0L);
        vo.setBadCount(0L);
        vo.setStar1(0L);
        vo.setStar2(0L);
        vo.setStar3(0L);
        vo.setStar4(0L);
        vo.setStar5(0L);
        vo.setImageCount(0L);
        vo.setGoodRate(total == 0 ? 0.0 : round1(vo.getGoodCount() * 100.0 / total));
        return vo;
    }

    private List<ReviewVO> recentReviews(Long userId) {
        ReviewQueryDTO query = new ReviewQueryDTO();
        query.setRevieweeId(userId);
        query.setPageNum(1L);
        query.setPageSize((long) RECENT_REVIEW_SIZE);
        Page<Review> page = new Page<>(1, RECENT_REVIEW_SIZE);
        IPage<Review> result = reviewMapper.selectPageByQuery(page, query);
        return reviewAssembler.toVOList(result.getRecords(), null, true);
    }

    private List<CreditVO.LevelItem> buildLevels() {
        List<CreditVO.LevelItem> items = new ArrayList<>();
        for (CreditLevel level : CreditLevel.values()) {
            CreditVO.LevelItem item = new CreditVO.LevelItem();
            item.setCode(level.name());
            item.setLabel(level.getLabel());
            item.setMin(level.getMin());
            item.setMax(level.getMax());
            item.setColor(level.getColor());
            items.add(item);
        }
        return items;
    }

    /**
     * 流水 → VO，批量补操作人昵称（管理员调整时才有 operatorId）。
     */
    private List<CreditLogVO> toLogVOList(List<CreditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> operatorIds = logs.stream()
                .map(CreditLog::getOperatorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, SysUser> operators = operatorIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
        List<CreditLogVO> list = new ArrayList<>(logs.size());
        for (CreditLog log : logs) {
            CreditLogVO vo = new CreditLogVO();
            vo.setId(log.getId());
            vo.setUserId(log.getUserId());
            vo.setChangeScore(log.getChangeScore());
            vo.setBeforeScore(log.getBeforeScore());
            vo.setAfterScore(log.getAfterScore());
            vo.setType(log.getType());
            vo.setTypeLabel(CreditLogType.label(log.getType()));
            vo.setReason(log.getReason());
            vo.setRelatedId(log.getRelatedId());
            vo.setOperatorId(log.getOperatorId());
            SysUser operator = log.getOperatorId() == null ? null : operators.get(log.getOperatorId());
            vo.setOperatorName(operator == null ? null : operator.getNickname());
            vo.setCreateTime(log.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    private long toLong(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double toDouble(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        if (value == null) {
            return 0.0;
        }
        // AVG() 在 MySQL 驱动里可能返回 BigDecimal 或 Double，两者都是 Number
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
