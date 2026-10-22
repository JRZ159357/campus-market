// 文件路径: src/main/java/com/campus/market/mapper/DashboardMapper.java
package com.campus.market.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘聚合 Mapper（模块 10）。
 * <p>
 * 这个 Mapper 与其它 Mapper 最大的不同是：它<b>不做单表 CRUD</b>，
 * 只提供「多源统计」查询。仪表盘的每个指标都要跨表汇总，
 * 如果把这些 SQL 塞进各个业务 Mapper，会出现同一个统计口径在两处各写一遍、
 * 数字对不上的经典问题；集中在这里，一处定义、一处复用。
 * <p>
 * 三条性能约定：
 * <ol>
 *   <li><b>聚合下推到数据库</b>：所有 COUNT / SUM / GROUP BY 都在 SQL 里完成，
 *       绝不把整表数据拉到 Java 再统计；</li>
 *   <li><b>排行榜用 ORDER BY + LIMIT</b>：TOP10 / TOP7 直接由数据库排序截断，
 *       Java 端拿到的就是最终结果，不做全量排序；</li>
 *   <li><b>趋势用按天 GROUP BY</b>：数据库只返回「有数据的那几天」，
 *       缺失的日期由应用层补零（见 DashboardServiceImpl#trend），
 *       这样既不生成无用行，也让「哪几天没有数据」在应用层可控。</li>
 * </ol>
 * 所有手写 SQL 都显式带 {@code deleted = 0}：MyBatis-Plus 的逻辑删除
 * 只对内置 CRUD 生效，注解里的手写 SQL 框架不会自动追加条件。
 */
@Mapper
public interface DashboardMapper {

    /**
     * 核心指标：一次请求合并 5 张表的统计。
     * <p>
     * 之所以用一条 SQL 而不是五条：管理端首页打开就要同时展示十几个数字，
     * 五条 SQL 就是五次数据库往返，而五条语句彼此没有依赖（都是纯聚合），
     * 完全可以在一条语句里用标量子查询并列算出。
     * <p>
     * 口径说明（答辩常问）：
     * <ul>
     *   <li><b>gmvTotal / gmvToday</b> 只算 status=3（已完成）的订单金额，
     *       与订单模块 {@code TradeOrderMapper#statByUser} 的口径保持一致；
     *       退款中的订单金额仍在流转中，计入会让成交额虚高；</li>
     *   <li><b>gmvToday</b> 按 finish_time（完成时间）而非 create_time 归集，
     *       否则今天下单、明天完成的订单会被算进「今天成交」；</li>
     *   <li><b>pendingReports</b> 取 status IN (0,1)，即「尚未结案」的举报
     *       （待处理 + 处理中），这才是客服真正待办的工作量；</li>
     *   <li><b>activeSessions</b> 取近 24 小时内有消息往来的聊天会话数。
     *       没有用 WebSocket 在线连接数：那是个纯内存指标，
     *       一旦放进 5 分钟缓存就会变成「5 分钟前的在线数」，反而误导。</li>
     * </ul>
     * 环比（近 7 日 vs 前 7 日）需要的对比期数据也在这一条里算出，
     * 避免为了拿「上上周的数字」再发一轮请求。
     */
    @Select("SELECT"
            + " (SELECT COUNT(*) FROM sys_user WHERE deleted = 0) AS totalUsers,"
            + " (SELECT COUNT(*) FROM sys_user WHERE deleted = 0"
            + "    AND create_time >= CURDATE()) AS newUsersToday,"
            + " (SELECT COUNT(*) FROM sys_user WHERE deleted = 0"
            + "    AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)) AS newUsersWeek,"
            + " (SELECT COUNT(*) FROM sys_user WHERE deleted = 0"
            + "    AND create_time >= DATE_SUB(CURDATE(), INTERVAL 13 DAY)"
            + "    AND create_time < DATE_SUB(CURDATE(), INTERVAL 6 DAY)) AS newUsersPrevWeek,"
            + " (SELECT COUNT(*) FROM product WHERE deleted = 0) AS totalProducts,"
            + " (SELECT COUNT(*) FROM product WHERE deleted = 0 AND status = 1) AS onSaleProducts,"
            + " (SELECT COUNT(*) FROM product WHERE deleted = 0 AND status = 0) AS pendingAuditProducts,"
            + " (SELECT COUNT(*) FROM product WHERE deleted = 0"
            + "    AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)) AS newProductsWeek,"
            + " (SELECT COUNT(*) FROM product WHERE deleted = 0"
            + "    AND create_time >= DATE_SUB(CURDATE(), INTERVAL 13 DAY)"
            + "    AND create_time < DATE_SUB(CURDATE(), INTERVAL 6 DAY)) AS newProductsPrevWeek,"
            + " (SELECT COUNT(*) FROM trade_order WHERE deleted = 0) AS totalOrders,"
            + " (SELECT COUNT(*) FROM trade_order WHERE deleted = 0 AND status = 3) AS completedOrders,"
            + " (SELECT COUNT(*) FROM trade_order WHERE deleted = 0 AND status = 5) AS pendingRefunds,"
            + " (SELECT COUNT(*) FROM trade_order WHERE deleted = 0"
            + "    AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)) AS newOrdersWeek,"
            + " (SELECT COUNT(*) FROM trade_order WHERE deleted = 0"
            + "    AND create_time >= DATE_SUB(CURDATE(), INTERVAL 13 DAY)"
            + "    AND create_time < DATE_SUB(CURDATE(), INTERVAL 6 DAY)) AS newOrdersPrevWeek,"
            + " (SELECT COALESCE(SUM(price), 0) FROM trade_order"
            + "    WHERE deleted = 0 AND status = 3) AS gmvTotal,"
            + " (SELECT COALESCE(SUM(price), 0) FROM trade_order"
            + "    WHERE deleted = 0 AND status = 3 AND finish_time >= CURDATE()) AS gmvToday,"
            + " (SELECT COALESCE(SUM(price), 0) FROM trade_order"
            + "    WHERE deleted = 0 AND status = 3"
            + "    AND finish_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)) AS gmvWeek,"
            + " (SELECT COALESCE(SUM(price), 0) FROM trade_order"
            + "    WHERE deleted = 0 AND status = 3"
            + "    AND finish_time >= DATE_SUB(CURDATE(), INTERVAL 13 DAY)"
            + "    AND finish_time < DATE_SUB(CURDATE(), INTERVAL 6 DAY)) AS gmvPrevWeek,"
            + " (SELECT COUNT(*) FROM report WHERE deleted = 0 AND status IN (0, 1)) AS pendingReports,"
            + " (SELECT COUNT(*) FROM report WHERE deleted = 0"
            + "    AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)) AS newReportsWeek,"
            + " (SELECT COUNT(*) FROM report WHERE deleted = 0"
            + "    AND create_time >= DATE_SUB(CURDATE(), INTERVAL 13 DAY)"
            + "    AND create_time < DATE_SUB(CURDATE(), INTERVAL 6 DAY)) AS newReportsPrevWeek,"
            + " (SELECT COUNT(*) FROM chat_session"
            + "    WHERE last_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)) AS activeSessions,"
            + " (SELECT COALESCE(ROUND(AVG(credit_score), 1), 0) FROM sys_user"
            + "    WHERE deleted = 0) AS avgCreditScore"
            + " FROM DUAL")
    Map<String, Object> selectSummary();

