// 文件路径: miniprogram/utils/request.js
const { BASE_URL, TOKEN_KEY } = require('../config')

/**
 * 统一请求封装。
 * 约定：后端返回 { code, msg, data }，code=200 视为成功，resolve(data)。
 */
function request({ url, method = 'GET', data = {}, showLoading = false, auth = true } = {}) {
  return new Promise((resolve, reject) => {
    if (showLoading) {
      wx.showLoading({ title: '加载中', mask: true })
    }

    const header = { 'Content-Type': 'application/json' }
    if (auth) {
      const token = wx.getStorageSync(TOKEN_KEY)
      if (token) {
        header.Authorization = 'Bearer ' + token
      }
    }

    wx.request({
      url: BASE_URL + url,
      method,
      data,
      header,
      timeout: 15000,
      success(res) {
        const body = res.data || {}
        if (body.code === 200) {
          resolve(body.data)
          return
        }
        if (body.code === 401) {
          // 登录失效：清缓存并引导重新登录
          getApp().clearLogin()
          wx.showToast({ title: body.msg || '请重新登录', icon: 'none' })
          setTimeout(() => {
            wx.navigateTo({ url: '/pages/login/index' })
          }, 800)
          reject(new Error(body.msg || '未登录'))
          return
        }
        wx.showToast({ title: body.msg || '操作失败', icon: 'none' })
        reject(new Error(body.msg || '操作失败'))
      },
      fail(err) {
        wx.showToast({ title: '网络异常，请检查后端服务', icon: 'none' })
        reject(err)
      },
      complete() {
        if (showLoading) {
          wx.hideLoading()
        }
      }
    })
  })
}

/**
 * 图片上传，返回服务端相对地址 /uploads/...
 */
function uploadImage(filePath) {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync(TOKEN_KEY)
    wx.uploadFile({
      url: BASE_URL + '/api/file/upload',
      filePath,
      name: 'file',
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success(res) {
        let body = {}
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          wx.showToast({ title: '上传响应格式异常', icon: 'none' })
          reject(e)
          return
        }
        if (body.code === 200) {
          resolve(body.data)
        } else {
          wx.showToast({ title: body.msg || '上传失败', icon: 'none' })
          reject(new Error(body.msg || '上传失败'))
        }
      },
      fail(err) {
        wx.showToast({ title: '图片上传失败', icon: 'none' })
        reject(err)
      }
    })
  })
}

module.exports = { request, uploadImage }
