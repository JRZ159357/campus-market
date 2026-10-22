// 文件路径: miniprogram/pages/login/index.js
const { wxLogin, accountLogin } = require('../../api/user')

const USERNAME_RE = /^[A-Za-z0-9]{4,20}$/

Page({
  data: {
    mode: 'wx', // wx | account
    username: '',
    password: '',
    showPassword: false,
    focused: '',
    logging: false,
    accountLoading: false,
    errors: {},
    errorMsg: ''
  },

  onLoad() {
    // 已登录用户再次进入登录页时，直接回首页，避免重复登录
    if (getApp().isLogin()) {
      this.goHome()
    }
  },

  switchMode(e) {
    const mode = e.currentTarget.dataset.mode
    if (!mode || mode === this.data.mode) return
    this.setData({ mode, errors: {}, errorMsg: '' })
  },

  onUsername(e) {
    this.setData({ username: e.detail.value, 'errors.username': '', errorMsg: '' })
  },

  onPassword(e) {
    this.setData({ password: e.detail.value, 'errors.password': '', errorMsg: '' })
  },

  onFocus(e) {
    this.setData({ focused: e.currentTarget.dataset.field || '' })
  },

  onBlur() {
    this.setData({ focused: '' })
  },

  togglePassword() {
    this.setData({ showPassword: !this.data.showPassword })
  },

  /**
   * 微信一键登录：wx.login 取 code → 后端换 openid → 签发 token
   */
  handleWxLogin() {
    if (this.data.logging) return
    this.setData({ logging: true, errorMsg: '' })

    wx.login({
      success: async res => {
        if (!res.code) {
          this.setData({ logging: false })
          wx.showToast({ title: '获取登录凭证失败', icon: 'none' })
          return
        }
        try {
          const data = await wxLogin({ code: res.code })
          getApp().setLogin(data)
          wx.showToast({ title: '登录成功', icon: 'success' })
          setTimeout(() => this.goHome(), 600)
        } catch (e) {
          // 错误提示由请求封装统一处理
        } finally {
          this.setData({ logging: false })
        }
      },
      fail: () => {
        this.setData({ logging: false })
        wx.showToast({ title: '微信登录失败，请重试', icon: 'none' })
      }
    })
  },

  /**
   * 账号密码登录：与微信登录签发同一套 token
   */
  async handleAccountLogin() {
    if (this.data.accountLoading) return
    const username = this.data.username.trim()
    const password = this.data.password
    const errors = {}

    if (!username) {
      errors.username = '请输入账号'
    } else if (!USERNAME_RE.test(username)) {
      errors.username = '账号需为4-20位字母或数字'
    }
    if (!password) {
      errors.password = '请输入密码'
    } else if (password.length < 6) {
      errors.password = '密码不少于6位'
    }

    if (Object.keys(errors).length) {
      this.setData({ errors, errorMsg: '' })
      return
    }

    this.setData({ accountLoading: true, errors: {}, errorMsg: '' })
    try {
      const data = await accountLogin({ username, password })
      getApp().setLogin(data)
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => this.goHome(), 600)
    } catch (e) {
      this.setData({ errorMsg: e.message || '登录失败，请重试' })
    } finally {
      this.setData({ accountLoading: false })
    }
  },

  goRegister() {
    wx.navigateTo({ url: '/pages/login/register' })
  },

  contactService() {
    wx.showModal({
      title: '联系客服',
      content: '客服邮箱：service@campus-market.edu.cn\n服务时间：周一至周五 9:00-18:00\n也可在「消息」页发起在线咨询。',
      showCancel: false,
      confirmText: '知道了'
    })
  },

  goHome() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      wx.navigateBack({ delta: 1 })
    } else {
      wx.reLaunch({ url: '/pages/index/index' })
    }
  }
})
