// src/api/dashboard.js
import request from '@/utils/request'

/**
 * 数据仪表盘 API。
 *
 * 全部是 GET 只读接口，管理员与客服都能访问。
 * 路径不带 /api 前缀——request 实例的 baseURL 已经是 /api，
 * 这里再写一次会拼成 /api/api/dashboard/summary，接口直接 404。
 */

/** 核心指标卡片 */
export function getDashboardSummary() {
  return request.get('/dashboard/summary')
}

/**
 * 趋势数据。
 * @param {number} days 统计天数，7 或 30
 */
export function getDashboardTrend(days) {
  return request.get('/dashboard/trend', { params: { days } })
}

/** 分布统计 */
export function getDashboardDistribution() {
  return request.get('/dashboard/distribution')
}

/** 排行榜 */
export function getDashboardRanking() {
  return request.get('/dashboard/ranking')
}

/** 最近动态 */
export function getDashboardRecent() {
  return request.get('/dashboard/recent')
}

/**
 * 手动刷新：清除服务端缓存。
 * 仅管理员可用（客服是只读账号，页面上的刷新按钮对它不可见）。
 */
export function refreshDashboard() {
  return request.post('/dashboard/refresh')
}
