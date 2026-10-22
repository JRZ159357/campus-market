// 文件路径: src/main/java/com/campus/market/service/impl/UnreadServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.market.common.RedisKey;
import com.campus.market.entity.Notice;
import com.campus.market.mapper.ChatSessionMapper;
import com.campus.market.mapper.NoticeMapper;
import com.campus.market.service.UnreadService;
import com.campus.market.vo.UnreadSummaryVO;
import com.campus.market.ws.WsFrame;
import com.campus.market.ws.WsSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 未读数实现。
 * <p>
 * 一致性策略（论文「未读数一致性」一节的核心）：
 * <ol>
 *   <li><b>数据库是唯一事实来源</b>：会话内未读存 chat_session 的冗余计数，
 *       通知未读是 notice 表中 is_read = 0 的行数；</li>
 *   <li><b>Redis 只缓存标量结果</b>：缓存「聊天未读总数」与「通知未读总数」两个数字，TTL 1 天；</li>
 *   <li><b>写后失效而非更新</b>：任何写操作只删除 key，不尝试在缓存上做加减。
 *       缓存参与加减在并发下极易算错（两次发送同时读到旧值再各自 +1），而删除是幂等的；</li>
 *   <li><b>降级可用</b>：Redis 异常时直接回源数据库，功能不中断，只是少了缓存收益。</li>
 * </ol>
 * 这套策略保证「缓存可能短暂缺失，但永远不会给出错误数字」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnreadServiceImpl implements UnreadService {

    private static final Duration CACHE_TTL = Duration.ofDays(1);

    private final ChatSessionMapper chatSessionMapper;
    private final NoticeMapper noticeMapper;
    private final StringRedisTemplate redis;
    private final WsSessionManager wsSessionManager;

    @Override
    public UnreadSummaryVO summary(Long userId) {
        if (userId == null) {
            return zero();
        }
        UnreadSummaryVO vo = new UnreadSummaryVO();
        vo.setChatUnread(cachedCount(RedisKey.CHAT_UNREAD + userId,
                () -> chatSessionMapper.sumMyUnread(userId)));
        vo.setNoticeUnread(cachedCount(RedisKey.NOTICE_UNREAD + userId,
                () -> noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                        .eq(Notice::getUserId, userId)
                        .eq(Notice::getIsRead, 0))));
        vo.setUnreadSessions(chatSessionMapper.countUnreadSessions(userId));
        vo.setTotal(vo.getChatUnread() + vo.getNoticeUnread());
        return vo;
    }

    @Override
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.delete(RedisKey.CHAT_UNREAD + userId);
            redis.delete(RedisKey.NOTICE_UNREAD + userId);
        } catch (Exception e) {
            log.warn("清除未读缓存失败 userId={}：{}", userId, e.getMessage());
        }
    }

    @Override
    public void push(Long userId) {
        if (userId == null || !wsSessionManager.isOnline(userId)) {
            return;
        }
        wsSessionManager.push(userId, WsFrame.of(WsFrame.TYPE_UNREAD, summary(userId)));
    }

    /**
     * 先读缓存，未命中则调用 loader 回源并回填。Redis 异常时直接走 loader。
     */
    private long cachedCount(String key, CountLoader loader) {
        try {
            String cached = redis.opsForValue().get(key);
            if (StringUtils.hasText(cached)) {
                return Long.parseLong(cached);
            }
        } catch (Exception e) {
            log.debug("读取未读缓存失败 key={}：{}", key, e.getMessage());
        }
        long value = loader.load();
        try {
            redis.opsForValue().set(key, String.valueOf(value), CACHE_TTL);
        } catch (Exception e) {
            log.debug("写入未读缓存失败 key={}：{}", key, e.getMessage());
        }
        return value;
    }

    private UnreadSummaryVO zero() {
        UnreadSummaryVO vo = new UnreadSummaryVO();
        vo.setChatUnread(0L);
        vo.setUnreadSessions(0L);
        vo.setNoticeUnread(0L);
        vo.setTotal(0L);
        return vo;
    }

    /**
     * 计数回调。用函数式接口把「取哪个数」与「缓存套路」解耦，两个分支共用同一段缓存逻辑。
     */
    @FunctionalInterface
    private interface CountLoader {
        long load();
    }
}
