// 文件路径: miniprogram/pages/follow/index.js
const { pageFollowing, pageFollowers, toggleFollow } = require('../../api/follow')
const { imgUrl } = require('../../utils/format')

Page({
  data: {
    tab: 0,
    list: [],
    loading: true,
    loadingMore: false,
    hasMore: false,
    followingTotal: 0,
    followersTotal: 0,
    actionId: null
  },

  /**
   * 支持从外部指定初始标签：个人中心的「关注 / 粉丝」统计行会跳过来，
   * tab=0 关注、tab=1 粉丝。
   */
  onLoad(options) {
    if (options && options.tab !== undefined) {
      const tab = Number(options.tab)
      if (tab === 0 || tab === 1) {
        this.setData({ tab })
      }
    }
  },

  onShow() {
    if (!getApp().isLogin()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    this.refresh()
  },

  onPullDownRefresh() {
    this.refresh().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loading || this.data.loadingMore) return
    this.fetch(this.pageNum + 1, false)
  },

  async refresh() {
    this.pageNum = 1
    await this.fetch(1, true)
  },

  async fetch(pageNum, replace) {
    this.setData(replace ? { loading: true } : { loadingMore: true })
    const tab = this.data.tab
    try {
      const data = tab === 0
        ? await pageFollowing(pageNum, 10)
        : await pageFollowers(pageNum, 10)
      const records = (data.records || []).map(this.decorate)
      const total = data.total || 0
      this.setData({
        list: replace ? records : this.data.list.concat(records),
        hasMore: (data.current || pageNum) * (data.size || 10) < total,
        followingTotal: tab === 0 ? total : this.data.followingTotal,
        followersTotal: tab === 1 ? total : this.data.followersTotal
      })
      this.pageNum = pageNum
    } catch (e) {
      this.setData({ hasMore: false })
    } finally {
      this.setData({ loading: false, loadingMore: false })
    }
  },

  /** 头像地址补全、昵称首字、认证标识等展示字段 */
  decorate(user) {
    return {
      ...user,
      avatarUrl: imgUrl(user.avatar),
      initial: (user.nickname || 'U').slice(0, 1),
      fansText: user.fansCount || 0,
      onSaleText: user.onSaleCount || 0
    }
  },

  switchTab(e) {
    const tab = Number(e.currentTarget.dataset.tab)
    if (tab === this.data.tab) return
    this.setData({ tab, list: [], loading: true })
    this.refresh()
  },

  /** 关注 / 取消关注 / 回关，三种操作共用同一接口 */
  async onToggle(e) {
    const userId = Number(e.currentTarget.dataset.id)
    if (!userId || this.data.actionId) return
    this.setData({ actionId: userId })
    try {
      const state = await toggleFollow(userId)
      wx.showToast({ title: state.followed ? '已关注' : '已取消关注', icon: 'none' })
      this.applyState(userId, state)
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ actionId: null })
    }
  },

  applyState(userId, state) {
    const tab = this.data.tab
    const list = this.data.list
    const index = list.findIndex(item => item.userId === userId)
    if (index < 0) return

    if (tab === 0 && !state.followed) {
      // 关注列表里取消关注后，该用户不应继续留在列表中
      const followingTotal = Math.max(this.data.followingTotal - 1, 0)
      this.setData({
        list: list.filter(item => item.userId !== userId),
        followingTotal
      })
      return
    }
    this.setData({
      [`list[${index}].followed`]: state.followed,
      [`list[${index}].fansCount`]: state.fansCount,
      [`list[${index}].fansText`]: state.fansCount || 0
    })
  },

  /** 跳到卖家主页（模块 8 提供，免登录可看） */
  goSeller(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '/pages/user/home?id=' + id })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  }
})
