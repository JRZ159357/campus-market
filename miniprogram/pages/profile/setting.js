// 文件路径: miniprogram/pages/profile/setting.js
const { logout, deleteAccount } = require('../../api/user')
const { TOKEN_KEY, USER_KEY } = require('../../config')

/** 应用版本号 */
const APP_VERSION = '1.0.0'

/** 注销账号的固定确认口令，必须与后端 UserRule.ACCOUNT_DELETE_CONFIRM 一致 */
const CONFIRM_TEXT = '注销账号'

Page({
  data: {
    version: APP_VERSION,
    cacheSize: '0 KB',
    cacheBytes: 0,
    // 注销账号弹层
    showDelete: false,
    deletePassword: '',
    deleteConfirm: '',
    deleteError: '',
    submitting: false
  },

  onShow() {
    this.refreshCacheSize()
  },

  /**
   * 计算本地缓存占用。wx.getStorageInfoSync 返回的 currentSize 单位是 KB。
   */
  refreshCacheSize() {
    try {
      const info = wx.getStorageInfoSync()
      const bytes = (info.currentSize || 0) * 1024
      this.setData({
        cacheBytes: bytes,
        cacheSize: this.formatSize(bytes)
      })
    } catch (e) {
      this.setData({ cacheBytes: 0, cacheSize: '0 KB' })
    }
  },

  formatSize(bytes) {
    if (!bytes) return '0 KB'
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / 1024 / 1024).toFixed(2) + ' MB'
  },

  goSecurity() {
    wx.navigateTo({ url: '/pages/profile/security' })
  },

  goAbout() {
    wx.navigateTo({ url: '/pages/profile/about' })
  },

  contactService() {
    wx.showModal({
      title: '联系客服',
      content: '客服工作时间 9:00 - 21:00\n邮箱：support@campus-market.local',
      showCancel: false,
      confirmText: '知道了'
    })
  },

  /**
   * 清除缓存。
   * <p>
   * 登录态（token / 用户信息）必须保留——清缓存的目的是省空间，
   * 不是把用户踢下线。这里逐个删掉除登录键之外的键，
   * 而不是 wx.clearStorage()。
   */
  clearCache() {
    wx.showModal({
      title: '清除缓存',
      content: '将清理本地缓存的图片与列表数据，不会退出登录。确定要清除吗？',
      confirmText: '清除',
      success: async res => {
        if (!res.confirm) return
        try {
          const info = wx.getStorageInfoSync()
          const keep = [TOKEN_KEY, USER_KEY]
          const keys = info.keys || []
          keys.forEach(key => {
            if (keep.indexOf(key) === -1) {
              wx.removeStorageSync(key)
            }
          })
          this.refreshCacheSize()
          wx.showToast({ title: '缓存已清除', icon: 'success' })
        } catch (e) {
          wx.showToast({ title: '清除失败', icon: 'none' })
        }
      }
    })
  },

  /**
   * 退出登录。二次确认，确认后清本地登录态并回到首页。
   */
  handleLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      confirmText: '退出',
      success: async res => {
        if (!res.confirm) return
        try {
          await logout()
        } catch (e) {
          // 即使接口失败也要清理本地登录态，否则用户会卡在"半登录"状态
        }
        getApp().clearLogin()
        getApp().globalData.verified = false
        wx.showToast({ title: '已退出登录', icon: 'none' })
        setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 600)
      }
    })
  },

  /**
   * 打开注销账号弹层。
   * <p>
   * 注销是不可逆操作，所以要求三重确认：弹层确认 + 口令 + 密码。
   * 客服与管理员不能自助注销（后端也会拦），这里提前给出提示。
   */
  openDelete() {
    this.setData({ showDelete: true, deletePassword: '', deleteConfirm: '', deleteError: '' })
  },

  closeDelete() {
    this.setData({ showDelete: false, deletePassword: '', deleteConfirm: '', deleteError: '' })
  },

  /** 弹层内点击空白处不穿透关闭 */
  noop() {
  },

  onDeleteInput(e) {
    const field = e.currentTarget.dataset.field
    if (!field) return
    this.setData({ [field]: e.detail.value, deleteError: '' })
  },

  async confirmDelete() {
    const { deletePassword, deleteConfirm } = this.data
    if (!deletePassword) {
      this.setData({ deleteError: '请输入登录密码' })
      return
    }
    if (deleteConfirm.trim() !== CONFIRM_TEXT) {
      this.setData({ deleteError: '请输入「' + CONFIRM_TEXT + '」以确认注销' })
      return
    }
    this.setData({ submitting: true })
    try {
      await deleteAccount({ password: deletePassword, confirmText: deleteConfirm.trim() })
      wx.showToast({ title: '账号已注销', icon: 'none' })
      getApp().clearLogin()
      getApp().globalData.verified = false
      setTimeout(() => {
        wx.reLaunch({ url: '/pages/index/index' })
      }, 800)
    } catch (e) {
      // 错误提示由请求封装统一处理，弹层保持打开便于重试
    } finally {
      this.setData({ submitting: false })
    }
  }
})
