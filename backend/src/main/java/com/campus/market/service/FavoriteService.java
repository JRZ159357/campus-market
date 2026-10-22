// 文件路径: src/main/java/com/campus/market/service/FavoriteService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.entity.Favorite;
import com.campus.market.vo.FavoriteFeedVO;
import com.campus.market.vo.FavoriteStateVO;

/**
 * 收藏业务接口。
 */
public interface FavoriteService extends IService<Favorite> {

    /**
     * 收藏 / 取消收藏（切换语义）。
     *
     * @return 本次操作后的收藏状态与商品最新收藏数
     */
    FavoriteStateVO toggle(Long userId, Long productId);

    /**
     * 当前用户是否收藏了该商品。优先读 Redis 集合，O(1) 判断。
     */
    boolean isFavorited(Long userId, Long productId);

    /**
     * 我的收藏，游标分页（按收藏时间倒序）。
     *
     * @param cursor 上一页最后一条收藏记录ID，首页不传
     */
    FavoriteFeedVO my(Long userId, Long cursor, Integer size);
}
