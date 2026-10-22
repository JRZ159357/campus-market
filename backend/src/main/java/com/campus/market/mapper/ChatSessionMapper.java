// 文件路径: src/main/java/com/campus/market/mapper/ChatSessionMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 会话 Mapper。
 * <p>
 * 会话列表本质是「我作为买家」与「我作为卖家」两拨数据的并集。如果用
 * {@code WHERE buyer_id = ? OR seller_id = ?} 一次查完，MySQL 只能走索引合并甚至全表扫描；
 * 这里拆成两条各自命中 idx_buyer_list / idx_seller_list 的子查询再 UNION ALL，
 * 每条子查询都是索引范围扫描。数据量小，代价可以接受，换来的是执行计划稳定。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 我的会话列表（买家侧 + 卖家侧并集），按最后消息时间倒序。
     *
     * @param userId 当前用户
     * @param offset 偏移量
     * @param size   每页条数
     */
    @Select("SELECT * FROM ("
            + "  SELECT * FROM chat_session WHERE buyer_id = #{userId} AND buyer_deleted = 0"
            + "  UNION ALL"
            + "  SELECT * FROM chat_session WHERE seller_id = #{userId} AND seller_deleted = 0"
            + ") t ORDER BY t.last_time DESC, t.id DESC LIMIT #{offset}, #{size}")
    List<ChatSession> selectMySessions(@Param("userId") Long userId,
                                       @Param("offset") long offset,
                                       @Param("size") long size);

    /**
     * 我的会话总数，与 selectMySessions 的 WHERE 条件保持一致。
     */
    @Select("SELECT (SELECT COUNT(*) FROM chat_session WHERE buyer_id = #{userId} AND buyer_deleted = 0)"
            + " + (SELECT COUNT(*) FROM chat_session WHERE seller_id = #{userId} AND seller_deleted = 0)")
    long countMySessions(@Param("userId") Long userId);

    /**
     * 客服工作台：全平台会话分页，可按买家/卖家昵称模糊匹配。
     * 昵称条件由 Service 层先查用户表拿到 ID 集合再传进来，避免 JOIN 大表。
     */
    @Select("<script>"
            + "SELECT * FROM chat_session WHERE 1 = 1"
            + "<if test='userIds != null and userIds.size() > 0'>"
            + "  AND (buyer_id IN <foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>"
            + "       OR seller_id IN <foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>)"
            + "</if>"
            + " ORDER BY last_time DESC, id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<ChatSession> selectAllSessions(@Param("userIds") List<Long> userIds,
                                        @Param("offset") long offset,
                                        @Param("size") long size);

    /**
     * 客服工作台会话总数。
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM chat_session WHERE 1 = 1"
            + "<if test='userIds != null and userIds.size() > 0'>"
            + "  AND (buyer_id IN <foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>"
            + "       OR seller_id IN <foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>)"
            + "</if>"
            + "</script>")
    long countAllSessions(@Param("userIds") List<Long> userIds);

    /**
     * 未读会话数（有未读消息的会话个数），用于 tabBar 角标。
     */
    @Select("SELECT COUNT(*) FROM chat_session WHERE (buyer_id = #{userId} AND buyer_unread > 0)"
            + " OR (seller_id = #{userId} AND seller_unread > 0)")
    long countUnreadSessions(@Param("userId") Long userId);

    /**
     * 我的未读消息总数 = 买家侧未读之和 + 卖家侧未读之和。
     * 两侧分别走 idx_buyer_list / idx_seller_list，避免 OR 条件退化。
     */
    @Select("SELECT IFNULL((SELECT SUM(buyer_unread) FROM chat_session"
            + " WHERE buyer_id = #{userId} AND buyer_deleted = 0), 0)"
            + " + IFNULL((SELECT SUM(seller_unread) FROM chat_session"
            + " WHERE seller_id = #{userId} AND seller_deleted = 0), 0)")
    long sumMyUnread(@Param("userId") Long userId);
}
