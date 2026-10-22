// 文件路径: src/main/java/com/campus/market/service/impl/NoticeServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.NoticeType;
import com.campus.market.common.RoleConst;
import com.campus.market.dto.NoticePublishDTO;
import com.campus.market.dto.NoticeQueryDTO;
import com.campus.market.entity.Notice;
import com.campus.market.entity.SysUser;
import com.campus.market.entity.TradeOrder;
import com.campus.market.mapper.NoticeMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.mapper.TradeOrderMapper;
import com.campus.market.service.NoticeService;
import com.campus.market.service.UnreadService;
import com.campus.market.vo.NoticeBroadcastVO;
import com.campus.market.vo.NoticeVO;
import com.campus.market.ws.WsFrame;
import com.campus.market.ws.WsSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统通知实现。
 * <p>
 * 三个设计要点：
 * <ol>
 *   <li><b>一人一行</b>：公告也按用户拆行，已读状态天然隔离，不需要额外的已读关联表；</li>
 *   <li><b>独立事务</b>：业务触发的通知用 REQUIRES_NEW，通知写失败只会丢一条提醒，
 *       不会让下单、审核这些主业务跟着回滚；</li>
 *   <li><b>在线即推</b>：落库后立刻尝试 WebSocket 推送，用户不在线就跳过——
 *       数据已经在库里，他下次打开小程序拉列表就能看到，不需要离线队列。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {

    /** 公告扇形写入的分批大小，避免一条 SQL 拼进上千个 values */
    private static final int BATCH_SIZE = 500;

    private final SysUserMapper sysUserMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final UnreadService unreadService;
    private final WsSessionManager wsSessionManager;

    @Override
    public IPage<NoticeVO> page(Long userId, NoticeQueryDTO query) {
        LambdaQueryWrapper<Notice> wrapper = Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getUserId, userId)
                .eq(query.getType() != null, Notice::getType, query.getType())
                .eq(Boolean.TRUE.equals(query.getOnlyUnread()), Notice::getIsRead, 0)
                // 未读优先，再按时间倒序：小程序通知 Tab 里新提醒永远在最上面
                .orderByAsc(Notice::getIsRead)
                .orderByDesc(Notice::getCreateTime)
                .orderByDesc(Notice::getId);

        IPage<Notice> page = page(new Page<>(pageNum(query.getPageNum()), pageSize(query.getPageSize())), wrapper);
        IPage<NoticeVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(toVOList(page.getRecords()));
        return result;
    }

    @Override
    public NoticeVO detail(Long userId, Long id) {
        Notice notice = getOne(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getId, id)
                .eq(Notice::getUserId, userId)
                .last("limit 1"));
        if (notice == null) {
            throw new BusinessException("通知不存在或已被删除");
        }
        return toVOList(Collections.singletonList(notice)).get(0);
    }

    @Override
    public int read(Long userId, Long id) {
        int rows = baseMapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .eq(Notice::getId, id)
                .eq(Notice::getUserId, userId)
                .eq(Notice::getIsRead, 0)
                .set(Notice::getIsRead, 1));
        if (rows > 0) {
            unreadService.refresh(userId);
        }
        return rows;
    }

    @Override
    public int readAll(Long userId) {
        int rows = baseMapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .eq(Notice::getUserId, userId)
                .eq(Notice::getIsRead, 0)
                .set(Notice::getIsRead, 1));
        if (rows > 0) {
            unreadService.refresh(userId);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(NoticePublishDTO dto) {
        List<Long> targets;
        if (Integer.valueOf(2).equals(dto.getScope())) {
            targets = dto.getUserIds() == null ? Collections.emptyList() : dto.getUserIds().stream()
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (targets.isEmpty()) {
                throw new BusinessException("请选择接收公告的用户");
            }
        } else {
            targets = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                            .select(SysUser::getId)
                            .eq(SysUser::getStatus, 0))
                    .stream().map(SysUser::getId).collect(Collectors.toList());
        }
        if (targets.isEmpty()) {
            throw new BusinessException("当前没有可接收公告的用户");
        }

        // 同一次发布共用一个时间戳，管理端才能按 (title, content, create_time) 把这一组聚合出来
        LocalDateTime now = LocalDateTime.now();
        List<Notice> notices = new ArrayList<>(targets.size());
        for (Long uid : targets) {
            Notice notice = new Notice();
            notice.setUserId(uid);
            notice.setType(NoticeType.ANNOUNCEMENT.getCode());
            notice.setTitle(dto.getTitle());
            notice.setContent(dto.getContent());
            notice.setIsRead(0);
            notice.setCreateTime(now);
            notices.add(notice);
        }
        saveBatch(notices, BATCH_SIZE);

        // 在线用户立即收到，离线用户下次拉列表时看到
        notices.forEach(notice -> {
            unreadService.evict(notice.getUserId());
            pushTo(notice);
        });
        log.info("系统公告已发布：{}，接收用户 {} 人", dto.getTitle(), notices.size());
    }

    @Override
    public IPage<NoticeBroadcastVO> broadcastPage(NoticeQueryDTO query) {
        // 一次发布 = 一组 type=3 的通知，聚合交给 SQL 的 GROUP BY，不把全表捞进内存
        long current = pageNum(query.getPageNum());
        long size = pageSize(query.getPageSize());
        long total = baseMapper.countBroadcastGroups();
        List<NoticeBroadcastVO> records = total == 0
                ? Collections.emptyList()
                : baseMapper.selectBroadcastPage((current - 1) * size, size);
        IPage<NoticeBroadcastVO> result = new Page<>(current, size, total);
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBroadcast(Long id) {
        Notice representative = baseMapper.selectById(id);
        if (representative == null || !NoticeType.ANNOUNCEMENT.getCode().equals(representative.getType())) {
            throw new BusinessException("公告不存在或已被删除");
        }
        int rows = baseMapper.delete(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getType, NoticeType.ANNOUNCEMENT.getCode())
                .eq(Notice::getTitle, representative.getTitle())
                .eq(Notice::getContent, representative.getContent())
                .eq(Notice::getCreateTime, representative.getCreateTime()));
        log.info("公告已删除：{}，影响通知 {} 条", representative.getTitle(), rows);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void notifyOrder(Long userId, String title, String content, Long orderId) {
        send(userId, NoticeType.ORDER, title, content, orderId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void notifyAudit(Long userId, String title, String content, Long productId) {
        send(userId, NoticeType.AUDIT, title, content, productId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void notifyReport(Long userId, String title, String content, Long productId) {
        send(userId, NoticeType.REPORT, title, content, productId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void notifyAllAdmins(String title, String content, Long relatedId) {
        List<SysUser> admins = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId)
                .eq(SysUser::getRole, RoleConst.ADMIN)
                .eq(SysUser::getStatus, 0));
        if (admins.isEmpty()) {
            log.warn("没有可用的管理员账号，广播通知被丢弃：{}", title);
            return;
        }
        for (SysUser admin : admins) {
            // send 内部已经吞掉异常，单个管理员写入失败不影响其他人收到
            send(admin.getId(), NoticeType.AUDIT, title, content, relatedId);
        }
    }

    /**
     * 落库 + 刷新未读 + 在线推送。三个动作都不允许抛出到调用方：
     * 通知是「锦上添花」，主业务不能因为一条提醒写入失败而失败。
     */
    private void send(Long userId, NoticeType type, String title, String content, Long relatedId) {
        if (userId == null || !StringUtils.hasText(title)) {
            return;
        }
        try {
            Notice notice = new Notice();
            notice.setUserId(userId);
            notice.setType(type.getCode());
            notice.setTitle(title);
            notice.setContent(content);
            notice.setRelatedId(relatedId);
            notice.setIsRead(0);
            baseMapper.insert(notice);
            unreadService.evict(userId);
            pushTo(notice);
        } catch (Exception e) {
            log.warn("发送通知失败 userId={} title={}：{}", userId, title, e.getMessage());
        }
    }

    private void pushTo(Notice notice) {
        if (!wsSessionManager.isOnline(notice.getUserId())) {
            return;
        }
        NoticeVO vo = toVO(notice, Collections.emptyMap());
        wsSessionManager.push(notice.getUserId(),
                WsFrame.of(WsFrame.TYPE_NOTICE, vo));
        unreadService.push(notice.getUserId());
    }

    /**
     * 批量转换，顺带一次性查出订单号（订单通知要跳订单详情页，页面参数是订单号而不是自增 ID）。
     */
    private List<NoticeVO> toVOList(List<Notice> notices) {
        if (notices == null || notices.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> orderIds = notices.stream()
                .filter(n -> NoticeType.ORDER.getCode().equals(n.getType()) && n.getRelatedId() != null)
                .map(Notice::getRelatedId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> orderNoMap = orderIds.isEmpty()
                ? Collections.emptyMap()
                : tradeOrderMapper.selectList(Wrappers.<TradeOrder>lambdaQuery()
                        .select(TradeOrder::getId, TradeOrder::getOrderNo)
                        .in(TradeOrder::getId, orderIds))
                .stream().collect(Collectors.toMap(TradeOrder::getId, o ->
                        o.getOrderNo() == null ? "" : o.getOrderNo(), (a, b) -> a));
        return notices.stream().map(n -> toVO(n, orderNoMap)).collect(Collectors.toList());
    }

    private NoticeVO toVO(Notice notice, Map<Long, String> orderNoMap) {
        NoticeVO vo = new NoticeVO();
        vo.setId(notice.getId());
        vo.setType(notice.getType());
        vo.setTypeLabel(NoticeType.of(notice.getType()).getLabel());
        vo.setTitle(notice.getTitle());
        vo.setContent(notice.getContent());
        vo.setRelatedId(notice.getRelatedId());
        vo.setIsRead(notice.getIsRead());
        vo.setCreateTime(notice.getCreateTime());
        vo.setTarget(targetOf(notice));
        if (NoticeType.ORDER.getCode().equals(notice.getType()) && notice.getRelatedId() != null) {
            vo.setRelatedNo(orderNoMap.get(notice.getRelatedId()));
        }
        return vo;
    }

    /**
     * 跳转目标由「类型 + 是否有关联业务」唯一确定，前端不需要再写一遍判断。
     */
    private String targetOf(Notice notice) {
        NoticeType type = NoticeType.of(notice.getType());
        return switch (type) {
            case ORDER -> notice.getRelatedId() == null ? "none" : "order";
            case REPORT -> notice.getRelatedId() == null ? "none" : "product";
            // 审核通知里，商品审核带 productId 可跳详情；学生认证审核不带关联，只展示正文
            case AUDIT -> notice.getRelatedId() == null ? "none" : "product";
            case ANNOUNCEMENT -> "none";
        };
    }

    private long pageNum(Long value) {
        return value == null || value < 1 ? 1 : value;
    }

    private long pageSize(Long value) {
        if (value == null || value < 1) {
            return 10;
        }
        return Math.min(value, 100);
    }
}