    /**
     * 趋势数据：把 5 个维度的按天统计 UNION 成一张窄表返回。
     * <p>
     * 一次请求返回 {@code (metric, date, value)} 三列的宽结果集，
     * 应用层再按 metric 拆开、横向拼成「每天一行」的数组。
     * 比起写 5 条 GROUP BY 再在 Java 里合并，UNION 的好处是
     * 只发一条 SQL、只做一次结果集传输。
     * <p>
     * 成交额按 finish_time 归集（当天完成的交易算当天），
     * 其余四项按 create_time 归集（当天发生的行为算当天）。
     * <p>
     * 只返回有数据的日期，缺失日期由应用层补零——
     * 这也是「趋势查询用按天 GROUP BY，应用层补全无数据的日期」的实现方式。
     * <p>
     * <b>GROUP BY 里必须写与 SELECT 完全相同的表达式</b>：
     * MySQL 8 默认开启 {@code only_full_group_by}，
     * {@code SELECT DATE_FORMAT(create_time,...) GROUP BY DATE(create_time)}
     * 会直接报「Expression #2 of SELECT list is not in GROUP BY clause」。
     * 也不能写 {@code GROUP BY dt}——MySQL 不允许在 GROUP BY 里引用 SELECT 的别名
     * （Oracle / PostgreSQL 允许，跨库迁移时这里最容易踩坑）。
     *
     * @param since 统计起始时间（含）
     */
    @Select("SELECT 'newUsers' AS metric, DATE_FORMAT(create_time, '%Y-%m-%d') AS dt, COUNT(*) AS val"
            + " FROM sys_user WHERE deleted = 0 AND create_time >= #{since}"
            + " GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')"
            + " UNION ALL"
            + " SELECT 'newProducts', DATE_FORMAT(create_time, '%Y-%m-%d'), COUNT(*)"
            + " FROM product WHERE deleted = 0 AND create_time >= #{since}"
            + " GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')"
            + " UNION ALL"
            + " SELECT 'orders', DATE_FORMAT(create_time, '%Y-%m-%d'), COUNT(*)"
            + " FROM trade_order WHERE deleted = 0 AND create_time >= #{since}"
            + " GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')"
            + " UNION ALL"
            + " SELECT 'gmv', DATE_FORMAT(finish_time, '%Y-%m-%d'), COALESCE(SUM(price), 0)"
            + " FROM trade_order WHERE deleted = 0 AND status = 3 AND finish_time >= #{since}"
            + " GROUP BY DATE_FORMAT(finish_time, '%Y-%m-%d')"
            + " UNION ALL"
            + " SELECT 'reports', DATE_FORMAT(create_time, '%Y-%m-%d'), COUNT(*)"
            + " FROM report WHERE deleted = 0 AND create_time >= #{since}"
            + " GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')")
    List<Map<String, Object>> selectTrend(@Param("since") LocalDateTime since);

