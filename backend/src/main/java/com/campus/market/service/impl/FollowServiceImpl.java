// 文件路径: src/main/java/com/campus/market/service/impl/FollowServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.ResultCode;
import com.campus.market.entity.Follow;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.FollowMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.service.FollowService;
import com.campus.market.vo.FollowStateVO;
import com.campus.market.vo.UserFollowVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 关注业务实现。
 * <p>
 * 关注关系是"多对多自关联"，列表页最容易写出 N+1 查询，
 * 这里统一采用「一次分页查关系 + 一次批量查用户 + 一次分组查在售数」的写法，
 * 无论一页多少条都只有 3 次 SQL。
 * <p>
 * 关注数 / 粉丝数冗余在 sys_user，切换关注时与关系记录同事务增减，
 * 个人中心与详情页直接读字段，不做 COUNT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    private final SysUserMapper sysUserMapper;
    private final ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowStateVO toggle(Long followerId, Long followingId) {
        if (followerId == null || followingId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        if (followerId.equals(followingId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能关注自己");
        }
        SysUser target = sysUserMapper.selectById(followingId);
        if (target == null) {
            throw new BusinessException("用户不存在或已注销");
        }
        if (Integer.valueOf(1).equals(target.getStatus())) {
            throw new BusinessException("该用户已被封禁，无法关注");
        }

        Follow existing = this.lambdaQuery()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFollowingId, followingId)
                .one();

        boolean followed;
        if (existing != null) {
            this.removeById(existing.getId());
            sysUserMapper.addFollowCount(followerId, -1);
            sysUserMapper.addFansCount(followingId, -1);
            followed = false;
            log.info("取消关注: followerId={}, followingId={}", followerId, followingId);
        } else {
            Follow follow = new Follow();
            follow.setFollowerId(followerId);
            follow.setFollowingId(followingId);
            try {
                this.save(follow);
                sysUserMapper.addFollowCount(followerId, 1);
                sysUserMapper.addFansCount(followingId, 1);
            } catch (DuplicateKeyException e) {
                // 并发重复点击由唯一索引兜底，视为已关注，不重复加计数
                log.debug("重复关注被唯一索引拦截: followerId={}, followingId={}", followerId, followingId);
            }
            followed = true;
            log.info("关注用户: followerId={}, followingId={}", followerId, followingId);
        }

        SysUser latest = sysUserMapper.selectById(followingId);
        int fans = latest == null || latest.getFansCount() == null ? 0 : latest.getFansCount();
        return FollowStateVO.of(followed, fans);
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        if (followerId == null || followingId == null || followerId.equals(followingId)) {
            return false;
        }
        return this.lambdaQuery()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFollowingId, followingId)
                .count() > 0;
    }

    @Override
    public IPage<UserFollowVO> following(Long userId, Integer pageNum, Integer pageSize) {
        IPage<Follow> page = this.page(buildPage(pageNum, pageSize),
                Wrappers.<Follow>lambdaQuery()
                        .eq(Follow::getFollowerId, userId)
                        .orderByDesc(Follow::getId));
        // 关注列表里每一条都必然是我已关注的人
        return convert(page, Follow::getFollowingId, true);
    }

    @Override
    public IPage<UserFollowVO> followers(Long userId, Integer pageNum, Integer pageSize) {
        IPage<Follow> page = this.page(buildPage(pageNum, pageSize),
                Wrappers.<Follow>lambdaQuery()
                        .eq(Follow::getFollowingId, userId)
                        .orderByDesc(Follow::getId));
        // 粉丝列表需要区分「已回关 / 未回关」，由 convert 内部批量判定
        return convert(page, Follow::getFollowerId, false);
    }

    // ==================== 内部方法 ====================

    private Page<Follow> buildPage(Integer pageNum, Integer pageSize) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        return new Page<>(current, size);
    }

    /**
     * 关系分页转用户卡片分页。
     *
     * @param userIdGetter 从关系中取出"对端用户ID"：关注列表取 followingId，粉丝列表取 followerId
     * @param followedAll  该列表是否全部为「已关注」（关注列表为 true，粉丝列表需要逐条判断）
     */
    private IPage<UserFollowVO> convert(IPage<Follow> page, Function<Follow, Long> userIdGetter, boolean followedAll) {
        Page<UserFollowVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<Follow> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        List<Long> userIds = records.stream().map(userIdGetter).distinct().toList();
        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
        Map<Long, Long> onSaleMap = countOnSale(userIds);
        Set<Long> followedIds = followedAll ? new HashSet<>(userIds) : followedIdsOf(records, userIds);

        List<UserFollowVO> vos = records.stream().map(record -> {
            Long userId = userIdGetter.apply(record);
            UserFollowVO vo = new UserFollowVO();
            vo.setUserId(userId);
            SysUser user = userMap.get(userId);
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
                vo.setStudentVerified(Integer.valueOf(1).equals(user.getStudentVerified()));
                vo.setCreditScore(user.getCreditScore());
                vo.setFansCount(user.getFansCount());
            } else {
                // 账号被注销时保留占位，避免列表出现空白项
                vo.setNickname("已注销用户");
                vo.setStudentVerified(false);
                vo.setCreditScore(0);
                vo.setFansCount(0);
            }
            vo.setOnSaleCount(onSaleMap.getOrDefault(userId, 0L));
            vo.setFollowed(followedIds.contains(userId));
            vo.setFollowTime(record.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        result.setRecords(vos);
        return result;
    }

    /**
     * 粉丝列表的「是否已回关」判定：一次查完当前登录用户对这些人的关注关系。
     */
    private Set<Long> followedIdsOf(List<Follow> records, List<Long> userIds) {
        Long selfId = records.get(0).getFollowingId();
        return this.lambdaQuery()
                .eq(Follow::getFollowerId, selfId)
                .in(Follow::getFollowingId, userIds)
                .list()
                .stream()
                .map(Follow::getFollowingId)
                .collect(Collectors.toSet());
    }

    /**
     * 批量统计在售商品数，返回 userId -> count。
     */
    private Map<Long, Long> countOnSale(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> row : productMapper.countOnSaleBySellers(userIds)) {
            Object sellerId = row.get("sellerId");
            Object cnt = row.get("cnt");
            if (sellerId instanceof Number id && cnt instanceof Number count) {
                map.put(id.longValue(), count.longValue());
            }
        }
        return map;
    }
}
