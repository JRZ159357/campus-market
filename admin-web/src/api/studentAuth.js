// 文件路径: admin-web/src/api/studentAuth.js
import request from '@/utils/request'

/**
 * 分页查询学生认证申请
 * @param {{pageNum: number, pageSize: number, status?: number, keyword?: string}} params
 */
export const pageStudentAuth = params => request.get('/student-auth/page', { params })

/** 查看申请详情 */
export const getStudentAuth = id => request.get(`/student-auth/${id}`)

/**
 * 审核申请
 * @param {number} id 申请ID
 * @param {{status: number, rejectReason?: string}} data status: 1通过 2驳回
 */
export const auditStudentAuth = (id, data) => request.put(`/student-auth/${id}/audit`, data)