    /**
     * 用户角色分布：按角色分组计数。
     * <p>
     * 只统计未删除账号。角色的中文名在应用层用 RoleConst 映射，
     * 不在 SQL 里写 CASE WHEN——角色码到中文名的映射只有一处定义，
     * 分散到 SQL 里必然出现「某处忘了翻译」。
     */
    @Select("SELECT role AS name, COUNT(*) AS val FROM sys_user"
            + " WHERE deleted = 0 GROUP BY role")
    List<Map<String, Object>> selectUserRoleDist();

    /**
     * 商品分类分布：按分类分组计数。
     * <p>
     * 用 JOIN 而不是「先查分类再逐个 COUNT」：后者是 N+1 查询。
     * 分类被删除但商品还在（历史数据）时，这些商品不计入任何分类，
     * 因此各分类之和可能小于商品总数——这比把它们算进「已删除分类」更诚实。
     */
    @Select("SELECT c.name AS name, COUNT(p.id) AS val"
            + " FROM product p JOIN category c ON c.id = p.category_id AND c.deleted = 0"
            + " WHERE p.deleted = 0 GROUP BY p.category_id, c.name ORDER BY val DESC")
    List<Map<String, Object>> selectProductCategoryDist();

    /**
     * 商品状态分布：待审核 / 已上架 / 已下架 / 已售出 / 违规下架 各多少。
     */
    @Select("SELECT status AS name, COUNT(*) AS val FROM product"
            + " WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> selectProductStatusDist();

    /**
     * 订单状态分布：7 个状态各多少笔。
     */
    @Select("SELECT status AS name, COUNT(*) AS val FROM trade_order"
            + " WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> selectOrderStatusDist();

    /**
     * 信誉分区间分布：0-59 / 60-79 / 80-99 / 100-119 / 120-150。
     * <p>
     * 用一条 SQL 里的 5 个 SUM(CASE WHEN ...) 一次算出全部区间，
     * 而不是 5 条区间查询——区间划分是固定的 5 档，写成 5 条 SQL 只会带来
     * 5 次往返和「改档位要改 5 处」的维护负担。
     * <p>
     * 返回列名即区间名（bucket0..bucket4），映射关系写在应用层常量里。
     */
    @Select("SELECT"
            + " COALESCE(SUM(CASE WHEN credit_score BETWEEN 0 AND 59 THEN 1 ELSE 0 END), 0) AS bucket0,"
            + " COALESCE(SUM(CASE WHEN credit_score BETWEEN 60 AND 79 THEN 1 ELSE 0 END), 0) AS bucket1,"
            + " COALESCE(SUM(CASE WHEN credit_score BETWEEN 80 AND 99 THEN 1 ELSE 0 END), 0) AS bucket2,"
            + " COALESCE(SUM(CASE WHEN credit_score BETWEEN 100 AND 119 THEN 1 ELSE 0 END), 0) AS bucket3,"
            + " COALESCE(SUM(CASE WHEN credit_score BETWEEN 120 AND 150 THEN 1 ELSE 0 END), 0) AS bucket4"
            + " FROM sys_user WHERE deleted = 0")
    Map<String, Object> selectCreditScoreDist();

    /**
     * 评价星级分布：1 星到 5 星各多少条。
     * <p>
     * 只统计买家评价卖家（review_type=1）：卖家评买家是另一个方向的评价，
     * 混进来会让商品的口碑分被「卖家对买家付款速度的评分」污染。
     */
    @Select("SELECT stars AS name, COUNT(*) AS val FROM review"
            + " WHERE deleted = 0 AND review_type = 1 GROUP BY stars")
    List<Map<String, Object>> selectReviewStarDist();

    /**
     * 卖家销量榜 TOP10。
     * <p>
     * 商品数与成交数据各自先在子查询里聚合，再 JOIN 到用户表，
     * 而不是「JOIN 后 GROUP BY」——后者会让商品表和订单表相乘，
     * 商品多的卖家成交额被重复累加，是典型的 JOIN 扇出错误。
     * <p>
     * 只取普通用户与认证学生：管理员与客服不参与交易，
     * 混进销量榜会让榜单失真。
     */
    @Select("SELECT u.id AS sellerId, u.nickname AS nickname, u.avatar AS avatar,"
            + " u.credit_score AS creditScore,"
            + " (SELECT COUNT(*) FROM product p WHERE p.seller_id = u.id AND p.deleted = 0) AS productCount,"
            + " COALESCE(o.soldCount, 0) AS soldCount,"
            + " COALESCE(o.amount, 0) AS amount"
            + " FROM sys_user u"
            + " LEFT JOIN ("
            + "   SELECT seller_id, COUNT(*) AS soldCount, SUM(price) AS amount"
            + "   FROM trade_order WHERE deleted = 0 AND status = 3 GROUP BY seller_id"
            + " ) o ON o.seller_id = u.id"
            + " WHERE u.deleted = 0 AND u.role IN ('user', 'student')"
            + " ORDER BY amount DESC, soldCount DESC, u.id ASC LIMIT 10")
    List<Map<String, Object>> selectTopSellers();

    /**
     * 热门商品 TOP10：按浏览量排序。
     * <p>
     * 只取在售（1）与已售出（3）的商品：待审核的商品还没对外展示，
     * 下架/违规下架的商品也不该出现在「热门」榜单里。
     */
    @Select("SELECT p.id AS productId, p.title AS title, p.price AS price,"
            + " p.view_count AS viewCount, p.favorite_count AS favoriteCount,"
            + " (SELECT pi.image_url FROM product_image pi"
            + "   WHERE pi.product_id = p.id AND pi.deleted = 0"
            + "   ORDER BY pi.sort ASC, pi.id ASC LIMIT 1) AS cover"
            + " FROM product p"
            + " WHERE p.deleted = 0 AND p.status IN (1, 3)"
            + " ORDER BY p.view_count DESC, p.favorite_count DESC, p.id ASC LIMIT 10")
    List<Map<String, Object>> selectTopProducts();

    /**
     * 热门分类 TOP7：按分类下商品的总浏览量排序。
     * <p>
     * 用 LEFT JOIN 而不是 INNER JOIN：浏览量为 0 的分类也应该出现，
     * 否则「刚建好还没人逛的新分类」会整个从榜单上消失，
     * 运营看不到自己新建的分类。
     */
    @Select("SELECT c.id AS categoryId, c.name AS name,"
            + " COUNT(p.id) AS productCount,"
            + " COALESCE(SUM(p.view_count), 0) AS viewCount"
            + " FROM category c"
            + " LEFT JOIN product p ON p.category_id = c.id AND p.deleted = 0"
            + " WHERE c.deleted = 0"
            + " GROUP BY c.id, c.name"
            + " ORDER BY viewCount DESC, productCount DESC, c.id ASC LIMIT 7")
    List<Map<String, Object>> selectTopCategories();

    /**
     * 最近操作日志（最近 20 条）。
     * <p>
     * JOIN 用户表取头像：日志表只冗余了昵称，没有头像，
     * 而管理端「最近动态」要显示操作人头像 + 昵称。
     * 昵称优先取日志里的冗余值——用户改过昵称后，日志要保留当时的名称，
     * 这正是冗余昵称存在的意义。
     */
    @Select("SELECT l.id AS id, l.operator_id AS operatorId, l.operator_name AS operatorName,"
            + " l.operator_role AS operatorRole, l.module AS module, l.action AS action,"
            + " l.target_id AS targetId, l.detail AS detail, l.create_time AS createTime,"
            + " u.avatar AS operatorAvatar"
            + " FROM sys_operation_log l"
            + " LEFT JOIN sys_user u ON u.id = l.operator_id"
            + " ORDER BY l.id DESC LIMIT 20")
    List<Map<String, Object>> selectRecentLogs();
}
