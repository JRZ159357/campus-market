// 文件路径: miniprogram/pages/address/edit.js
const { listAddresses, createAddress, updateAddress } = require('../../api/address')

Page({
  data: {
    id: 0,
    isEdit: false,
    loading: false,
    saving: false,
    form: {
      receiverName: '',
      phone: '',
      address: '',
      isDefault: false
    },
    errors: {}
  },

  onLoad(options) {
    const id = Number(options.id || 0)
    if (!id) return
    this.setData({ id, isEdit: true, loading: true })
    this.loadExisting(id)
  },

  /** 没有单条地址接口，从列表中取出目标地址回填；一次请求即可，无需新增后端接口 */
  async loadExisting(id) {
    try {
      const list = await listAddresses()
      const target = list.find(item => item.id === id)
      if (!target) {
        wx.showToast({ title: '地址不存在', icon: 'none' })
        setTimeout(() => wx.navigateBack(), 600)
        return
      }
      this.setData({
        form: {
          receiverName: target.receiverName,
          phone: target.phone,
          address: target.address,
          isDefault: !!target.isDefault
        }
      })
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ loading: false })
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({
      [`form.${field}`]: e.detail.value,
      [`errors.${field}`]: ''
    })
  },

  onDefaultChange(e) {
    this.setData({ 'form.isDefault': e.detail.value })
  },

  /**
   * 前端校验只做"必填 + 格式"，长度上限与手机号规则和服务端保持一致，
   * 避免用户填完才被后端驳回。
   */
  validate() {
    const form = this.data.form
    const errors = {}
    const name = (form.receiverName || '').trim()
    const phone = (form.phone || '').trim()
    const address = (form.address || '').trim()

    if (!name) {
      errors.receiverName = '请填写收货人姓名'
    } else if (name.length > 20) {
      errors.receiverName = '姓名最多 20 个字'
    }
    if (!phone) {
      errors.phone = '请填写联系电话'
    } else if (!/^1[3-9]\d{9}$/.test(phone)) {
      errors.phone = '手机号格式不正确'
    }
    if (!address) {
      errors.address = '请填写详细地址'
    } else if (address.length > 120) {
      errors.address = '地址最多 120 个字'
    }
    this.setData({ errors })
    return Object.keys(errors).length === 0
  },

  async save() {
    if (this.data.saving) return
    if (!this.validate()) return
    const form = this.data.form
    const payload = {
      receiverName: form.receiverName.trim(),
      phone: form.phone.trim(),
      address: form.address.trim(),
      isDefault: !!form.isDefault
    }
    this.setData({ saving: true })
    try {
      if (this.data.isEdit) {
        await updateAddress(this.data.id, payload)
      } else {
        await createAddress(payload)
      }
      wx.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ saving: false })
    }
  }
})
