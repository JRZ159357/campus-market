// 文件路径: src/main/java/com/campus/market/mapper/ProductMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 商品 Mapper。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 按分类统计未删除商品数量，供管理端分类列表展示。
     * 返回字段：category_id / cnt
     */
    @Select("SELECT category_id AS categoryId, COUNT(*) AS cnt FROM product WHERE deleted = 0 GROUP BY category_id")
    List<Map<String, Object>> countByCategory();

    /**
     * 统计卖家占用额度的商品数（待审核 + 已上架）。
     */
    @Select("SELECT COUNT(*) FROM product WHERE deleted = 0 AND seller_id = #{sellerId} AND status IN (0, 1)")
    long countOccupied(@Param("sellerId") Long sellerId);

    /**
     * 浏览数累加，供 Redis 增量定时回写使用。
     */
    @Update("UPDATE product SET view_count = view_count + #{delta} WHERE id = #{productId} AND deleted = 0")
    int addViewCount(@Param("productId") Long productId, @Param("delta") int delta);

    /**
     * 收藏数增减。与 favorite 表写入放在同一事务内，GREATEST 兜底防止负数。
     */
    @Update("UPDATE product SET favorite_count = GREATEST(favorite_count + #{delta}, 0) "
            + "WHERE id = #{productId} AND deleted = 0")
    int addFavoriteCount(@Param("productId") Long productId, @Param("delta") int delta);

    /**
     * 批量统计多个卖家的在售商品数，供关注/粉丝列表展示，避免逐个 COUNT。
     * 返回字段：sellerId / cnt
     */
    @Select("<script>"
            + "SELECT seller_id AS sellerId, COUNT(*) AS cnt FROM product "
            + "WHERE deleted = 0 AND status = 1 AND seller_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY seller_id"
            + "</script>")
    List<Map<String, Object>> countOnSaleBySellers(@Param("ids") List<Long> ids);

    /**
     * 上架 → 已售出的状态 CAS，是防超卖的关键。
     * <p>
     * 返回 1 表示本线程抢到了商品；返回 0 说明商品已被其他订单买走
     * （或已下架 / 已删除），调用方必须回滚事务并提示"手慢了"。
     * 用一条 UPDATE 完成"判断 + 修改"，天然是原子的，
     * 比"先 SELECT status 再 UPDATE"少一次竞态窗口。
     */
    @Update("UPDATE product SET status = 3 WHERE id = #{productId} AND status = 1 AND deleted = 0")
    int markSold(@Param("productId") Long productId);

    /**
     * 已售出 → 已上架的恢复，用于取消订单 / 退款完成。
     * 同样带状态条件，避免把管理员后来强制下架的商品又顶回上架。
     */
    @Update("UPDATE product SET status = 1 WHERE id = #{productId} AND status = 3 AND deleted = 0")
    int restoreOnSale(@Param("productId") Long productId);

    /**
     * 某卖家各状态的商品数。个人中心与卖家主页都要「发布数 / 在售数 / 已售数」，
     * 一次 GROUP BY 出全部数字，比分别 COUNT 三次少两次往返。
     * 返回字段：status / cnt
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM product "
            + "WHERE deleted = 0 AND seller_id = #{sellerId} GROUP BY status")
    List<Map<String, Object>> countGroupByStatus(@Param("sellerId") Long sellerId);

    /**
     * 某卖家的商品数（不限状态）。管理端用户详情要显示「发布商品总数」。
     */
    @Select("SELECT COUNT(*) FROM product WHERE deleted = 0 AND seller_id = #{sellerId}")
    long countBySeller(@Param("sellerId") Long sellerId);

    /**
     * 某卖家最近发布的商品，供管理端用户详情弹窗使用。
     */
    @Select("SELECT * FROM product WHERE deleted = 0 AND seller_id = #{sellerId} "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<Product> selectRecentBySeller(@Param("sellerId") Long sellerId, @Param("limit") int limit);
}
