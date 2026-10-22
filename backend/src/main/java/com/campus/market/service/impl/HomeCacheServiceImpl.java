// 文件路径: src/main/java/com/campus/market/service/impl/HomeCacheServiceImpl.java
package com.campus.market.service.impl;

import com.campus.market.common.RedisKey;
import com.campus.market.service.HomeCacheService;
import com.campus.market.vo.HomeIndexVO;
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

/**
 * 首页聚合缓存实现。
 * <p>
 * 两个关键点（与模块 10 的仪表盘缓存同源，但这里只有一个 key，更简单）：
 * <ol>
 *   <li><b>缓存最终 VO 而不是原始数据</b>：命中缓存时完全不碰数据库，
 *       连一次「按 id 批量查商品」都不发生；</li>
 *   <li><b>失效推迟到事务提交之后</b>：事务内先删缓存、提交前有读请求进来，
 *       读请求会读到未提交的数据并把旧值写回缓存，
 *       之后事务回滚，缓存里就留下一个「数据库里并不存在」的首页。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeCacheServiceImpl implements HomeCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public HomeIndexVO getIndex() {
        try {
            String cached = redis.opsForValue().get(RedisKey.HOME_INDEX);
            if (!StringUtils.hasText(cached)) {
                return null;
            }
            return objectMapper.readValue(cached, new TypeReference<HomeIndexVO>() {
            });
        } catch (Exception e) {
            log.warn("读取首页缓存失败，回源数据库: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void putIndex(HomeIndexVO vo) {
        if (vo == null) {
            return;
        }
        try {
            redis.opsForValue().set(RedisKey.HOME_INDEX,
                    objectMapper.writeValueAsString(vo),
                    Duration.ofSeconds(INDEX_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("写入首页缓存失败: {}", e.getMessage());
        }
    }

    @Override
    public void evictIndex() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doEvict();
                }
            });
            return;
        }
        doEvict();
    }

    private void doEvict() {
        try {
            redis.delete(RedisKey.HOME_INDEX);
        } catch (Exception e) {
            // 删不掉也不能让保存失败：缓存最迟 5 分钟后自己过期
            log.warn("清除首页缓存失败: {}", e.getMessage());
        }
    }
}
