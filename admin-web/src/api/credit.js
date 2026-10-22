// src/api/credit.js
import request from '@/utils/request'

/** 用户信誉分详情（分数 / 等级 / 统计 / 最近评价与变动） */
export const getCreditDetail = userId => request.get(`/credit/${userId}`)

/** 信誉分变动记录，type=3 只看管理员调整 */
export const pageCreditLogs = (userId, params) => request.get(`/credit/${userId}/logs`, { params })

/** 管理员调整信誉分 */
export const adjustCredit = (userId, data) => request.put(`/credit/${userId}/adjust`, data)

/** 按关键词搜索用户（昵称 / 账号 / 手机号 / ID） */
export const pageCreditUsers = params => request.get('/credit/admin/users', { params })
