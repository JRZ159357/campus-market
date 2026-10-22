// 文件路径: src/main/java/com/campus/market/service/NoticeService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.NoticePublishDTO;
import com.campus.market.dto.NoticeQueryDTO;
import com.campus.market.entity.Notice;
import com.campus.market.vo.NoticeBroadcastVO;
import com.campus.market.vo.NoticeVO;

/**
 * 系统通知业务接口。
 * <p>
 * 通知有两个来源：
 * <ol>
 *   <li><b>业务触发</b>：订单状态变更、商品/认证审核、举报处理，由各业务 Service 主动调用
 *       {@code notifyXxx}，通知内容与业务语义强相关，因此收发点写在业务侧；</li>
 *   <li><b>管理员发布</b>：系统公告，走 {@link #publish}，按用户扇形写入。</li>
 * </ol>
 * 所有 notifyXxx 都用独立事务（REQUIRES_NEW）：通知失败绝不能连带把下单、审核这些主业务回滚。
 */
public interface NoticeService extends IService<Notice> {

    /** 我的通知列表（未读优先，再按时间倒序） */
    IPage<NoticeVO> page(Long userId, NoticeQueryDTO query);

    /** 通知详情（只能看自己的，不存在或不属于自己统一报「通知不存在」） */
    NoticeVO detail(Long userId, Long id);

    /** 标记单条已读，返回受影响行数（0 表示不是自己的通知或已读） */
    int read(Long userId, Long id);

    /** 全部标记已读，返回影响条数 */
    int readAll(Long userId);

    /** 发布系统公告（管理员） */
    void publish(NoticePublishDTO dto);

    /** 已发布公告列表（一次发布 = 一组通知，按标题 + 发布时间聚合） */
    IPage<NoticeBroadcastVO> broadcastPage(NoticeQueryDTO query);

    /** 删除一次公告发布（整组删除） */
    void deleteBroadcast(Long id);

    /** 订单相关通知 */
    void notifyOrder(Long userId, String title, String content, Long orderId);

    /** 审核相关通知；productId 不为空时前端可跳商品详情 */
    void notifyAudit(Long userId, String title, String content, Long productId);

    /** 举报处理通知 */
    void notifyReport(Long userId, String title, String content, Long productId);

    /**
     * 广播给所有「正常状态」的管理员（模块11：敏感词命中待复核）。
     * <p>
     * 为什么不复用 {@link #notifyAudit} 逐个调用：管理员是动态的
     * （可能今天新加一个），调用方不该知道「谁是管理员」这件事——
     * 那是账号管理模块的职责。让通知服务自己去查一遍，
     * 调用方只表达「这件事要让管理员知道」这个意图。
     * <p>
     * 只发给 status=0 的管理员：已禁用的账号登录不了，
     * 给它写通知等于往一个没人看的收件箱里投信。
     */
    void notifyAllAdmins(String title, String content, Long relatedId);
}
