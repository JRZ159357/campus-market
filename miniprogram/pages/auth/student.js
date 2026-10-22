// 文件路径: miniprogram/pages/auth/student.js
const { getAuthStatus, applyStudentAuth } = require('../../api/user')
const { uploadImage } = require('../../utils/request')
const { BASE_URL } = require('../../config')

Page({
  data: {
    loading: true,
    submitting: false,
    uploading: false,

    verified: false,
    status: null,
    statusLabel: '未提交认证',
    rejectReason: '',
    applyInfo: null,

    form: {
      studentNo: '',
      realName: '',
      school: '北华大学',
      certImg: ''
    },
    certPreview: '',
    canSubmit: false
  },

  onLoad() {
    if (!getApp().isLogin()) {
      wx.redirectTo({ url: '/pages/login/index' })
      return
    }
    this.loadStatus()
  },

  onPullDownRefresh() {
    this.loadStatus().finally(() => wx.stopPullDownRefresh())
  },

  /**
   * 拉取认证状态：已认证 / 待审核 / 已驳回 / 未提交
   */
  async loadStatus() {
    this.setData({ loading: true })
    try {
      const data = await getAuthStatus()
      this.setData({
        verified: !!data.verified,
        status: data.status,
        statusLabel: data.statusLabel || '未提交认证',
        rejectReason: data.rejectReason || '',
        applyInfo: data,
        form: {
          ...this.data.form,
          studentNo: data.studentNo || '',
          realName: data.realName || '',
          school: data.school || '北华大学'
        }
      })
      this.checkCanSubmit()
    } catch (e) {
      // 统一提示
    } finally {
      this.setData({ loading: false })
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
    this.checkCanSubmit()
  },

  /**
   * 选择并上传学生证照片
   */
  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: async res => {
        const filePath = res.tempFiles[0].tempFilePath
        this.setData({ uploading: true, certPreview: filePath })
        try {
          const url = await uploadImage(filePath)
          this.setData({ 'form.certImg': url, certPreview: BASE_URL + url })
          wx.showToast({ title: '上传成功', icon: 'success' })
        } catch (e) {
          this.setData({ certPreview: '' })
        } finally {
          this.setData({ uploading: false })
          this.checkCanSubmit()
        }
      }
    })
  },

  previewCert() {
    if (!this.data.certPreview) return
    wx.previewImage({ urls: [this.data.certPreview] })
  },

  checkCanSubmit() {
    const { studentNo, realName, certImg } = this.data.form
    const valid =
      /^[0-9A-Za-z]{6,30}$/.test(studentNo) &&
      realName.trim().length > 0 &&
      !!certImg
    this.setData({ canSubmit: valid })
  },

  async handleSubmit() {
    if (!this.data.canSubmit || this.data.submitting) return

    const { studentNo, realName, school, certImg } = this.data.form
    if (!/^[0-9A-Za-z]{6,30}$/.test(studentNo)) {
      wx.showToast({ title: '学号格式不正确（6-30位数字或字母）', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      await applyStudentAuth({
        studentNo: studentNo.trim(),
        realName: realName.trim(),
        school: school.trim(),
        certImg
      })
      await this.loadStatus()
      getApp().setUserInfo({ studentVerified: 0 })
      wx.showModal({
        title: '提交成功',
        content: '认证申请已提交，管理员会在 1-2 个工作日内完成审核。',
        showCancel: false,
        confirmText: '知道了'
      })
    } catch (e) {
      // 统一提示
    } finally {
      this.setData({ submitting: false })
    }
  },

  goBack() {
    wx.navigateBack({ delta: 1, fail: () => wx.reLaunch({ url: '/pages/index/index' }) })
  }
})
