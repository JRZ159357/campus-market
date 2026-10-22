// 文件路径: miniprogram/pages/index/index.js
const { getHomeIndex } = require('../../api/home')
const { getAuthStatus } = require('../../api/user')
const { feedProducts } = require('../../api/product')
const { splitColumns } = require('../../utils/waterfall')
const { imgUrl, money } = require('../../utils/format')

Page({
  data: {
    loading: true,
    error: false,
    // 公告：后端即使关闭也会返回对象，这里只保留「要展示」的那份
    notice: null,
    // 关闭状态记在 globalData 里而不是 storage：需求是「本次会话不再显示」，
    // 存 storage 会变成永久关闭，运营换个公告用户也看不见
    noticeClosed: false,
    banners: [],
    categories: [],
    hotProducts: [],
    left: [],
    right: [],
    cursor: null,
    hasMore: false,
    loadingMore: false,
    hotWord: '校园好物'
  },

  onLoad() {
    this.setData({ noticeClosed: !!getApp().globalData.noticeClosed })
    this.loadHome()
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 })
      this.getTabBar().refresh()
    }
    this.refreshAuth()
  },

  onPullDownRefresh() {
    this.loadHome().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadMore()
  },

  /**
   * 首屏只发一个聚合请求：公告、轮播图、金刚区、热门、新品一起拿到。
   * 新品推荐的前 N 条同时作为瀑布流第一页，cursor 取其最后一条 id。
   */
  async loadHome() {
    this.setData({ loading: true, error: false })
    try {
      const data = (await getHomeIndex()) || {}

      // 轮播图：后端返回的是 /uploads/xxx 相对地址，直接给 image 组件加载不出来，
      // 必须补全域名（模块12 之前这里漏了，只有商品图做了补全）
      const banners = (data.banners || []).map(item => ({
        ...item,
        imageFull: imgUrl(item.imageUrl)
      }))

      const categories = this.buildCategories(data.categories || [])

      const hotProducts = (data.hotProducts || []).map(item => ({
        ...item,
        coverFull: imgUrl(item.cover),
        priceText: money(item.price)
      }))

      const newProducts = data.newProducts || []
      const columns = splitColumns(newProducts)

      const rawNotice = data.notice || null
      const notice =
        rawNotice && rawNotice.enabled === 1 && (rawNotice.text || '').trim() ? rawNotice : null

      this.setData({
        notice,
        banners,
        categories,
        hotProducts,
        left: columns.left,
        right: columns.right,
        cursor: newProducts.length ? newProducts[newProducts.length - 1].id : null,
        hasMore: newProducts.length > 0,
        hotWord: (data.hotKeywords && data.hotKeywords[0]) || '校园好物'
      })
    } catch (e) {
      this.setData({ error: true })
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * 金刚区固定 4 列：首页最多放 7 个分类 + 「更多」。
   * 显示哪些、什么顺序由管理端「金刚区配置」决定，前端不再自己截断排序。
   */
  buildCategories(list) {
    const items = list.slice(0, 7).map(item => ({
      id: item.id,
      name: item.name,
      icon: item.icon || '📦',
      isImage: !!item.icon && /^(\/|https?:)/.test(item.icon),
      iconFull: imgUrl(item.icon)
    }))
    if (list.length > 7) {
      items.push({ id: null, name: '更多', icon: '⋯', isImage: false, isMore: true })
    }
    return items
  },

  async loadMore() {
    if (!this.data.hasMore || this.data.loadingMore || this.data.loading) return
    this.setData({ loadingMore: true })
    try {
      const feed = await feedProducts({ cursor: this.data.cursor, size: 10 })
      const records = feed.records || []
      const columns = splitColumns(records, this.data.left, this.data.right)
      this.setData({
        left: columns.left,
        right: columns.right,
        cursor: feed.nextCursor,
        hasMore: !!feed.hasMore
      })
    } catch (e) {
      // 错误提示由请求封装统一处理，保留已加载数据
    } finally {
      this.setData({ loadingMore: false })
    }
  },

  /** 同步认证状态给 tabBar 的发布入口使用 */
  async refreshAuth() {
    const app = getApp()
    if (!app.isLogin()) {
      app.globalData.verified = false
      return
    }
    try {
      const status = await getAuthStatus()
      app.globalData.verified = !!(status && status.verified)
    } catch (e) {
      // 查询失败不阻断首页
    }
  },

  goSearch() {
    wx.navigateTo({ url: '/pages/search/index' })
  },

  goMessages() {
    wx.switchTab({ url: '/pages/message/index' })
  },

  goDetail(e) {
    const id = Number(e.currentTarget.dataset.id)
    if (id) {
      wx.navigateTo({ url: '/pages/product/detail?id=' + id })
    }
  },

  goCategory(e) {
    const more = !!e.currentTarget.dataset.more
    const id = Number(e.currentTarget.dataset.id)
    if (more) {
      wx.navigateTo({ url: '/pages/product/list' })
      return
    }
    // switchTab 不支持传参，用 globalData 传递待选分类
    getApp().globalData.pendingCategoryId = id || null
    wx.switchTab({ url: '/pages/category/index' })
  },

  /** 公告点击：有链接就复制（小程序内打不开外部网页），没有就什么都不做 */
  onNoticeTap() {
    const notice = this.data.notice
    if (!notice || !notice.link) return
    wx.setClipboardData({
      data: notice.link,
      success: () => wx.showToast({ title: '活动链接已复制', icon: 'none' })
    })
  },

  /** 关闭公告：本次会话不再显示，重新打开小程序会再次出现 */
  onNoticeClose() {
    getApp().globalData.noticeClosed = true
    this.setData({ noticeClosed: true })
  },

  onBannerTap(e) {
    const banner = this.data.banners[Number(e.currentTarget.dataset.index)]
    if (!banner) return
    if (banner.linkType === 1) {
      wx.navigateTo({ url: '/pages/product/detail?id=' + banner.linkValue })
      return
    }
    if (banner.linkType === 2) {
      getApp().globalData.pendingCategoryId = Number(banner.linkValue)
      wx.switchTab({ url: '/pages/category/index' })
      return
    }
    if (banner.linkType === 3) {
      // 外部链接：小程序内不能直接打开外部网页，复制链接更稳妥
      wx.setClipboardData({
        data: banner.linkValue,
        success: () => wx.showToast({ title: '链接已复制', icon: 'none' })
      })
      return
    }
    // linkType === 4（无跳转）：纯展示海报，点击不做任何事
  },

  retry() {
    this.loadHome()
  }
})
