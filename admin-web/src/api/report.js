// src/api/report.js
import request from '@/utils/request'

/**
 * 举报管理 API。
 *
 * 路径分两段：/api/report/** 是用户端，/api/report/admin/** 是管理端。
 * 分段不放在不同 Controller 里而是同一个 ReportController 的两个路径前缀，
 * 是为了让「举报」这个领域的接口在一个文件里能看全；
 * 真正的权限隔离靠后端 @RequireRole + 拦截器的客服只读规则，不靠路径前缀。
 */

/** 提交举报 */
export function submitReport(data) {
  return request.post('/report', data)
}

/** 我的举报列表 */
export function myReports(params) {
  return request.get('/report/my', { params })
}

/** 我的举报详情 */
export function myReportDetail(id) {
  return request.get(`/report/my/${id}`)
}

/** 举报列表（客服与管理员） */
export function pageReports(params) {
  return request.get('/report/admin/page', { params })
}

/** 举报详情（含被举报对象快照） */
export function getReportDetail(id) {
  return request.get(`/report/admin/${id}`)
}

/** 接单：待处理 → 处理中 */
export function acceptReport(id) {
  return request.put(`/report/admin/${id}/accept`)
}

/** 处理举报 */
export function handleReport(id, data) {
  return request.put(`/report/admin/${id}/handle`, data)
}

/** 举报统计 */
export function reportStats() {
  return request.get('/report/admin/stats')
}

/**
 * 处理动作可选项。带 desc 是为了在管理端把「点了会发生什么」直接写在选项旁边：
 * 客服做的是一次真实处罚（下架 / 封禁 / 删评价 / 扣分），
 * 只显示「下架商品」四个字会让人误以为只是隐藏，而看不到「卖家扣10分」这一层后果。
 */
export const HANDLE_ACTIONS = [
  { value: 0, label: '不处理（举报不成立）', desc: '仅把举报标记为已驳回并通知举报人，不做任何处罚' },
  { value: 1, label: '下架商品', desc: '商品强制下架（违规下架），同时卖家信誉分 -10' },
  { value: 2, label: '封禁用户', desc: '账号封禁且其全部在架商品下架，同时信誉分 -10' },
  { value: 3, label: '删除评价', desc: '违规评价逻辑删除，同时评价人信誉分 -5' },
  { value: 4, label: '扣除信誉分', desc: '不删除任何内容，仅对被举报人信誉分 -10' }
]

/** 按举报类型给出可选动作（与后端 ReportType.allowedActions 保持一致） */
export function actionsOfType(reportType) {
  if (reportType === 1) return HANDLE_ACTIONS.filter(a => [0, 1, 4].includes(a.value))
  if (reportType === 2) return HANDLE_ACTIONS.filter(a => [0, 2, 4].includes(a.value))
  if (reportType === 3) return HANDLE_ACTIONS.filter(a => [3, 4].includes(a.value))
  return HANDLE_ACTIONS
}

/**
 * 按举报类型给出「默认应执行的动作」。
 * <p>
 * 处理区默认结论是「举报成立」，那默认动作就不能是「不处理」——
 * 两者自相矛盾，提交后会得到一条「已处理但什么也没处置」的举报，
 * 举报人被告知「已处理」，实际违规内容还挂在那儿。
 * <p>
 * 这里给的是「该类型最典型的处罚」：商品→下架、用户→封禁、评价→删除。
 * 只是默认值，客服完全可以改成「仅扣信誉分」或「不处理」。
 */
export function defaultActionOfType(reportType) {
  if (reportType === 1) return 1
  if (reportType === 2) return 2
  if (reportType === 3) return 3
  return 0
}

/** 举报类型选项（与后端 ReportType 保持一致） */
export const REPORT_TYPES = [
  { value: 1, label: '商品举报', typeTag: 'danger', color: '#409EFF' },
  { value: 2, label: '用户举报', typeTag: 'warning', color: '#FF6B35' },
  { value: 3, label: '评价举报', typeTag: 'primary', color: '#7C4DFF' }
]

/** 举报原因选项（与后端 ReportReason 保持一致） */
export const REPORT_REASONS = [
  { value: 1, label: '虚假描述' },
  { value: 2, label: '违禁品' },
  { value: 3, label: '欺诈' },
  { value: 4, label: '辱骂骚扰' },
  { value: 5, label: '广告导流' },
  { value: 6, label: '其他' }
]

export const reportTypeLabel = value => {
  const hit = REPORT_TYPES.find(t => t.value === value)
  return hit ? hit.label : '未知'
}

export const reportTypeTag = value => {
  const hit = REPORT_TYPES.find(t => t.value === value)
  return hit ? hit.typeTag : 'info'
}

export const reportStatusTag = value => {
  return { 0: 'danger', 1: 'warning', 2: 'success', 3: 'info' }[value] || 'info'
}

export const reportReasonLabel = value => {
  const hit = REPORT_REASONS.find(t => t.value === value)
  return hit ? hit.label : '未选择'
}