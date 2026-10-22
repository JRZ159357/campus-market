// 文件路径: miniprogram/api/chat.js
const { request } = require('../utils/request')

/**
 * 我的会话列表（按最后消息时间倒序）
 * @param {{pageNum?:number,pageSize?:number}} params
 */
function pageSessions(params = {}) {
  return request({ url: '/api/chat/session/list?pageNum=' + (params.pageNum || 1) + '&pageSize=' + (params.pageSize || 20) })
}

/** 会话详情（含最近 50 条消息） */
function getSessionDetail(id) {
  return request({ url: '/api/chat/session/' + id })
}

/**
 * 创建或获取会话
 * @param {{sellerId:number,productId?:number}} data
 */
function createSession(data) {
  return request({ url: '/api/chat/session', method: 'POST', data })
}

/** 删除会话（仅从自己列表移除） */
function deleteSession(id) {
  return request({ url: '/api/chat/session/' + id, method: 'DELETE' })
}

/** 某商品下我作为卖家收到的咨询会话（我的发布 → 查看咨询） */
function listSessionsByProduct(productId) {
  return request({ url: '/api/chat/session/by-product/' + productId })
}

/**
 * 历史消息（游标分页）
 * @param {number} sessionId
 * @param {{cursor?:number,size?:number}} params
 */
function pageMessages(sessionId, params = {}) {
  let url = '/api/chat/message/' + sessionId + '?size=' + (params.size || 20)
  if (params.cursor) {
    url += '&cursor=' + params.cursor
  }
  return request({ url })
}

/**
 * 发送消息
 * @param {{sessionId:number,msgType:number,content:string}} data
 */
function sendMessage(data) {
  return request({ url: '/api/chat/message', method: 'POST', data })
}

/** 标记会话已读 */
function readSession(sessionId) {
  return request({ url: '/api/chat/message/read/' + sessionId, method: 'PUT' })
}

/** 未读数汇总（tabBar 角标） */
function getUnread() {
  return request({ url: '/api/chat/unread' })
}

module.exports = {
  pageSessions,
  getSessionDetail,
  createSession,
  deleteSession,
  listSessionsByProduct,
  pageMessages,
  sendMessage,
  readSession,
  getUnread
}
