// 文件路径: src/main/java/com/campus/market/service/ReportService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.ReportHandleDTO;
import com.campus.market.dto.ReportQueryDTO;
import com.campus.market.dto.ReportSubmitDTO;
import com.campus.market.entity.Report;
import com.campus.market.vo.ReportStatsVO;
import com.campus.market.vo.ReportVO;

/**
 * 举报业务接口。
 * <p>
 * 举报闭环共 4 步，每一步都是一个独立方法而不是一个大方法里的状态机：
 * <pre>
 *   submit()   用户提交       → status=待处理(0)
 *   accept()   客服接单       → status=处理中(1)，记 handler_id 与 accept_time
 *   handle()   客服给出结论   → status=已处理(2)/已驳回(3)，执行联动动作并发通知
 * </pre>
 * 接单与处理分成两步的理由：接单是一个「排他性的占位动作」——
 * 两个客服同时看到待处理列表，必须靠接单让状态从 0 原子地变成 1，
 * 否则两个人会同时核实同一条举报并给出两个互相矛盾的结论。
 */
public interface ReportService extends IService<Report> {

    /**
     * 提交举报。
     * <p>
     * 三条业务校验：
     * <ul>
     *   <li>不能举报自己；</li>
     *   <li>同一用户对同一对象 24 小时内只能举报一次（防止刷单淹没客服队列）；</li>
     *   <li>对象必须存在（不存在直接拒绝，而不是存一条指向空气的记录）。</li>
     * </ul>
     * 同时会把被举报对象的关键信息存成快照。
     */
    Long submit(Long reporterId, ReportSubmitDTO dto);

    /** 我的举报列表（分页，带状态与处理结果） */
    IPage<ReportVO> myPage(Long reporterId, Long pageNum, Long pageSize);

    /** 我的举报详情（只能看自己的） */
    ReportVO myDetail(Long reporterId, Long id);

    /**
     * 管理端举报列表。
     *
     * @param currentUserId 当前登录者，用于标记「这条是不是我接的」
     */
    IPage<ReportVO> adminPage(ReportQueryDTO query, Long currentUserId);

    /** 管理端举报详情（含完整快照） */
    ReportVO adminDetail(Long id);

    /**
     * 客服接单：待处理 → 处理中。
     * <p>
     * 条件更新（WHERE status = 0）保证并发下只有一个客服能接到，
     * 另一个会收到「该举报已被他人接单」而不是静默覆盖 handler_id。
     */
    void accept(Long reportId, Long handlerId);

    /**
     * 处理举报：执行处置动作 + 双发通知（举报人 + 被举报人）。
     *
     * @param operatorId 操作人（客服或管理员）
     */
    void handle(Long reportId, ReportHandleDTO dto, Long operatorId);

    /** 举报统计（工作台与统计卡片） */
    ReportStatsVO stats();
}