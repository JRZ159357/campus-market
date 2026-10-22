// 文件路径: admin-web/src/api/account.js
import request from '@/utils/request'

/** 后台账号分页（仅 admin 与 customer_service） */
export const pageAccounts = params => request.get('/admin/account/page', { params })

/** 新增后台账号 */
export const createAccount = data => request.post('/admin/account', data)

/** 编辑后台账号（昵称 / 角色 / 状态） */
export const updateAccount = (id, data) => request.put(`/admin/account/${id}`, data)

/** 重置密码（不需要旧密码，仅管理员可用） */
export const resetAccountPassword = (id, data) => request.put(`/admin/account/${id}/password`, data)

/** 启用 / 禁用账号 */
export const updateAccountStatus = (id, status) =>
  request.put(`/admin/account/${id}/status`, { status })

/** 删除账号 */
export const deleteAccount = id => request.delete(`/admin/account/${id}`)

/** 可分配的角色。普通用户与认证学生不在后台账号体系内 */
export const ACCOUNT_ROLES = [
  { value: 'admin', label: '管理员', color: '#F56C6C' },
  { value: 'customer_service', label: '客服', color: '#409EFF' }
]

export const roleMeta = role =>
  ACCOUNT_ROLES.find(r => r.value === role) || { value: role, label: '未知', color: '#909399' }
