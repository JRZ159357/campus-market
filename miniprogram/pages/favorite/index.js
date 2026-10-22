// 文件路径: miniprogram/pages/favorite/index.js
const { pageMyFavorites, toggleFavorite } = require('../../api/favorite')
const { splitColumns } = require('../../utils/waterfall')

Page({
  data: {
    left: [],
    right: [],
    count: 0,
    hasMore: false,
    loading: true,
    loadingMore: false
  },

  onShow() {
    if (!getApp().isLogin()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    const userInfo = getApp().globalData.userInfo || {}
    this.setData({ count: userInfo.favoriteCount || 0 })
    // 每次进入都重新拉第一页：用户可能在详情页取消了收藏，缓存列表会失真
    this.refresh()
  },

  onPullDownRefresh() {
    this.refresh().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadMore()
  },

  async refresh() {
    this.cursor = null
    await this.fetch(true)
  },

  async fetch(replace) {
    this.setData(replace ? { loading: true } : { loadingMore: true })
    try {
      const data = await pageMyFavorites({ cursor: replace ? null : this.cursor, size: 10 })
      const records = data.records || []
      const columns = replace
        ? splitColumns(records)
        : splitColumns(records, this.data.left, this.data.right)
      this.cursor = data.nextCursor || null
      this.setData({
        left: columns.left,
        right: columns.right,
        hasMore: !!data.hasMore
      })
    } catch (e) {
      this.setData({ hasMore: false })
    } finally {
      this.setData({ loading: false, loadingMore: false })
    }
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loading || this.data.loadingMore) return
    this.fetch(false)
  },

  /** 长按商品卡片：弹出取消收藏操作 */
  onLongPress(e) {
    const productId = Number(e.currentTarget.dataset.id)
    if (!productId) return
    wx.showActionSheet({
      itemList: ['取消收藏'],
      success: res => {
        if (res.tapIndex === 0) {
          this.cancel(productId)
        }
      }
    })
  },

  /** 已售/已下架商品点击给出解释，避免跳到详情页看到报错 */
  onMaskTap(e) {
    const status = Number(e.currentTarget.dataset.status)
    const text = status === 3 ? '该商品已售出' : '该商品已下架'
    wx.showToast({ title: text, icon: 'none' })
  },

  async cancel(productId) {
    try {
      const state = await toggleFavorite(productId)
      wx.showToast({ title: state.favorited ? '收藏成功' : '已取消收藏', icon: 'none' })
      this.removeLocal(productId)
    } catch (e) {
      // 失败提示由请求封装统一处理
    }
  },

  removeLocal(productId) {
    const left = this.data.left.filter(item => item.id !== productId)
    const right = this.data.right.filter(item => item.id !== productId)
    const count = Math.max(this.data.count - 1, 0)
    this.setData({ left, right, count })
    const app = getApp()
    app.setUserInfo({ favoriteCount: count })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  }
})
