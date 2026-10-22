// 文件路径: miniprogram/api/user.js
const { request } = require('../utils/request')

/** 拼接查询串，自动忽略空值 */
function qs(params = {}) {
  const parts = Object.keys(params)
    .filter(key => params[key] !== undefined && params[key] !== null && params[key] !== '')
    .map(key => key + '=' + encodeURIComponent(params[key]))
  return parts.length ? '?' + parts.join('&') : ''
}

/**
 * 微信登录
 * @param {{code: string, nickname?: string, avatar?: string}} data
 */
const wxLogin = data => request({ url: '/api/auth/wx-login', method: 'POST', data })

/**
 * 账号密码登录
 * @param {{username: string, password: string}} data
 */
const accountLogin = data => request({ url: '/api/auth/login', method: 'POST', data })

/**
 * 账号密码注册（成功后直接返回 token，注册即登录）
 * @param {{username: string, password: string, nickname?: string}} data
 */
const register = data => request({ url: '/api/auth/register', method: 'POST', data })

/**
 * 微信用户绑定账号密码
 * @param {{username: string, password: string}} data
 */
const bindAccount = data => request({ url: '/api/auth/bind-account', method: 'POST', data })

/**
 * 修改密码
 * @param {{oldPassword: string, newPassword: string}} data
 */
const changePassword = data =>
  request({ url: '/api/auth/change-password', method: 'POST', data })

/**
 * 校验账号是否可注册
 * @param {string} username
 */
const checkUsername = username =>
  request({ url: '/api/auth/check-username', data: { username } })

/** 当前登录用户信息 */
const getMe = () => request({ url: '/api/auth/me' })

/**
 * 绑定手机号
 * @param {string} code getPhoneNumber 回调返回的 code
 */
const bindPhone = code => request({ url: '/api/auth/bind-phone', method: 'POST', data: { code } })

/** 我的学生认证状态 */
const getAuthStatus = () => request({ url: '/api/student-auth/status' })

/**
 * 提交学生认证申请
 * @param {{studentNo: string, realName: string, school?: string, certImg: string}} data
 */
const applyStudentAuth = data => request({ url: '/api/student-auth', method: 'POST', data })

/** 退出登录 */
const logout = () => request({ url: '/api/user/logout', method: 'POST' })

/**
 * 个人中心聚合数据
 * 用户信息 + 信誉分等级 + 发布/已售/收藏/关注/粉丝/待评价 + 各状态订单数
 */
const getProfile = () => request({ url: '/api/user/profile' })

/**
 * 卖家主页数据（免登录，携带 token 时后端会返回关注状态）
 * @param {number} userId
 */
const getUserHome = userId => request({ url: '/api/user/home/' + userId })

/**
 * 卖家在售商品列表
 * @param {number} userId
 * @param {{pageNum?:number,pageSize?:number}} params
 */
const getUserProducts = (userId, params) =>
  request({ url: '/api/user/products/' + userId + qs(params) })

/**
 * 修改个人资料
 * @param {{nickname?:string,avatar?:string,gender?:number,intro?:string}} data
 */
const updateProfile = data => request({ url: '/api/user/profile', method: 'PUT', data })

/**
 * 修改手机号（微信授权 code）
 * @param {string} code
 */
const updatePhone = code => request({ url: '/api/user/phone', method: 'PUT', data: { code } })

/**
 * 注销账号
 * @param {{password:string,confirmText:string}} data
 */
const deleteAccount = data => request({ url: '/api/user/account', method: 'DELETE', data })

module.exports = {
  wxLogin,
  accountLogin,
  register,
  bindAccount,
  changePassword,
  checkUsername,
  getMe,
  bindPhone,
  getAuthStatus,
  applyStudentAuth,
  logout,
  getProfile,
  getUserHome,
  getUserProducts,
  updateProfile,
  updatePhone,
  deleteAccount
}
