// 文件路径: src/main/java/com/campus/market/service/impl/ViewCountServiceImpl.java
package com.campus.market.service.impl;

import com.campus.market.common.RedisKey;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.service.ViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 浏览量 Redis 计数实现。
 * <p>
 * 回写策略：读取 Hash 中某商品的增量 delta → 累加到 MySQL → 从 Redis 减去 delta。
 * 不用「先删字段再写库」是因为删除与写库之间进来的访问会直接丢失；
 * 用「先写库再减」最多在极端并发下让某个访客看到 O(1) 的重复计数，
 * 对浏览量这种非精确指标可以接受，而且不会丢数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountServiceImpl implements ViewCountService {

    private final StringRedisTemplate redis;
    private final ProductMapper productMapper;

    @Override
    public void increment(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            redis.opsForHash().increment(RedisKey.PRODUCT_VIEW_DELTA, String.valueOf(productId), 1);
        } catch (Exception e) {
            // Redis 不可用时降级为直接写库，保证功能不中断
            log.warn("浏览量 Redis 计数失败，降级写库: productId={}, err={}", productId, e.getMessage());
            productMapper.addViewCount(productId, 1);
        }
    }

    @Override
    public int pending(Long productId) {
        if (productId == null) {
            return 0;
        }
        try {
            Object value = redis.opsForHash().get(RedisKey.PRODUCT_VIEW_DELTA, String.valueOf(productId));
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${app.view-count.flush-interval-ms:60000}",
            initialDelayString = "${app.view-count.flush-interval-ms:60000}")
    public int flush() {
        Map<Object, Object> entries;
        try {
            entries = redis.opsForHash().entries(RedisKey.PRODUCT_VIEW_DELTA);
        } catch (Exception e) {
            log.warn("浏览量回写跳过，Redis 不可用: {}", e.getMessage());
            return 0;
        }
        if (entries.isEmpty()) {
            return 0;
        }

        int affected = 0;
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Long productId;
            int delta;
            try {
                productId = Long.valueOf(String.valueOf(entry.getKey()));
                delta = Integer.parseInt(String.valueOf(entry.getValue()));
            } catch (NumberFormatException e) {
                redis.opsForHash().delete(RedisKey.PRODUCT_VIEW_DELTA, entry.getKey());
                continue;
            }
            if (delta <= 0) {
                redis.opsForHash().delete(RedisKey.PRODUCT_VIEW_DELTA, entry.getKey());
                continue;
            }
            try {
                productMapper.addViewCount(productId, delta);
                // 减去已回写部分，期间新增的访问量会留在 Hash 里等下一轮
                redis.opsForHash().increment(RedisKey.PRODUCT_VIEW_DELTA, String.valueOf(productId), -delta);
                affected++;
            } catch (Exception e) {
                log.warn("浏览量回写失败，保留增量待下次重试: productId={}, delta={}, err={}",
                        productId, delta, e.getMessage());
            }
        }
        // 清理已归零的字段，避免 Hash 无限增长
        entries.keySet().forEach(key -> {
            Object left = redis.opsForHash().get(RedisKey.PRODUCT_VIEW_DELTA, key);
            if (left != null && Long.parseLong(left.toString()) <= 0) {
                redis.opsForHash().delete(RedisKey.PRODUCT_VIEW_DELTA, key);
            }
        });

        if (affected > 0) {
            log.info("浏览量批量回写完成: {} 件商品", affected);
        }
        return affected;
    }

    @Override
    public void clear(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            redis.opsForHash().delete(RedisKey.PRODUCT_VIEW_DELTA, String.valueOf(productId));
        } catch (Exception e) {
            log.warn("清理浏览量增量失败: productId={}, err={}", productId, e.getMessage());
        }
    }
}
