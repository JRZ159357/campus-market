// 文件路径: src/main/java/com/campus/market/service/ReviewService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.ReviewFollowDTO;
import com.campus.market.dto.ReviewQueryDTO;
import com.campus.market.dto.ReviewReplyDTO;
import com.campus.market.dto.ReviewSubmitDTO;
import com.campus.market.entity.Review;
import com.campus.market.vo.ReviewOrderVO;
import com.campus.market.vo.ReviewPendingVO;
import com.campus.market.vo.ReviewVO;

/**
 * 评价服务。
 * <p>
 * 所有写操作都要求「操作者身份」参数，而不是从 DTO 里取：
 * 评价人必须是订单的买家或卖家、追评必须是评价人本人、回复必须是被评价的卖家，
 * 这三条越权校验只能在服务层做，绝不能相信前端传来的 reviewerId。
 */
public interface ReviewService extends IService<Review> {

    /** 提交评价（下单方向由订单角色决定），成功后立即给对方加/减信誉分 */
    ReviewVO submit(Long userId, ReviewSubmitDTO dto);

    /** 追评（评价人本人、未追评过、交易完成 7 天内） */
    ReviewVO follow(Long userId, ReviewFollowDTO dto);

    /** 卖家回复买家评价（仅被评价的卖家、只能回一次） */
    ReviewVO reply(Long userId, ReviewReplyDTO dto);

    /** 单条评价详情（追评页要展示原评价内容，删除违规评价后返回「评价不存在」） */
    ReviewVO detail(Long reviewId, Long viewerId);

    /** 商品评价列表（匿名已脱敏，按时间倒序） */
    IPage<ReviewVO> pageByProduct(Long productId, ReviewQueryDTO query);

    /** 某用户收到的评价列表（带被评价人的信誉等级标签） */
    IPage<ReviewVO> pageByUser(Long userId, ReviewQueryDTO query);

    /** 订单评价详情：买卖双方各一条，并给出当前查看者能否评价 / 追评 */
    ReviewOrderVO orderReviews(Long orderId, Long viewerId);

    /** 我待评价的订单列表 */
    IPage<ReviewPendingVO> pending(Long userId, Long pageNum, Long pageSize);

    /** 我待评价的订单数（个人中心角标） */
    long pendingCount(Long userId);

    /** 管理端评价列表（多条件筛选，保留真实评价人便于判断违规） */
    IPage<ReviewVO> adminPage(ReviewQueryDTO query);

    /** 管理员删除违规评价（逻辑删除，不回退信誉分，但留审计流水） */
    void adminDelete(Long reviewId, Long operatorId);
}
