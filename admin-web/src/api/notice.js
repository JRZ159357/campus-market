// 文件路径: admin-web/src/api/notice.js
import request from '@/utils/request'

/**
 * 已发布公告列表（一次发布 = 一组通知，按标题 + 发布时间聚合）
 * @param {{pageNum:number,pageSize:number}} params
 */
export const pageBroadcasts = params => request.get('/notice/admin/page', { params })

/**
 * 发布系统公告
 * @param {{title:string,content:string,scope:number,userIds?:number[]}} data
 */
export const publishNotice = data => request.post('/notice/admin/publish', data)

/** 删除公告（整组删除） */
export const deleteBroadcast = id => request.delete(`/notice/admin/${id}`)
