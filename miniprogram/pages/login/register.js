// 文件路径: miniprogram/pages/login/register.js
const { register, checkUsername } = require('../../api/user')

const USERNAME_RE = /^[A-Za-z0-9]{4,20}$/

Page({
  data: {
    username: '',
    password: '',
    confirmPassword: '',
    nickname: '',
    showPassword: false,
    focused: '',
    submitting: false,
    usernameState: '', // '' | checking | ok | taken
    errors: {},
    strength: 0, // 0 无 1 弱 2 中 3 强
    strengthText: ''
  },

  onUnload() {
    if (this.checkTimer) {
      clearTimeout(this.checkTimer)
      this.checkTimer = null
    }
  },

  onFocus(e) {
    this.setData({ focused: e.currentTarget.dataset.field || '' })
  },

  onBlur() {
    this.setData({ focused: '' })
  },

  onUsername(e) {
    const username = e.detail.value
    this.setData({
      username,
      'errors.username': '',
      usernameState: ''
    })
    if (this.checkTimer) {
      clearTimeout(this.checkTimer)
      this.checkTimer = null
    }
    if (!USERNAME_RE.test(username)) {
      return
    }
    // 输入停顿 500ms 后再校验，避免频繁请求
    this.checkTimer = setTimeout(() => this.verifyUsername(username), 500)
  },

  async verifyUsername(username) {
    this.setData({ usernameState: 'checking' })
    try {
      const available = await checkUsername(username)
      // 输入已变化时丢弃过期结果
      if (this.data.username !== username) return
      this.setData({
        usernameState: available ? 'ok' : 'taken',
        'errors.username': available ? '' : '该账号已被注册，请更换'
      })
    } catch (e) {
      this.setData({ usernameState: '' })
    }
  },

  onPassword(e) {
    const password = e.detail.value
    this.setData({
      password,
      'errors.password': '',
      ...this.calcStrength(password)
    })
    if (this.data.confirmPassword) {
      this.setData({
        'errors.confirmPassword':
          this.data.confirmPassword === password ? '' : '两次输入的密码不一致'
      })
    }
  },

  onConfirm(e) {
    const confirmPassword = e.detail.value
    this.setData({
      confirmPassword,
      'errors.confirmPassword':
        confirmPassword && confirmPassword !== this.data.password ? '两次输入的密码不一致' : ''
    })
  },

  onNickname(e) {
    this.setData({ nickname: e.detail.value, 'errors.nickname': '' })
  },

  togglePassword() {
    this.setData({ showPassword: !this.data.showPassword })
  },

  /**
   * 密码强度：长度 + 字母数字组合 + 超长，三档提示
   */
  calcStrength(password) {
    if (!password) {
      return { strength: 0, strengthText: '' }
    }
    if (password.length < 6) {
      return { strength: 1, strengthText: '强度：弱（至少6位）' }
    }
    let score = 1
    if (/[A-Za-z]/.test(password) && /\d/.test(password)) score += 1
    if (password.length >= 10) score += 1
    const level = Math.min(score, 3)
    const textMap = { 1: '强度：弱', 2: '强度：中', 3: '强度：强' }
    return { strength: level, strengthText: textMap[level] }
  },

  validate() {
    const { username, password, confirmPassword, nickname } = this.data
    const errors = {}

    if (!username) {
      errors.username = '请输入账号'
    } else if (!USERNAME_RE.test(username)) {
      errors.username = '账号需为4-20位字母或数字'
    } else if (this.data.usernameState === 'taken') {
      errors.username = '该账号已被注册，请更换'
    }

    if (!password) {
      errors.password = '请输入密码'
    } else if (password.length < 6 || password.length > 20) {
      errors.password = '密码长度需为6-20位'
    }

    if (!confirmPassword) {
      errors.confirmPassword = '请再次输入密码'
    } else if (confirmPassword !== password) {
      errors.confirmPassword = '两次输入的密码不一致'
    }

    if (nickname && nickname.length > 20) {
      errors.nickname = '昵称不能超过20个字'
    }

    this.setData({ errors })
    return Object.keys(errors).length === 0
  },

  async handleSubmit() {
    if (this.data.submitting) return
    if (!this.validate()) return

    this.setData({ submitting: true })
    try {
      const data = await register({
        username: this.data.username.trim(),
        password: this.data.password,
        nickname: this.data.nickname.trim() || undefined
      })
      // 注册即登录：直接写入登录态并回首页
      getApp().setLogin(data)
      wx.showToast({ title: '注册成功', icon: 'success' })
      setTimeout(() => {
        wx.reLaunch({ url: '/pages/index/index' })
      }, 700)
    } catch (e) {
      const msg = e.message || '注册失败，请重试'
      if (msg.indexOf('已被注册') > -1) {
        this.setData({ 'errors.username': msg, usernameState: 'taken' })
      }
    } finally {
      this.setData({ submitting: false })
    }
  },

  goLogin() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      wx.navigateBack({ delta: 1 })
    } else {
      wx.redirectTo({ url: '/pages/login/index' })
    }
  }
})
