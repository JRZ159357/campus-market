// src/api/review.js
import request from '@/utils/request'

/**
 * 管理端评价列表（多条件筛选）
 * @param {{
 *   pageNum:number, pageSize:number, reviewType?:number,
 *   reviewerKeyword?:string, revieweeKeyword?:string, productKeyword?:string,
 *   minStars?:number, maxStars?:number, hasImage?:boolean, hasFollow?:boolean,
 *   keyword?:string, startTime?:string, endTime?:string, orderNo?:string
 * }} params
 */
export const pageReviews = params => request.get('/review/admin/page', { params })

/** 删除违规评价（仅管理员，不回退信誉分） */
export const deleteReview = id => request.delete(`/review/${id}`)

/** 商品评价统计（平均分 / 好评数 / 各星级分布） */
export const getProductReviewStat = productId => request.get(`/review/product/${productId}/stat`)
