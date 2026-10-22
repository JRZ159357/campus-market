// 文件路径: src/main/java/com/campus/market/service/FollowService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.entity.Follow;
import com.campus.market.vo.FollowStateVO;
import com.campus.market.vo.UserFollowVO;

/**
 * 关注业务接口。
 */
public interface FollowService extends IService<Follow> {

    /**
     * 关注 / 取消关注（切换语义），不允许关注自己。
     *
     * @return 本次操作后的关注状态与被关注者最新粉丝数
     */
    FollowStateVO toggle(Long followerId, Long followingId);

    /**
     * 当前用户是否已关注目标用户。
     */
    boolean isFollowing(Long followerId, Long followingId);

    /**
     * 我关注的人（分页，含在售商品数）。
     */
    IPage<UserFollowVO> following(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 关注我的人，即粉丝列表（分页，含在售商品数与"是否已回关"）。
     */
    IPage<UserFollowVO> followers(Long userId, Integer pageNum, Integer pageSize);
}
