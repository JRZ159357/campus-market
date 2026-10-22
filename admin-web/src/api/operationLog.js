// src/api/operationLog.js
import request from '@/utils/request'

/**
 * 操作日志 API。
 *
 * 这个文件刻意<b>只有</b>一个方法。操作日志的价值全部来自「不可篡改」：
 * 一旦提供修改或删除入口，管理员在纠纷里删掉自己那几条记录，
 * 日志就失去了意义，答辩时被问「你怎么证明你没有偷偷改数据」也答不上来。
 * 不可变性由「前端封装里根本没有写方法」和「后端接口层没有写接口」共同保证。
 */

/** 操作日志分页（管理员与客服，只读） */
export function pageOperationLogs(params) {
  return request.get('/operation-log/page', { params })
}

/** 业务模块选项，与后端 OpLogModule 保持一致 */
export const LOG_MODULES = [
  { value: 'report', label: '举报处理' },
  { value: 'user', label: '用户管理' },
  { value: 'product', label: '商品管理' },
  { value: 'review', label: '评价管理' },
  { value: 'order', label: '订单管理' },
  { value: 'student_auth', label: '学生认证' }
]

/** 动作选项，与后端 OpLogModule 保持一致 */
export const LOG_ACTIONS = [
  { value: 'accept', label: '接单' },
  { value: 'handle', label: '处理' },
  { value: 'offline', label: '强制下架' },
  { value: 'ban', label: '封禁/解封' },
  { value: 'update_profile', label: '修改资料' },
  { value: 'update_role', label: '变更角色' },
  { value: 'delete', label: '删除' },
  { value: 'audit', label: '审核' }
]

/** 模块配色，让表格一眼能区分是哪一类操作 */
export const MODULE_COLORS = {
  report: '#f56c6c',
  user: '#409eff',
  product: '#ff6b35',
  review: '#7c4dff',
  order: '#e6a23c',
  student_auth: '#67c23a'
}