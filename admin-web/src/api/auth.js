// 文件路径: admin-web/src/api/auth.js
import request from '@/utils/request'

/**
 * 管理端账号密码登录（仅管理员与客服）
 * @param {{username: string, password: string}} data
 */
export const adminLogin = data => request.post('/auth/admin-login', data)

/** 获取当前登录用户信息 */
export const getMe = () => request.get('/auth/me')

/**
 * 修改当前账号密码
 * @param {{oldPassword: string, newPassword: string}} data
 */
export const changePassword = data => request.post('/auth/change-password', data)

/** 退出登录 */
export const logout = () => request.post('/auth/logout')
