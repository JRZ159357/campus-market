// 文件路径: miniprogram/pages/profile/index.js
const { getProfile, logout } = require('../../api/user')
const { imgUrl } = require('../../utils/format')

/**
 * 功能宫格。key 与 onMenuTap 中的分支一一对应。
 * bg 是图标底色，用 pastel 色区分不同功能，让宫格不至于全是同一种橙。
 */
const MENU_ITEMS = [
  { key: 'publish', icon: '📦', label: '我的发布', bg: '#FFF1E8' },
  { key: 'favorite', icon: '⭐', label: '我的收藏', bg: '#FFF8E1' },
  { key: 'follow', icon: '👥', label: '我的关注', bg: '#E8F4FF' },
  { key: 'address', icon: '📍', label: '地址管理', bg: '#E9F7EF' },
  { key: 'security', icon: '🔐', label: '账号与安全', bg: '#F3E8FF' },
  { key: 'pending', icon: '✍️', label: '待评价', bg: '#FFECF0' },
  { key: 'auth', icon: '🎓', label: '学生认证', bg: '#E8F7F7' },
  { key: 'report', icon: '🚨', label: '我的举报', bg: '#FFEDF0' },
  { key: 'contact', icon: '💬', label: '联系客服', bg: '#FFF0F3' },
  { key: 'about', icon: 'ℹ️', label: '关于我们', bg: '#F0F2F5' },
  { key: 'setting', icon: '⚙️', label: '设置', bg: '#F5F0FF' }
]

