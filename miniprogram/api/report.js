// 文件路径: miniprogram/api/report.js
const { request } = require('../utils/request')

/**
 * 提交举报
 * @param {{reportType:number,targetId:number,reason:number,content:string,images?:string[]}} data
 */
function submitReport(data) {
  return request({ url: '/api/report', method: 'POST', data })
}

/**
 * 我的举报列表
 * @param {{pageNum?:number,pageSize?:number}} params
 */
function myReports(params = {}) {
  const query = ['pageNum=' + (params.pageNum || 1), 'pageSize=' + (params.pageSize || 10)]
  return request({ url: '/api/report/my?' + query.join('&') })
}

/** 我的举报详情 */
function myReportDetail(id) {
  return request({ url: '/api/report/my/' + id })
}

/**
 * 举报类型，与后端 ReportType 保持一致。
 * typeLabel 展示用，color 是卡片左侧色条——
 * 三类举报用不同颜色，让人一眼扫过列表就知道这条是商品还是人还是评价的问题。
 */
const REPORT_TYPES = {
  1: { typeLabel: '商品举报', shortLabel: '商品', color: '#409EFF', bg: '#ECF5FF' },
  2: { typeLabel: '用户举报', shortLabel: '用户', color: '#FF6B35', bg: '#FFF3EC' },
  3: { typeLabel: '评价举报', shortLabel: '评价', color: '#7C4DFF', bg: '#F3EEFF' }
}

/** 举报原因，与后端 ReportReason 保持一致 */
const REPORT_REASONS = [
  { value: 1, label: '虚假描述', desc: '商品与实际不符' },
  { value: 2, label: '违禁品', desc: '禁止交易的物品' },
  { value: 3, label: '欺诈', desc: '以交易为名的诈骗' },
  { value: 4, label: '辱骂骚扰', desc: '不文明言行' },
  { value: 5, label: '广告导流', desc: '微信号、二维码、外链' },
  { value: 6, label: '其他', desc: '其他情况，请详细说明' }
]

/** 处理动作，与后端 ReportHandleAction 保持一致 */
const HANDLE_ACTIONS = {
  0: '不处理（举报不成立）',
  1: '强制下架商品',
  2: '封禁用户',
  3: '删除违规评价',
  4: '扣除信誉分'
}

/** 状态配色，与管理端 ReportStatus.color 保持一致 */
const STATUS_META = {
  0: { label: '待处理', color: '#F56C6C', bg: '#FEF0F0' },
  1: { label: '处理中', color: '#E6A23C', bg: '#FDF6EC' },
  2: { label: '已处理', color: '#67C23A', bg: '#F0F9EB' },
  3: { label: '已驳回', color: '#909399', bg: '#F4F4F5' }
}

/** 原因取值 → 文案 */
function reasonLabel(value) {
  const hit = REPORT_REASONS.find(r => r.value === value)
  return hit ? hit.label : '未选择原因'
}

/** 类型取值 → 类型元信息（未知类型兜底为商品，避免 undefined 报错） */
function typeMeta(value) {
  return REPORT_TYPES[value] || { typeLabel: '未知类型', shortLabel: '未知', color: '#909399', bg: '#F4F4F5' }
}

module.exports = {
  submitReport, myReports, myReportDetail,
  REPORT_TYPES, REPORT_REASONS, HANDLE_ACTIONS, STATUS_META, reasonLabel, typeMeta
}