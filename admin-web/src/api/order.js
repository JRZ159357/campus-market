// 文件路径: admin-web/src/api/order.js
import request from '@/utils/request'

/**
 * 全部订单（分页）
 * @param {{pageNum:number,pageSize:number,status?:number,orderNo?:string,
 *          buyerKeyword?:string,sellerKeyword?:string,startTime?:string,endTime?:string}} params
 */
export const pageOrders = params => request.get('/order/admin/page', { params })

/** 各状态订单数，用于标签页角标 */
export const getOrderCount = () => request.get('/order/admin/count')

/** 订单详情（含操作日志） */
export const getOrderDetail = orderNo => request.get(`/order/admin/${orderNo}`)

/** 管理员强制取消 */
export const adminCancelOrder = (orderNo, reason) =>
  request.post(`/order/${orderNo}/admin-cancel`, { reason })

/** 管理员强制退款 */
export const adminRefundOrder = (orderNo, reason) =>
  request.post(`/order/${orderNo}/admin-refund`, { reason })
