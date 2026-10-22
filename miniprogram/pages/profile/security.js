// 文件路径: miniprogram/pages/profile/security.js
const { getMe, bindAccount, changePassword, bindPhone, getAuthStatus, updatePhone } = require('../../api/user')

const USERNAME_RE = /^[A-Za-z0-9]{4,20}$/

/** 学生认证状态文案 */
const AUTH_STATUS = {
  verified: { label: '已认证', className: 'auth__state--pass' },
  pending: { label: '审核中', className: 'auth__state--wait' },
  none: { label: '未认证', className: 'auth__state--none' }
}

Page({
  data: {
    loading: true,
    userInfo: null,
    hasAccount: false,
    isWechatUser: false,
    phoneMask: '',
    bindingPhone: false,
    mode: '', // '' | bind | change
    // 绑定账号表单
    username: '',
    password: '',
    confirmPassword: '',
    // 修改密码表单
    oldPassword: '',
    newPassword: '',
    confirmNew: '',
    showPassword: false,
    focused: '',
    submitting: false,
    errors: {},
    // 学生认证
    authStatus: 'none',
    authStatusLabel: '未认证',
    authStatusClass: 'auth__state--none',
    studentNo: '',
    authRejectReason: ''
  },

  onShow() {
    this.loadProfile()
  },

  async loadProfile() {
    if (!getApp().isLogin()) {
      wx.redirectTo({ url: '/pages/login/index' })
      return
    }
    this.setData({ loading: true })
    try {
      const me = await getMe()
      getApp().setUserInfo(me)
      this.setData({
        userInfo: me,
        hasAccount: !!me.hasAccount,
        isWechatUser: me.registerType !== 2,
        phoneMask: this.maskPhone(me.phone)
      })
    } catch (e) {
      // 登录失效由请求封装统一处理
    } finally {
      this.setData({ loading: false })
    }
    // 认证状态单独取，失败不影响其它内容展示
    this.loadAuthStatus()
  },

  /**
   * 学生认证状态。
   * <p>
   * 三个状态来自后端 AuthStatusVO：verified（已通过）、status=0（待审核）、
   * 无记录或被驳回（未认证）。学号只在已通过时展示——
   * 学号属于个人敏感信息，审核中不该出现在任何界面。
   */
  async loadAuthStatus() {
    try {
      const auth = await getAuthStatus()
      if (!auth) return
      if (auth.verified) {
        this.setData({
          authStatus: 'verified',
          authStatusLabel: AUTH_STATUS.verified.label,
          authStatusClass: AUTH_STATUS.verified.className,
          studentNo: auth.studentNo || '',
          authRejectReason: ''
        })
      } else if (Number(auth.status) === 0) {
        this.setData({
          authStatus: 'pending',
          authStatusLabel: AUTH_STATUS.pending.label,
          authStatusClass: AUTH_STATUS.pending.className,
          studentNo: '',
          authRejectReason: ''
        })
      } else {
        this.setData({
          authStatus: 'none',
          authStatusLabel: AUTH_STATUS.none.label,
          authStatusClass: AUTH_STATUS.none.className,
          studentNo: '',
          authRejectReason: auth.rejectReason || ''
        })
      }
    } catch (e) {
      // 认证状态获取失败不阻塞页面
    }
  },

  openBind() {
    this.setData({
      mode: this.data.mode === 'bind' ? '' : 'bind',
      username: '',
      password: '',
      confirmPassword: '',
      errors: {}
    })
  },

  openChange() {
    this.setData({
      mode: this.data.mode === 'change' ? '' : 'change',
      oldPassword: '',
      newPassword: '',
      confirmNew: '',
      errors: {}
    })
  },

  closeForm() {
    this.setData({ mode: '', errors: {} })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    if (!field) return
    this.setData({ [field]: e.detail.value, [`errors.${field}`]: '' })
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
   * 手机号脱敏展示
   */
  maskPhone(phone) {
    if (!phone || phone.length < 7) return phone || ''
    return phone.slice(0, 3) + '****' + phone.slice(-4)
  },

  /**
   * 手机号快捷绑定（button open-type="getPhoneNumber"）
   * mock 模式下后端会返回模拟号码，便于本地联调
   */
  async handleGetPhone(e) {
    const { code, errMsg } = e.detail || {}
    if (!code) {
      if (errMsg && errMsg.indexOf('deny') === -1) {
        wx.showToast({ title: '未获取到手机号授权', icon: 'none' })
      }
      return
    }
    this.setData({ bindingPhone: true })
    try {
      await bindPhone(code)
      wx.showToast({ title: '绑定成功', icon: 'success' })
      await this.loadProfile()
    } catch (err) {
      // 统一提示
    } finally {
      this.setData({ bindingPhone: false })
    }
  },

  /**
   * 修改手机号。
   * <p>
   * 与「首次绑定」走同一个微信授权按钮，但调用的是 PUT /api/user/phone：
   * 绑定接口是「没有就写上」，改绑接口要求「换成新的」并且校验唯一性。
   * 前端不传手机号字符串，服务端用授权码去换真实号码，
   * 避免有人直接把账号手机号改成自己的。
   */
  async handleChangePhone(e) {
    const { code, errMsg } = e.detail || {}
    if (!code) {
      if (errMsg && errMsg.indexOf('deny') === -1) {
        wx.showToast({ title: '未获取到手机号授权', icon: 'none' })
      }
      return
    }
    this.setData({ bindingPhone: true })
    try {
      await updatePhone(code)
      wx.showToast({ title: '手机号已更新', icon: 'success' })
      await this.loadProfile()
    } catch (err) {
      // 统一提示
    } finally {
      this.setData({ bindingPhone: false })
    }
  },

  /** 跳学生认证页 */
  goStudentAuth() {
    if (this.data.authStatus === 'verified') {
      wx.showToast({ title: '你已完成学生认证', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/auth/student' })
  },

  async handleBind() {
    const { username, password, confirmPassword } = this.data
    const errors = {}
    if (!USERNAME_RE.test(username)) {
      errors.username = '账号需为4-20位字母或数字'
    }
    if (password.length < 6 || password.length > 20) {
      errors.password = '密码长度需为6-20位'
    }
    if (confirmPassword !== password) {
      errors.confirmPassword = '两次输入的密码不一致'
    }
    if (Object.keys(errors).length) {
      this.setData({ errors })
      return
    }

    this.setData({ submitting: true, errors: {} })
    try {
      await bindAccount({ username, password })
      wx.showToast({ title: '绑定成功', icon: 'success' })
      this.setData({ mode: '' })
      await this.loadProfile()
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ submitting: false })
    }
  },

  async handleChange() {
    const { oldPassword, newPassword, confirmNew } = this.data
    const errors = {}
    if (!oldPassword) {
      errors.oldPassword = '请输入原密码'
    }
    if (newPassword.length < 6 || newPassword.length > 20) {
      errors.newPassword = '新密码长度需为6-20位'
    } else if (newPassword === oldPassword) {
      errors.newPassword = '新密码不能与原密码相同'
    }
    if (confirmNew !== newPassword) {
      errors.confirmNew = '两次输入的密码不一致'
    }
    if (Object.keys(errors).length) {
      this.setData({ errors })
      return
    }

    this.setData({ submitting: true, errors: {} })
    try {
      await changePassword({ oldPassword, newPassword })
      wx.showToast({ title: '密码修改成功', icon: 'success' })
      this.setData({ mode: '' })
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ submitting: false })
    }
  }
})
