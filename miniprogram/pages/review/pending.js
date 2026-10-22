// 文件路径: miniprogram/pages/review/pending.js
const { pagePendingReviews } = require('../../api/review')
const { imgUrl, money, timeAgo } = require('../../utils/format')

/**
 * 待评价列表。
 * <p>
 * 「待评价」的判定在服务端：订单已完成 + 我这一侧的评价标记还是 0。
 * 前端不参与判断，避免出现「前端认为该评价、后端拒绝」的错位。
 */
Page({
  data: {
    list: [],
    pageNum: 1,
    pageSize: 10,
    total: 0,
    hasMore: true,
    loading: false,
    loaded: false
  },

  onLoad() {
    wx.setNavigationBarTitle({ title: '待评价' })
  },

  onShow() {
    // 从评价页返回时列表可能已变化，每次显示都重新拉第一页
    this.loadList(true)
  },

  onPullDownRefresh() {
    this.loadList(true).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadList(false)
    }
  },

  async loadList(reset) {
    if (this.data.loading) return
    if (reset) {
      this.setData({ pageNum: 1, hasMore: true })
    }
    this.setData({ loading: true })
    try {
      const data = await pagePendingReviews({
        pageNum: this.data.pageNum,
        pageSize: this.data.pageSize
      })
      const records = (data.records || []).map(item => Object.assign({}, item, {
        cover: imgUrl(item.productCover),
        price: money(item.price),
        timeText: timeAgo(item.finishTime),
        peerLabel: item.reviewType === 1 ? '卖家' : '买家'
      }))
      this.setData({
        list: reset ? records : this.data.list.concat(records),
        total: data.total || 0,
        pageNum: this.data.pageNum + 1,
        hasMore: (data.records || []).length >= this.data.pageSize,
        loaded: true
      })
    } catch (e) {
      this.setData({ hasMore: false, loaded: true })
    } finally {
      this.setData({ loading: false })
    }
  },

  goReview(e) {
    const orderId = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/review/edit?orderId=' + orderId })
  },

  goOrder(e) {
    const orderNo = e.currentTarget.dataset.no
    wx.navigateTo({ url: '/pages/order/detail?orderNo=' + orderNo })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  }
})
