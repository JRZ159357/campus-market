// 文件路径: miniprogram/pages/notice/detail.js
const { request } = require('../../utils/request')
const { readNotice } = require('../../api/notice')
const { timeAgo } = require('../../utils/format')

/**
 * 通知详情。
 * <p>
 * 详情接口返回的 target 已经由服务端算好跳转目标（order / product / none），
 * 页面只按 target 决定是否显示跳转按钮，不需要再判断一遍通知类型。
 * 打开即标记已读：通知的价值在于「看到」，用户点进来就说明看到了。
 */
Page({
  data: {
    id: 0,
    notice: null,
    loading: true,
    timeText: ''
  },

  onLoad(options) {
    const id = Number(options.id || 0)
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' })
      return
    }
    this.setData({ id })
    this.loadDetail(id)
  },

  loadDetail(id) {
    request({ url: '/api/notice/' + id })
      .then(notice => {
        this.setData({
          notice,
          loading: false,
          timeText: timeAgo(notice.createTime),
          icon: this.iconOf(notice.type)
        })
        if (notice.isRead === 0) {
          readNotice(id).catch(() => {})
        }
      })
      .catch(() => this.setData({ loading: false }))
  },

  iconOf(type) {
    switch (Number(type)) {
      case 1:
        return '📦'
      case 2:
        return '✅'
      case 4:
        return '⚠️'
      default:
        return '📢'
    }
  },

  /** 按 target 跳转：订单通知跳订单详情，商品通知跳商品详情 */
  goRelated() {
    const notice = this.data.notice
    if (!notice) return
    if (notice.target === 'order' && notice.relatedNo) {
      wx.navigateTo({ url: '/pages/order/detail?orderNo=' + notice.relatedNo })
      return
    }
    if (notice.target === 'product' && notice.relatedId) {
      wx.navigateTo({ url: '/pages/product/detail?id=' + notice.relatedId })
      return
    }
    wx.showToast({ title: '该通知没有关联内容', icon: 'none' })
  },

  goBack() {
    wx.navigateBack({
      fail: () => wx.switchTab({ url: '/pages/message/index' })
    })
  }
})
