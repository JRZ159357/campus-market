// 文件路径: miniprogram/api/credit.js
const { request } = require('../utils/request')

/**
 * 用户信誉分详情：分数、等级、评价统计、最近评价与变动。
 * 未登录也能看——买家在商品详情页就要判断卖家靠不靠谱。
 */
const getCredit = userId => request({ url: '/api/credit/' + userId })

/** 信誉分变动明细，type 可选（1评价 2举报 3管理员调整 4评价被删除） */
const pageCreditLogs = (userId, params) => request({ url: '/api/credit/' + userId + '/logs', data: params })

module.exports = { getCredit, pageCreditLogs }
