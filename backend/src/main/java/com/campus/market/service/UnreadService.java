// 文件路径: src/main/java/com/campus/market/service/UnreadService.java
package com.campus.market.service;

import com.campus.market.vo.UnreadSummaryVO;

/**
 * 未读数汇总服务（聊天 + 通知）。
 * <p>
 * 单独抽出来是为了打断依赖环：ChatService 需要通知未读数才能算总角标，NoticeService 也需要
 * 聊天未读数才能在推送里给出完整汇总；两者互相注入会形成循环依赖。
 * 把「怎么算未读」下放到这里，Chat 与 Notice 各自只负责在数据变化后调一次 {@link #evict} + {@link #push}。
 */
public interface UnreadService {

    /**
     * 当前用户的未读汇总。走 Redis 缓存，未命中回源数据库。
     */
    UnreadSummaryVO summary(Long userId);

    /**
     * 使缓存失效。任何会改变未读数的写操作（发消息、标记已读、发通知）完成后都必须调用。
     */
    void evict(Long userId);

    /**
     * 计算并推送 unread 帧。用户不在线时 push 会静默失败，不影响业务。
     */
    void push(Long userId);

    /**
     * 数据变化后一步到位：先失效缓存，再推送最新汇总。
     */
    default void refresh(Long userId) {
        evict(userId);
        push(userId);
    }
}
