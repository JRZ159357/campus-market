// 文件路径: miniprogram/api/review.js
const { request } = require('../utils/request')

/**
 * 提交评价。参数：orderId, reviewType, 各维度星级, content, images, isAnonymous
 * 成功后返回评价详情（含被评价人最新的信誉等级）。
 */
const submitReview = data => request({ url: '/api/review', method: 'POST', data })

/** 追评（评价人本人、交易完成 7 天内、未追评过） */
const followReview = data => request({ url: '/api/review/follow', method: 'POST', data })

/** 卖家回复买家评价 */
const replyReview = data => request({ url: '/api/review/reply', method: 'POST', data })

/** 单条评价详情（追评页展示原评价内容） */
const getReviewDetail = reviewId => request({ url: '/api/review/' + reviewId })

/**
 * 商品评价列表。params: pageNum, pageSize, minStars, maxStars, hasImage
 * 匿名评价的昵称已由后端脱敏，前端直接展示即可。
 */
const pageProductReviews = (productId, params) =>
  request({ url: '/api/review/product/' + productId, data: params })

/** 商品评价统计（平均分 / 好评数 / 各星级分布） */
const getProductReviewStat = productId => request({ url: '/api/review/product/' + productId + '/stat' })

/** 某用户收到的评价列表 */
const pageUserReviews = (userId, params) =>
  request({ url: '/api/review/user/' + userId, data: params })

/** 订单评价详情（买卖双方 + 当前用户能否评价/追评） */
const getOrderReviews = orderId => request({ url: '/api/review/order/' + orderId })

/** 我待评价的订单列表 */
const pagePendingReviews = params => request({ url: '/api/review/my/pending', data: params })

/** 我待评价的订单数（个人中心角标） */
const getPendingCount = () => request({ url: '/api/review/my/pending/count' })

module.exports = {
  submitReview,
  followReview,
  replyReview,
  getReviewDetail,
  pageProductReviews,
  getProductReviewStat,
  pageUserReviews,
  getOrderReviews,
  pagePendingReviews,
  getPendingCount
}
