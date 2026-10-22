// 文件路径: admin-web/src/api/chat.js
import request from '@/utils/request'

/**
 * 客服工作台：全部会话列表（只读）
 * @param {{pageNum:number,pageSize:number,keyword?:string}} params
 */
export const pageAllSessions = params => request.get('/chat/admin/session/list', { params })

/** 客服工作台：查看某个会话的聊天记录（只读） */
export const getSessionMessages = (sessionId, size = 100) =>
  request.get(`/chat/admin/message/${sessionId}`, { params: { size } })
