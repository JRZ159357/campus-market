// 文件路径: miniprogram/pages/address/list.js
const { listAddresses, deleteAddress, setDefaultAddress } = require('../../api/address')

Page({
  data: {
    list: [],
    loading: true,
    empty: false,
    acting: false
  },

  onShow() {
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  async load() {
    this.setData({ loading: !this.loadedOnce })
    try {
      const list = await listAddresses()
      this.setData({ list, empty: list.length === 0 })
      this.loadedOnce = true
    } catch (e) {
      this.setData({ empty: true })
    } finally {
      this.setData({ loading: false })
    }
  },

  goAdd() {
    wx.navigateTo({ url: '/pages/address/edit' })
  },

  goEdit(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/address/edit?id=' + id })
  },

  async handleDefault(e) {
    const id = e.currentTarget.dataset.id
    const item = this.data.list.find(a => a.id === id)
    if (!item || item.isDefault || this.data.acting) return
    this.setData({ acting: true })
    try {
      await setDefaultAddress(id)
      wx.showToast({ title: '已设为默认', icon: 'success' })
      this.load()
    } catch (err) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ acting: false })
    }
  },

  handleDelete(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除地址',
      content: '确定删除这个地址吗？',
      confirmColor: '#FF6B35',
      success: async res => {
        if (!res.confirm) return
        try {
          await deleteAddress(id)
          wx.showToast({ title: '已删除', icon: 'success' })
          this.load()
        } catch (err) {
          // 错误提示由请求封装统一处理
        }
      }
    })
  }
})
