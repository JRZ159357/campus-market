// 文件路径: src/main/java/com/campus/market/service/impl/FavoriteServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.ProductStatus;
import com.campus.market.common.RedisKey;
import com.campus.market.entity.Favorite;
import com.campus.market.entity.Product;
import com.campus.market.entity.ProductImage;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.FavoriteMapper;
import com.campus.market.mapper.ProductImageMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.service.FavoriteService;
import com.campus.market.vo.FavoriteFeedVO;
import com.campus.market.vo.FavoriteProductVO;
import com.campus.market.vo.FavoriteStateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏业务实现。
 * <p>
 * 三条关键设计：
 * <ol>
 *   <li><b>是否收藏走 Redis</b>：每个用户一个 Set（member=productId），
 *       详情页每次打开都要判断收藏态，SISMEMBER 是 O(1)，比一次 SQL 便宜一个量级；
 *       集合未命中时一次性回源全量商品ID 并回填，之后不再打库。</li>
 *   <li><b>计数冗余</b>：product.favorite_count 与 sys_user.favorite_count 与
 *       收藏记录在同一事务内增减，列表与个人中心读字段即可，不 COUNT。</li>
 *   <li><b>物理删除</b>：取消收藏直接删记录，唯一索引 (user_id, product_id)
 *       让重复点击天然幂等，不存在"逻辑删除 + 唯一索引"的冲突问题。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    /** 收藏集合缓存时长：7 天，用户长期不访问自然淘汰，不占内存 */
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    /**
     * 哨兵成员。Redis 无法用一个"空集合"表达"已加载但没有收藏"，
     * 写入固定成员 0（productId 自增恒大于 0）后，就能区分
     * 「缓存不存在需回源」与「缓存存在但用户没有收藏」。
     */
    private static final String SENTINEL = "0";

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate redis;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteStateVO toggle(Long userId, Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在或已被删除");
        }

        Favorite existing = this.lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)
                .one();

        boolean favorited;
        if (existing != null) {
            this.removeById(existing.getId());
            productMapper.addFavoriteCount(productId, -1);
            sysUserMapper.addFavoriteCount(userId, -1);
            cacheRemove(userId, productId);
            favorited = false;
            log.info("取消收藏: userId={}, productId={}", userId, productId);
        } else {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setProductId(productId);
            try {
                this.save(favorite);
                productMapper.addFavoriteCount(productId, 1);
                sysUserMapper.addFavoriteCount(userId, 1);
                cacheAdd(userId, productId);
            } catch (DuplicateKeyException e) {
                // 并发重复点击：唯一索引已拦住，视为"已收藏"直接返回，不重复加计数
                log.debug("重复收藏被唯一索引拦截: userId={}, productId={}", userId, productId);
            }
            favorited = true;
            log.info("收藏商品: userId={}, productId={}", userId, productId);
        }

        Product latest = productMapper.selectById(productId);
        int count = latest == null || latest.getFavoriteCount() == null ? 0 : latest.getFavoriteCount();
        return FavoriteStateVO.of(favorited, count);
    }

    @Override
    public boolean isFavorited(Long userId, Long productId) {
        if (userId == null || productId == null) {
            return false;
        }
        String key = RedisKey.FAVORITE_IDS + userId;
        try {
            if (Boolean.TRUE.equals(redis.opsForSet().isMember(key, String.valueOf(productId)))) {
                return true;
            }
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                // 集合已加载且不含该商品，直接判定未收藏，无需回源
                return false;
            }
            return loadIntoCache(userId).contains(productId);
        } catch (Exception e) {
            // Redis 不可用时降级为一次数据库查询，不影响收藏态展示
            log.warn("读取收藏缓存失败，回源数据库: userId={}, {}", userId, e.getMessage());
            return this.lambdaQuery()
                    .eq(Favorite::getUserId, userId)
                    .eq(Favorite::getProductId, productId)
                    .count() > 0;
        }
    }

    @Override
    public FavoriteFeedVO my(Long userId, Long cursor, Integer size) {
        int limit = size == null || size < 1 ? 10 : Math.min(size, 50);
        List<Favorite> favorites = this.lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .lt(cursor != null && cursor > 0, Favorite::getId, cursor)
                .orderByDesc(Favorite::getId)
                // 多取一条用于判断是否还有下一页，省掉一次 COUNT
                .last("limit " + (limit + 1))
                .list();

        boolean hasMore = favorites.size() > limit;
        if (hasMore) {
            favorites = new ArrayList<>(favorites.subList(0, limit));
        }

        FavoriteFeedVO vo = new FavoriteFeedVO();
        vo.setRecords(buildRecords(favorites));
        vo.setHasMore(hasMore);
        vo.setNextCursor(hasMore && !favorites.isEmpty() ? favorites.get(favorites.size() - 1).getId() : null);
        return vo;
    }

    // ==================== 缓存维护 ====================

    private Set<Long> loadIntoCache(Long userId) {
        List<Long> ids = baseMapper.selectProductIds(userId);
        Set<Long> set = new HashSet<>(ids);
        try {
            String key = RedisKey.FAVORITE_IDS + userId;
            Set<String> members = ids.stream().map(String::valueOf).collect(Collectors.toSet());
            members.add(SENTINEL);
            redis.opsForSet().add(key, members.toArray(new String[0]));
            redis.expire(key, CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入收藏集合缓存失败: userId={}, {}", userId, e.getMessage());
        }
        return set;
    }

    private void cacheAdd(Long userId, Long productId) {
        try {
            String key = RedisKey.FAVORITE_IDS + userId;
            redis.opsForSet().add(key, SENTINEL, String.valueOf(productId));
            redis.expire(key, CACHE_TTL);
        } catch (Exception e) {
            log.warn("收藏缓存写入失败: userId={}, {}", userId, e.getMessage());
        }
    }

    private void cacheRemove(Long userId, Long productId) {
        try {
            redis.opsForSet().remove(RedisKey.FAVORITE_IDS + userId, String.valueOf(productId));
        } catch (Exception e) {
            log.warn("收藏缓存移除失败: userId={}, {}", userId, e.getMessage());
        }
    }

    // ==================== 列表拼装 ====================

    /**
     * 批量拼装收藏卡片：图片、卖家各一次批量查询，避免 N+1。
     * 商品被删除后收藏记录理论上应一并清理，这里对缺失商品直接跳过，
     * 保证列表不会出现"空卡片"。
     */
    private List<FavoriteProductVO> buildRecords(List<Favorite> favorites) {
        if (favorites == null || favorites.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> productIds = favorites.stream().map(Favorite::getProductId).distinct().toList();
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        if (productMap.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> coverMap = new HashMap<>();
        productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                        .in(ProductImage::getProductId, productIds)
                        .orderByAsc(ProductImage::getSort)
                        .orderByAsc(ProductImage::getId))
                .forEach(img -> coverMap.putIfAbsent(img.getProductId(), img.getImageUrl()));

        List<Long> sellerIds = productMap.values().stream().map(Product::getSellerId).distinct().toList();
        Map<Long, SysUser> sellerMap = sysUserMapper.selectBatchIds(sellerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        List<FavoriteProductVO> records = new ArrayList<>();
        for (Favorite favorite : favorites) {
            Product product = productMap.get(favorite.getProductId());
            if (product == null) {
                continue;
            }
            FavoriteProductVO vo = new FavoriteProductVO();
            vo.setFavoriteId(favorite.getId());
            vo.setId(product.getId());
            vo.setSellerId(product.getSellerId());
            SysUser seller = sellerMap.get(product.getSellerId());
            if (seller != null) {
                vo.setSellerNickname(seller.getNickname());
                vo.setSellerAvatar(seller.getAvatar());
            }
            vo.setCategoryId(product.getCategoryId());
            vo.setTitle(product.getTitle());
            vo.setPrice(product.getPrice());
            vo.setOriginalPrice(product.getOriginalPrice());
            vo.setCover(coverMap.get(product.getId()));
            vo.setStatus(product.getStatus());
            vo.setStatusLabel(ProductStatus.label(product.getStatus()));
            vo.setViewCount(product.getViewCount());
            vo.setFavoriteCount(product.getFavoriteCount());
            vo.setIsVerifiedSeller(product.getIsVerifiedSeller());
            vo.setFavoriteTime(favorite.getCreateTime());
            records.add(vo);
        }
        return records;
    }
}
