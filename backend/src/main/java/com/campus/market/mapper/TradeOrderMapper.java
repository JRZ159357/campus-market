// 文件路径: src/main/java/com/campus/market/mapper/TradeOrderMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 订单 Mapper。
 * 状态流转本身用 MyBatis-Plus 的条件更新（where status = 原状态）实现 CAS，
 * 这里只放统计类查询，避免 SQL 分散在两处。
 */
@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    /**
     * 扫描超时未付款订单ID。走 (status, pay_deadline) 组合索引，
     * 一次最多取 limit 条，避免单次事务过大。
     */
    @Select("SELECT id FROM trade_order WHERE deleted = 0 AND status = 0 "
            + "AND pay_deadline < NOW() ORDER BY pay_deadline ASC LIMIT #{limit}")
    List<Long> selectTimeoutIds(@Param("limit") int limit);

    /**
     * 按状态统计买家订单数，供个人中心角标。返回字段：status / cnt
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM trade_order "
            + "WHERE deleted = 0 AND buyer_id = #{buyerId} GROUP BY status")
    List<Map<String, Object>> countByBuyer(@Param("buyerId") Long buyerId);

    /**
     * 按状态统计卖家订单数。返回字段：status / cnt
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM trade_order "
            + "WHERE deleted = 0 AND seller_id = #{sellerId} GROUP BY status")
    List<Map<String, Object>> countBySeller(@Param("sellerId") Long sellerId);

    /**
     * 管理端按状态统计全部订单数，供标签页角标。返回字段：status / cnt
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM trade_order WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countAllGroupByStatus();

    /**
     * 某用户参与的全部订单（买 + 卖），管理端用户详情用。返回字段：id / order_no /
     * buyer_id / seller_id / product_id / price / status / create_time
     */
    @Select("SELECT id, order_no, buyer_id, seller_id, product_id, price, status, create_time"
            + " FROM trade_order WHERE deleted = 0"
            + " AND (buyer_id = #{userId} OR seller_id = #{userId})"
            + " ORDER BY id DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 某用户参与订单的总笔数与累计成交金额。
     * 成交金额只统计已完成（status=3）与已退款（status=6）之外的正常成交单，
     * 退款中的（status=5）金额仍在流转中，计入会让「累计成交额」虚高。
     */
    @Select("SELECT COUNT(*) AS orderCount, COALESCE(SUM(price), 0) AS tradeAmount"
            + " FROM trade_order WHERE deleted = 0"
            + " AND (buyer_id = #{userId} OR seller_id = #{userId}) AND status = 3")
    Map<String, Object> statByUser(@Param("userId") Long userId);
}
