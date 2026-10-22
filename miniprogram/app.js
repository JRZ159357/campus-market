// 文件路径: miniprogram/app.js
const { TOKEN_KEY, USER_KEY } = require('./config')

App({
  globalData: {
    token: '',
    userInfo: null,
    // 首页公告是否已被用户手动关闭（模块12）。
    // 只活在本次运行期间：用户关掉的是「这条公告」，
    // 下次打开小程序或者运营换了新公告，都应该重新展示
    noticeClosed: false
  },

  onLaunch() {
    this.globalData.token = wx.getStorageSync(TOKEN_KEY) || ''
    this.globalData.userInfo = wx.getStorageSync(USER_KEY) || null
  },

  /**
   * 登录成功后写入全局态与本地缓存
   */
  setLogin(loginData) {
    this.globalData.token = loginData.token
    this.globalData.userInfo = loginData
    wx.setStorageSync(TOKEN_KEY, loginData.token)
    wx.setStorageSync(USER_KEY, loginData)
  },

  /**
   * 刷新用户信息（角色变更、认证通过后调用）
   */
  setUserInfo(userInfo) {
    this.globalData.userInfo = { ...(this.globalData.userInfo || {}), ...userInfo }
    wx.setStorageSync(USER_KEY, this.globalData.userInfo)
  },

  clearLogin() {
    this.globalData.token = ''
    this.globalData.userInfo = null
    wx.removeStorageSync(TOKEN_KEY)
    wx.removeStorageSync(USER_KEY)
  },

  isLogin() {
    return !!this.globalData.token
  }
})
