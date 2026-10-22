// 文件路径: src/main/java/com/campus/market/mapper/ReviewMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.dto.ReviewQueryDTO;
import com.campus.market.entity.Review;
import com.campus.market.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 评价 Mapper。
 * <p>
 * 列表查询统一用「动态 SQL + Page 参数」：筛选条件最多七八个（星级区间、有图、
 * 有追评、关键词、时间范围…），在 Service 里用 LambdaQueryWrapper 拼也不是不行，
 * 但一旦需要「只查有图片的」这种对 JSON 列做判空的条件，Wrapper 就写不出来了，
 * 索性全部下沉到 SQL，Service 只管组装条件。
 * <p>
 * 所有自定义 SQL 都要显式写 {@code deleted = 0}：MyBatis-Plus 的逻辑删除
 * 只对内置 CRUD 生效，注解里的手写 SQL 框架不会自动追加条件。
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 评价列表通用分页（商品评价 / 用户收到的评价 / 管理端全量列表共用）。
     * <p>
     * 一个方法覆盖三种场景，靠 DTO 里传哪个字段来区分：
     * 商品页传 productId，个人主页传 revieweeId，管理端可以任意组合。
     * 这样三处列表的排序、过滤语义天然一致，不会出现「商品页按时间倒序、
     * 管理端忘了排序」这类漂移。
     */
    @Select("<script>"
            + "SELECT * FROM review WHERE deleted = 0"
            + "<if test='q.productId != null'> AND product_id = #{q.productId}</if>"
            + "<if test='q.productIds != null and q.productIds.size() > 0'>"
            + "  AND product_id IN <foreach collection='q.productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</if>"
            + "<if test='q.revieweeId != null'> AND reviewee_id = #{q.revieweeId}</if>"
            + "<if test='q.revieweeIds != null and q.revieweeIds.size() > 0'>"
            + "  AND reviewee_id IN <foreach collection='q.revieweeIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>"
            + "</if>"
            + "<if test='q.reviewerId != null'> AND reviewer_id = #{q.reviewerId}</if>"
            + "<if test='q.reviewerIds != null and q.reviewerIds.size() > 0'>"
            + "  AND reviewer_id IN <foreach collection='q.reviewerIds' item='rid' open='(' separator=',' close=')'>#{rid}</foreach>"
            + "</if>"
            + "<if test='q.reviewType != null'> AND review_type = #{q.reviewType}</if>"
            + "<if test='q.orderNo != null and q.orderNo != \"\"'> AND order_no LIKE CONCAT('%', #{q.orderNo}, '%')</if>"
            + "<if test='q.minStars != null'> AND stars &gt;= #{q.minStars}</if>"
            + "<if test='q.maxStars != null'> AND stars &lt;= #{q.maxStars}</if>"
            + "<if test='q.hasImage != null and q.hasImage'> AND images IS NOT NULL AND images &lt;&gt; '[]' AND images &lt;&gt; ''</if>"
            + "<if test='q.hasFollow != null and q.hasFollow'> AND follow_content IS NOT NULL AND follow_content &lt;&gt; ''</if>"
            + "<if test='q.hasReply != null and q.hasReply'> AND reply_content IS NOT NULL AND reply_content &lt;&gt; ''</if>"
            + "<if test='q.keyword != null and q.keyword != \"\"'>"
            + "  AND (content LIKE CONCAT('%', #{q.keyword}, '%')"
            + "       OR follow_content LIKE CONCAT('%', #{q.keyword}, '%')"
            + "       OR reply_content LIKE CONCAT('%', #{q.keyword}, '%'))"
            + "</if>"
            + "<if test='q.startTime != null'> AND create_time &gt;= #{q.startTime}</if>"
            + "<if test='q.endTime != null'> AND create_time &lt;= #{q.endTime}</if>"
            + " ORDER BY create_time DESC, id DESC"
            + "</script>")
    IPage<Review> selectPageByQuery(IPage<Review> page, @Param("q") ReviewQueryDTO query);

    /**
     * 按订单批量取评价（订单详情要同时展示买卖双方的评价，一次查完避免两次往返）。
     */
    @Select("<script>"
            + "SELECT * FROM review WHERE deleted = 0 AND order_id IN "
            + "<foreach collection='orderIds' item='oid' open='(' separator=',' close=')'>#{oid}</foreach>"
            + " ORDER BY id ASC"
            + "</script>")
    List<Review> selectByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * 我待评价的订单：已完成，且「我这一侧」还没有评价标记。
     * <p>
     * 直接查 trade_order 而不是从 review 反查：评价标记就冗余在订单上，
     * 用它做条件可以走 idx_buyer_status / idx_seller_status，
     * 不必先扫全量评价再排除。
     */
    @Select("SELECT * FROM trade_order WHERE deleted = 0 AND status = 3"
            + " AND ((buyer_id = #{userId} AND buyer_reviewed = 0)"
            + "   OR (seller_id = #{userId} AND seller_reviewed = 0))"
            + " ORDER BY finish_time DESC, id DESC")
    IPage<TradeOrder> selectPendingOrders(IPage<TradeOrder> page, @Param("userId") Long userId);

    /**
     * 我待评价的订单数（个人中心角标用）。
     */
    @Select("SELECT COUNT(*) FROM trade_order WHERE deleted = 0 AND status = 3"
            + " AND ((buyer_id = #{userId} AND buyer_reviewed = 0)"
            + "   OR (seller_id = #{userId} AND seller_reviewed = 0))")
    long countPendingOrders(@Param("userId") Long userId);

    /**
     * 某用户收到的评价统计，一次 SQL 出全部指标：
     * 总数、好评数（4-5 星）、中评（3 星）、差评（1-2 星）、各星级数量、平均星级、有图数。
     * <p>
     * 这些数字在信誉分详情页要同时展示，分多条 SQL 查会往返七八次；
     * 用 SUM(CASE WHEN ...) 一次算完，代价只是一次全索引扫描。
     */
    @Select("SELECT COUNT(*) AS total,"
            + " COALESCE(SUM(CASE WHEN stars >= 4 THEN 1 ELSE 0 END), 0) AS goodCount,"
            + " COALESCE(SUM(CASE WHEN stars = 3 THEN 1 ELSE 0 END), 0) AS midCount,"
            + " COALESCE(SUM(CASE WHEN stars <= 2 THEN 1 ELSE 0 END), 0) AS badCount,"
            + " COALESCE(SUM(CASE WHEN stars = 5 THEN 1 ELSE 0 END), 0) AS star5,"
            + " COALESCE(SUM(CASE WHEN stars = 4 THEN 1 ELSE 0 END), 0) AS star4,"
            + " COALESCE(SUM(CASE WHEN stars = 3 THEN 1 ELSE 0 END), 0) AS star3,"
            + " COALESCE(SUM(CASE WHEN stars = 2 THEN 1 ELSE 0 END), 0) AS star2,"
            + " COALESCE(SUM(CASE WHEN stars = 1 THEN 1 ELSE 0 END), 0) AS star1,"
            + " COALESCE(ROUND(AVG(stars), 1), 0) AS avgStars,"
            + " COALESCE(SUM(CASE WHEN images IS NOT NULL AND images <> '[]' AND images <> '' THEN 1 ELSE 0 END), 0) AS imageCount"
            + " FROM review WHERE deleted = 0 AND reviewee_id = #{userId}")
    Map<String, Object> selectStatOfUser(@Param("userId") Long userId);

    /**
     * 某商品的评价汇总（详情页展示「4.8 分 · 23 条评价」）。
     * <p>
     * 必须带 review_type = 1：一笔订单会产出两行评价（买家评卖家 + 卖家评买家），
     * 两行都带同一个 product_id。若不过滤方向，商品页的平均分会把
     * 「卖家对买家的付款速度打分」也算进去，分数直接失真。
     */
    @Select("SELECT COUNT(*) AS total, COALESCE(ROUND(AVG(stars), 1), 0) AS avgStars,"
            + " COALESCE(SUM(CASE WHEN stars >= 4 THEN 1 ELSE 0 END), 0) AS goodCount"
            + " FROM review WHERE deleted = 0 AND review_type = 1 AND product_id = #{productId}")
    Map<String, Object> selectStatOfProduct(@Param("productId") Long productId);

    /**
     * 判断某订单的某个方向是否已经评价过。
     * <p>
     * 提交评价前用它做一次明确的预检查，给出可读的错误提示（「你已经评价过这笔订单」）；
     * 真正的并发防线仍然是 uk_order_type 唯一索引 —— 预检查只是为了让
     * 99% 的重复提交走「友好提示」而不是「捕获唯一键冲突」。
     */
    @Select("SELECT COUNT(*) FROM review WHERE deleted = 0 AND order_id = #{orderId}"
            + " AND review_type = #{reviewType}")
    int countByOrderType(@Param("orderId") Long orderId, @Param("reviewType") Integer reviewType);

    /**
     * 某用户最近收到的评价，管理端用户详情用。
     * 返回字段与实体一致，直接映射成 Review 实体即可。
     */
    @Select("SELECT * FROM review WHERE deleted = 0 AND reviewee_id = #{userId}"
            + " ORDER BY id DESC LIMIT #{limit}")
    List<Review> selectRecentByReviewee(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 某用户收到的评价总数（不含已删除）。
     */
    @Select("SELECT COUNT(*) FROM review WHERE deleted = 0 AND reviewee_id = #{userId}")
    long countByReviewee(@Param("userId") Long userId);
}