Page({
  data: {
    logged: false,
    loading: true,
    profile: null,
    verified: false,
    avatarText: 'U',
    avatarUrl: '',
    orderEntries: [],
    menuItems: MENU_ITEMS
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 4 })
      this.getTabBar().refresh()
    }
    this.loadProfile()
  },

  onPullDownRefresh() {
    this.loadProfile().finally(() => wx.stopPullDownRefresh())
  },

  async loadProfile() {
    const app = getApp()
    const logged = app.isLogin()
    this.setData({ logged })
    if (!logged) {
      this.setData({
        loading: false,
        profile: null,
        verified: false,
        orderEntries: [],
        avatarText: 'U',
        avatarUrl: ''
      })
      app.globalData.verified = false
      return
    }
    this.setData({ loading: true })
    try {
      // 一次聚合接口拿到个人中心全部数据：用户信息 / 信誉分 / 统计数 / 订单数
      const profile = await getProfile()
      app.setUserInfo(profile)
      this.setData({
        profile,
        avatarText: (profile.nickname || '微').slice(0, 1),
        avatarUrl: imgUrl(profile.avatar),
        orderEntries: this.buildOrderEntries(profile.orderCount || {}, profile.pendingReviewCount || 0),
        // 认证状态直接取自聚合接口里的 studentVerified，不再单独发一次请求
        verified: profile.studentVerified === 1
      })
      app.globalData.verified = profile.studentVerified === 1
    } catch (e) {
      // token 失效由请求封装统一处理
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * 我的订单入口的 5 个状态图标。
   * <p>
   * 角标数来自聚合接口里的 orderCount，接口失败时全部为 0（不显示角标）。
   * 「待评价」这一项取的是 pendingReviewCount 而不是 orderCount.finished：
   * finished 是全部已完成订单，其中大部分已经评价过了，
   * 用它当「待评价」角标会一直显示一个偏大的数字，点了进去却是空的。
   */
  buildOrderEntries(count, pendingReviewCount) {
    const c = count || {}
    return [
      { status: 0, icon: '💰', label: '待付款', badge: c.pendingPay || 0 },
      { status: 1, icon: '📦', label: '待发货', badge: c.pendingShip || 0 },
      { status: 2, icon: '🚚', label: '待收货', badge: c.pendingReceive || 0 },
      { status: 3, icon: '✅', label: '待评价', badge: pendingReviewCount || 0, nav: 'pending' },
      { status: 5, icon: '↩️', label: '退款售后', badge: c.afterSale || 0 }
    ]
  },

  /**
   * 统计行点击。不同类型跳到不同的列表页。
   */
  goStat(e) {
    if (!this.data.logged) {
      this.goLogin()
      return
    }
    const type = e.currentTarget.dataset.type
    if (type === 'favorite') {
      this.goFavorite()
    } else if (type === 'publish' || type === 'sold') {
      this.goMyProducts(type === 'sold' ? 3 : null)
    } else {
      this.goFollow(type === 'fans' ? 'fans' : 'following')
    }
  },

  /**
   * 功能宫格点击。未登录时统一跳登录页，不做逐项禁用判断——
   * 让用户点得到反馈（提示登录）比点了没反应体验更好。
   */
  onMenuTap(e) {
    const key = e.currentTarget.dataset.key
    if (!this.data.logged) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => this.goLogin(), 600)
      return
    }
    switch (key) {
      case 'publish':
        this.goMyProducts()
        break
      case 'favorite':
        this.goFavorite()
        break
      case 'follow':
        this.goFollow('following')
        break
      case 'address':
        wx.navigateTo({ url: '/pages/address/list' })
        break
      case 'security':
        wx.navigateTo({ url: '/pages/profile/security' })
        break
      case 'pending':
        wx.navigateTo({ url: '/pages/review/pending' })
        break
      case 'auth':
        wx.navigateTo({ url: '/pages/auth/student' })
        break
      case 'report':
        wx.navigateTo({ url: '/pages/report/my' })
        break
      case 'contact':
        this.contactService()
        break
      case 'about':
        wx.navigateTo({ url: '/pages/profile/about' })
        break
      case 'setting':
        wx.navigateTo({ url: '/pages/profile/setting' })
        break
      default:
        break
    }
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/index' })
  },

  goEdit() {
    if (!this.data.logged) {
      this.goLogin()
      return
    }
    wx.navigateTo({ url: '/pages/profile/edit' })
  },

  goMyProducts(status) {
    if (!this.data.logged) {
      this.goLogin()
      return
    }
    const url = status === null || status === undefined
      ? '/pages/product/my'
      : '/pages/product/my?status=' + status
    wx.navigateTo({ url })
  },

  goSecurity() {
    if (!this.data.logged) {
      this.goLogin()
      return
    }
    wx.navigateTo({ url: '/pages/profile/security' })
  },

  goFavorite() {
    if (!this.data.logged) {
      this.goLogin()
      return
    }
    wx.navigateTo({ url: '/pages/favorite/index' })
  },

  /**
   * 关注 / 粉丝列表。follow 页面用 tab 区分两者，tab 参数用 0/1。
   */
  goFollow(tab) {
    if (!this.data.logged) {
      this.goLogin()
      return
    }
    wx.navigateTo({ url: '/pages/follow/index?tab=' + (tab === 'fans' ? 1 : 0) })
  },

  /**
   * 跳订单列表。status 为空表示"全部"；
   * 具体状态由订单列表页 onLoad 读取后自动选中对应标签。
   */
  goOrderList(e) {
    if (!this.data.logged) {
      this.goLogin()
      return
    }
    const status = e && e.currentTarget.dataset.status
    // 待评价单独走待评价页：订单列表的"已完成"里混着已评价过的订单，
    // 角标显示的是"还没评价"的数，点进去却看到全部已完成订单会对不上
    if (status === 'pending') {
      wx.navigateTo({ url: '/pages/review/pending' })
      return
    }
    const url = status === '' || status === undefined
      ? '/pages/order/list'
      : '/pages/order/list?status=' + status
    wx.navigateTo({ url })
  },

  goAddress() {
    if (!this.data.logged) {
      this.goLogin()
      return
    }
    wx.navigateTo({ url: '/pages/address/list' })
  },

  /**
   * 联系客服：给出工作时间与联系方式。
   */
  contactService() {
    wx.showModal({
      title: '联系客服',
      content: '客服工作时间 9:00 - 21:00\n邮箱：support@campus-market.local',
      showCancel: false,
      confirmText: '知道了'
    })
  },

  handleLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: async res => {
        if (!res.confirm) return
        try {
          await logout()
        } catch (e) {
          // 即使接口失败也清理本地登录态
        }
        getApp().clearLogin()
        getApp().globalData.verified = false
        this.setData({ logged: false, profile: null, verified: false })
        wx.showToast({ title: '已退出登录', icon: 'none' })
      }
    })
  }
})
