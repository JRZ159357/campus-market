// 文件路径: miniprogram/api/order.js
const { request } = require('../utils/request')

/** 创建订单，返回 { orderId, orderNo, price, payDeadline } */
const createOrder = data => request({ url: '/api/order/create', method: 'POST', data })

/** 模拟支付 */
const payOrder = orderNo => request({ url: '/api/order/' + orderNo + '/pay', method: 'POST' })

/** 卖家发货 */
const shipOrder = orderNo => request({ url: '/api/order/' + orderNo + '/ship', method: 'POST' })

/** 买家确认收货 */
const receiveOrder = orderNo => request({ url: '/api/order/' + orderNo + '/receive', method: 'POST' })

/** 买家取消订单 */
const cancelOrder = (orderNo, reason) =>
  request({ url: '/api/order/' + orderNo + '/cancel', method: 'POST', data: { reason: reason || '' } })

/** 买家申请退款 */
const applyRefund = (orderNo, reason) =>
  request({ url: '/api/order/' + orderNo + '/apply-refund', method: 'POST', data: { reason } })

/** 买家撤销退款申请 */
const cancelRefund = orderNo => request({ url: '/api/order/' + orderNo + '/cancel-refund', method: 'POST' })

/** 卖家同意退款 */
const agreeRefund = orderNo => request({ url: '/api/order/' + orderNo + '/agree-refund', method: 'POST' })

/** 卖家拒绝退款 */
const rejectRefund = (orderNo, reason) =>
  request({ url: '/api/order/' + orderNo + '/reject-refund', method: 'POST', data: { reason } })

/** 订单详情 */
const getOrder = orderNo => request({ url: '/api/order/' + orderNo })

/**
 * 我买到的（分页）
 * @param {{pageNum?:number, pageSize?:number, status?:number}} params
 */
const buildPageQuery = (url, params) => {
  const query = []
  if (params && params.pageNum) query.push('pageNum=' + params.pageNum)
  if (params && params.pageSize) query.push('pageSize=' + params.pageSize)
  if (params && params.status !== null && params.status !== undefined && params.status !== '') {
    query.push('status=' + params.status)
  }
  return request({ url: url + (query.length ? '?' + query.join('&') : '') })
}

const pageMyOrders = params => buildPageQuery('/api/order/buyer/page', params)

/** 我卖出的（分页） */
const pageMySales = params => buildPageQuery('/api/order/seller/page', params)

/** 我买到的各状态订单数，用于个人中心角标 */
const getOrderCount = () => request({ url: '/api/order/buyer/count' })

module.exports = {
  createOrder,
  payOrder,
  shipOrder,
  receiveOrder,
  cancelOrder,
  applyRefund,
  cancelRefund,
  agreeRefund,
  rejectRefund,
  getOrder,
  pageMyOrders,
  pageMySales,
  getOrderCount
}
