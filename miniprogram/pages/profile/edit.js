// 文件路径: miniprogram/pages/profile/edit.js
const { getProfile, updateProfile } = require('../../api/user')
const { uploadImage } = require('../../utils/request')
const { imgUrl } = require('../../utils/format')

const NICKNAME_MAX = 20
const INTRO_MAX = 100

/** 性别选项。value 与后端 Gender 常量一一对应：0保密 1男 2女 */
const GENDERS = [
  { value: 0, label: '保密' },
  { value: 1, label: '男' },
  { value: 2, label: '女' }
]

Page({
  data: {
    loading: true,
    saving: false,
    avatar: '',
    avatarUrl: '',
    avatarText: 'U',
    nickname: '',
    gender: 0,
    genderIndex: 0,
    genderOptions: GENDERS,
    intro: '',
    nicknameCount: 0,
    introCount: 0,
    errors: {}
  },

  onLoad() {
    this.loadProfile()
  },

  async loadProfile() {
    this.setData({ loading: true })
    try {
      const profile = await getProfile()
      const gender = profile.gender === 1 || profile.gender === 2 ? profile.gender : 0
      this.setData({
        avatar: profile.avatar || '',
        avatarUrl: imgUrl(profile.avatar),
        avatarText: (profile.nickname || 'U').slice(0, 1),
        nickname: profile.nickname || '',
        gender,
        genderIndex: GENDERS.findIndex(item => item.value === gender),
        intro: profile.intro || '',
        nicknameCount: (profile.nickname || '').length,
        introCount: (profile.intro || '').length
      })
    } catch (e) {
      // 登录失效由请求封装统一处理
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * 选择头像。头像上传复用商品图与学生证的上传接口，
   * 拿回的是 /uploads/... 相对路径，保存时原样提交。
   */
  async chooseAvatar() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async res => {
        const file = res.tempFiles && res.tempFiles[0]
        if (!file) return
        wx.showLoading({ title: '上传中', mask: true })
        try {
          const url = await uploadImage(file.tempFilePath)
          this.setData({ avatar: url, avatarUrl: imgUrl(url) })
        } catch (e) {
          // 错误提示已由上传封装统一处理
        } finally {
          wx.hideLoading()
        }
      }
    })
  },

  onNicknameInput(e) {
    const value = e.detail.value
    this.setData({
      nickname: value,
      nicknameCount: value.length,
      'errors.nickname': this.nicknameError(value)
    })
  },

  onIntroInput(e) {
    const value = e.detail.value
    this.setData({
      intro: value,
      introCount: value.length,
      'errors.intro': this.introError(value)
    })
  },

  /**
   * 长度错误在输入时就提示，而不是等点保存才报。
   * input 的 maxlength 会挡住超长输入，但粘贴等场景仍可能触发，
   * 边输边提示比"保存后弹一个错"体验好。
   */
  nicknameError(value) {
    const v = (value || '').trim()
    if (!v) return '请输入昵称'
    if (v.length > NICKNAME_MAX) return '昵称不能超过 ' + NICKNAME_MAX + ' 个字'
    return ''
  },

  introError(value) {
    const v = (value || '').trim()
    if (v.length > INTRO_MAX) return '个人简介不能超过 ' + INTRO_MAX + ' 个字'
    return ''
  },

  onGenderChange(e) {
    const index = Number(e.detail.value)
    this.setData({
      genderIndex: index,
      gender: GENDERS[index].value
    })
  },

  validate() {
    const errors = {}
    const nicknameError = this.nicknameError(this.data.nickname)
    if (nicknameError) {
      errors.nickname = nicknameError
    }
    const introError = this.introError(this.data.intro)
    if (introError) {
      errors.intro = introError
    }
    this.setData({ errors })
    return !Object.keys(errors).length
  },

  async save() {
    if (!this.validate()) return
    this.setData({ saving: true })
    try {
      await updateProfile({
        nickname: this.data.nickname.trim(),
        avatar: this.data.avatar,
        gender: this.data.gender,
        intro: this.data.intro.trim()
      })
      wx.showToast({ title: '资料已更新', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 700)
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ saving: false })
    }
  }
})
