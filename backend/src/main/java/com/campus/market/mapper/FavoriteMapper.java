// 文件路径: src/main/java/com/campus/market/mapper/FavoriteMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 收藏 Mapper。单表查询用 Wrapper，此处只维护需要手写 SQL 的两处。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 用户收藏的全部商品ID，用于一次性预热 Redis 集合缓存。
     */
    @Select("SELECT product_id FROM favorite WHERE user_id = #{userId}")
    List<Long> selectProductIds(@Param("userId") Long userId);
}
