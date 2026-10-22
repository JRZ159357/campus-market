// 文件路径: miniprogram/utils/format.js
const { BASE_URL } = require('../config')

/**
 * 相对地址转完整图片地址。后端返回 /uploads/xxx，直接给 image 组件无法加载。
 */
function imgUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//.test(path)) return path
  return BASE_URL + path
}

/** 价格保留两位小数 */
function money(value) {
  if (value === null || value === undefined || value === '') return '0.00'
  return Number(value).toFixed(2)
}

/** 商品状态文案与样式类 */
function statusMeta(status) {
  switch (status) {
    case 0:
      return { label: '待审核', className: 'tag--wait' }
    case 1:
      return { label: '已上架', className: 'tag--pass' }
    case 2:
      return { label: '已下架', className: 'tag--off' }
    case 3:
      return { label: '已售出', className: 'tag--sold' }
    case 4:
      return { label: '违规下架', className: 'tag--reject' }
    default:
      return { label: '未知', className: 'tag--off' }
  }
}

/**
 * 时间友好展示：1 分钟内显示"刚刚"，1 小时内显示"x分钟前"，
 * 24 小时内显示"x小时前"，7 天内显示"x天前"，更早显示日期。
 */
function timeAgo(text) {
  if (!text) return ''
  const date = new Date(String(text).replace(/-/g, '/'))
  if (isNaN(date.getTime())) return text
  const diff = Date.now() - date.getTime()
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 24 * 60 * 60 * 1000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 7 * 24 * 60 * 60 * 1000) return Math.floor(diff / 86400000) + '天前'
  return String(text).slice(0, 10)
}

/**
 * 订单状态文案、样式类与一句话说明。
 * 颜色约定：待付款橙、待发货蓝、待收货紫、已完成绿、已取消灰、退款红。
 * className 对应 app.wxss 的 order-tag--x 与页面内的 banner 配色。
 */
function orderStatusMeta(status) {
  switch (Number(status)) {
    case 0:
      return { label: '待付款', className: 'order-tag--pay', desc: '请尽快完成支付，超时订单将自动取消' }
    case 1:
      return { label: '待发货', className: 'order-tag--ship', desc: '买家已付款，等待卖家发货或当面交付' }
    case 2:
      return { label: '待收货', className: 'order-tag--receive', desc: '卖家已发货，请确认收到商品后点确认收货' }
    case 3:
      return { label: '已完成', className: 'order-tag--done', desc: '交易已完成，感谢使用校园二手交易平台' }
    case 4:
      return { label: '已取消', className: 'order-tag--cancel', desc: '订单已取消' }
    case 5:
      return { label: '退款中', className: 'order-tag--refund', desc: '退款申请处理中，请等待卖家响应' }
    case 6:
      return { label: '已退款', className: 'order-tag--refunded', desc: '退款已完成，商品已恢复上架' }
    default:
      return { label: '未知状态', className: 'order-tag--cancel', desc: '' }
  }
}

/**
 * 星级对应的文字描述。评价页点星时要实时显示，列表页也可用作副标题。
 */
function starText(stars) {
  switch (Number(stars)) {
    case 5:
      return '非常满意'
    case 4:
      return '满意'
    case 3:
      return '一般'
    case 2:
      return '不满意'
    case 1:
      return '非常差'
    default:
      return ''
  }
}

/**
 * 星级对应的信誉分变动，必须与后端 CreditRule.deltaOfStars 保持一致。
 * 前端只用来生成「评价成功，信誉分+2」这类提示文案，
 * 真正的加分仍由服务端执行——前端算错顶多提示不准，不会影响数据。
 */
function creditDeltaOfStars(stars) {
  switch (Number(stars)) {
    case 5:
      return 2
    case 4:
      return 1
    case 3:
      return 0
    case 2:
      return -2
    case 1:
      return -5
    default:
      return 0
  }
}

module.exports = { imgUrl, money, statusMeta, orderStatusMeta, timeAgo, starText, creditDeltaOfStars }
