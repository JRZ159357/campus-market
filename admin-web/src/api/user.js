// 文件路径: admin-web/src/api/user.js
import request from '@/utils/request'

/**
 * 管理端用户列表分页
 * @param {{pageNum:number,pageSize:number,keyword?:string,role?:string,status?:number,
 *          studentVerified?:number,minCredit?:number,maxCredit?:number,
 *          startTime?:string,endTime?:string}} params
 */
export const pageUsers = params => request.get('/admin/user/page', { params })

/** 用户详情（基本信息 + 信誉分变动 + 商品 + 订单 + 评价） */
export const getUserDetail = userId => request.get(`/admin/user/${userId}`)

/** 编辑用户资料 */
export const updateUserProfile = (userId, data) => request.put(`/admin/user/${userId}`, data)

/** 封禁 / 解封：status 0正常 1封禁 */
export const updateUserStatus = (userId, data) => request.put(`/admin/user/${userId}/status`, data)

/** 分配角色 */
export const updateUserRole = (userId, role) => request.put(`/admin/user/${userId}/role`, { role })

/** 学生认证通过 / 驳回：status 1通过 2驳回 */
export const auditUserStudentAuth = (userId, data) =>
  request.put(`/admin/user/${userId}/student-auth`, data)
