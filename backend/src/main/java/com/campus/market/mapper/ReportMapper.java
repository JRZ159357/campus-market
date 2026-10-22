// 文件路径: src/main/java/com/campus/market/mapper/ReportMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.dto.ReportQueryDTO;
import com.campus.market.entity.Report;
import com.campus.market.vo.ReportVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 举报 Mapper。
 * <p>
 * 管理端列表必须联表带出「举报人昵称」「被举报人昵称」与「处理人昵称」，
 * 否则表格里只能看到一串 userId，客服完全无法工作。
 * 这三处都用 LEFT JOIN 而不是子查询：这里是一对多的「恰好一个」，
 * 子查询反而在关键词搜索时更难走索引。
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    /**
     * 管理端举报列表。
     * <p>
     * 关键词用三路 OR 覆盖：举报人昵称、被举报人昵称、举报内容。
     * 刻意<b>不</b>把「被举报对象标题」也塞进 OR 里——
     * 那需要先按 report_type 分别 JOIN product / sys_user / review，
     * 三张表结构完全不同，而管理端真正用得上的定位手段已经够用：
     * 先按类型筛选，再按被举报人昵称搜。
     */
    @Select("<script>"
            + "SELECT r.id, r.report_type, r.target_id, r.target_user_id, r.reason, r.content,"
            + " r.target_snapshot AS targetSnapshotRaw, r.images AS imagesRaw,"
            + " r.status, r.handler_id, r.handle_result, r.handle_action, r.handle_time,"
            + " r.accept_time, r.reporter_id, r.create_time,"
            + " ru.nickname AS reporterName, ru.avatar AS reporterAvatar,"
            + " CASE WHEN tu.id IS NULL THEN '已注销用户'"
            + "      WHEN tu.deleted = 1 THEN '已注销用户'"
            + "      ELSE tu.nickname END AS targetUserName,"
            + " CASE WHEN tu.id IS NULL OR tu.deleted = 1 THEN '账号已注销'"
            + "      WHEN tu.status = 1 THEN '已封禁'"
            + "      ELSE '正常' END AS targetUserStatus,"
            + " hu.nickname AS handlerName"
            + " FROM report r"
            + " LEFT JOIN sys_user ru ON ru.id = r.reporter_id"
            + " LEFT JOIN sys_user tu ON tu.id = r.target_user_id"
            + " LEFT JOIN sys_user hu ON hu.id = r.handler_id"
            + " WHERE r.deleted = 0"
            + "<if test='q.reportType != null'> AND r.report_type = #{q.reportType}</if>"
            + "<if test='q.status != null'> AND r.status = #{q.status}</if>"
            + "<if test='q.pendingOnly != null and q.pendingOnly'>"
            + "  AND r.status IN (0, 1)</if>"
            + "<if test='q.handlerId != null'> AND r.handler_id = #{q.handlerId}</if>"
            + "<if test='q.startTime != null'> AND r.create_time &gt;= #{q.startTime}</if>"
            + "<if test='q.endTime != null'> AND r.create_time &lt;= #{q.endTime}</if>"
            + "<if test='q.keyword != null and q.keyword != \"\"'>"
            + "  AND (ru.nickname LIKE CONCAT('%', #{q.keyword}, '%')"
            + "    OR tu.nickname LIKE CONCAT('%', #{q.keyword}, '%')"
            + "    OR r.content LIKE CONCAT('%', #{q.keyword}, '%'))"
            + "</if>"
            + " ORDER BY CASE r.status WHEN 0 THEN 0 WHEN 1 THEN 1 WHEN 2 THEN 2 ELSE 3 END,"
            + "          r.create_time DESC"
            + "</script>")
    IPage<ReportVO> selectAdminPage(IPage<ReportVO> page, @Param("q") ReportQueryDTO q);

    /**
     * 同一用户对同一对象的最近一次举报时间，用于 24 小时节流判定。
     * <p>
     * 走 idx_reporter_type_target 复合索引，只扫该用户对该对象的那几行。
     */
    @Select("SELECT create_time FROM report"
            + " WHERE deleted = 0 AND reporter_id = #{reporterId}"
            + "   AND report_type = #{reportType} AND target_id = #{targetId}"
            + " ORDER BY id DESC LIMIT 1")
    java.time.LocalDateTime selectLastReportTime(@Param("reporterId") Long reporterId,
                                                 @Param("reportType") Integer reportType,
                                                 @Param("targetId") Long targetId);

    /** 举报统计：按状态计数 */
    @Select("SELECT status, COUNT(*) AS cnt FROM report WHERE deleted = 0 GROUP BY status")
    java.util.List<Map<String, Object>> countGroupByStatus();

    /** 今日新增 / 今日已处理，用于工作台顶部卡片 */
    @Select("SELECT"
            + " SUM(CASE WHEN create_time >= CURDATE() THEN 1 ELSE 0 END) AS todayCount,"
            + " SUM(CASE WHEN handle_time >= CURDATE() AND status IN (2, 3) THEN 1 ELSE 0 END) AS todayHandledCount"
            + " FROM report WHERE deleted = 0")
    Map<String, Object> countToday();

    /**
     * 平均处理时长样本：已完成记录的 (handle_time − accept_time) 分钟数。
     * <p>
     * 起点必须是 accept_time 而不是 create_time：
     * 举报在队列里躺 3 天才被接单很常见，若从提交时间起算，
     * 「平均处理时长 72 小时」会被解读为客服效率极差，
     * 但那是排队时间不是处理时间，指标会反过来逼客服去抢单而不是把单做干净。
     * <p>
     * 防御性写法：accept_time 为 NULL 的历史数据（未接单直接被判定的）
     * 用 IFNULL 退回 create_time，保证不会因为 NULL 把整行从 AVG 里剔除，
     * 那样会让统计悄悄少算样本量。
     */
    @Select("SELECT TIMESTAMPDIFF(MINUTE, IFNULL(accept_time, create_time), handle_time) AS minutes"
            + " FROM report WHERE deleted = 0 AND handle_time IS NOT NULL AND status IN (2, 3)")
    java.util.List<Integer> selectHandleDurations();
}