// 文件路径: miniprogram/api/notice.js
const { request } = require('../utils/request')

/**
 * 我的通知列表（未读优先）
 * @param {{pageNum?:number,pageSize?:number,type?:number,onlyUnread?:boolean}} params
 */
function pageNotices(params = {}) {
  const query = ['pageNum=' + (params.pageNum || 1), 'pageSize=' + (params.pageSize || 20)]
  if (params.type) query.push('type=' + params.type)
  if (params.onlyUnread) query.push('onlyUnread=true')
  return request({ url: '/api/notice/list?' + query.join('&') })
}

/** 标记单条已读 */
function readNotice(id) {
  return request({ url: '/api/notice/read/' + id, method: 'PUT' })
}

/** 全部已读 */
function readAllNotices() {
  return request({ url: '/api/notice/read-all', method: 'PUT' })
}

/** 未读数汇总 */
function getUnreadCount() {
  return request({ url: '/api/notice/unread-count' })
}

module.exports = { pageNotices, readNotice, readAllNotices, getUnreadCount }
