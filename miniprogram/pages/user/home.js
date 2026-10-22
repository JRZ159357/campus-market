// 文件路径: miniprogram/pages/user/home.js
const { getUserHome, getUserProducts } = require('../../api/user')
const { toggleFollow } = require('../../api/follow')
const { createSession } = require('../../api/chat')
const { pageUserReviews } = require('../../api/review')
const { imgUrl, money, timeAgo } = require('../../utils/format')
const { splitColumns } = require('../../utils/waterfall')

Page({
  data: {
    userId: null,
    loading: true,
    home: null,
    avatarUrl: '',
    avatarText: 'U',
    // Tab：0 在售商品 1 评价
    activeTab: 0,
    tabs: [
      { label: '在售商品', value: 0 },
      { label: '评价', value: 1 }
    ],
    // 瀑布流左右两列
    left: [],
    right: [],
    productPageNum: 1,
    productTotal: 0,
    hasMore: true,
    loadingMore: false,
    // 评价
    reviews: [],
    reviewPageNum: 1,
    reviewHasMore: true,
    reviewLoading: false,
    following: false,
    chatLoading: false
  },

  onLoad(options) {
    const userId = Number(options && (options.id || options.userId))
    if (!userId) {
      wx.showToast({ title: '用户不存在', icon: 'none' })
      return
    }
    this.setData({ userId })
    this.loadHome()
  },

  onShow() {
    // 从商品详情跳过来时关注状态可能已变，回来刷新一次
    if (this.data.userId && !this.data.loading) {
      this.loadHome()
    }
  },

  onPullDownRefresh() {
    const task = this.data.activeTab === 0
      ? this.loadProducts(1, true)
      : this.loadReviews(1, true)
    task.finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.activeTab !== 0) {
      if (this.data.reviewHasMore && !this.data.reviewLoading) {
        this.loadReviews(this.data.reviewPageNum + 1, false)
      }
      return
    }
    if (!this.data.hasMore || this.data.loadingMore) return
    this.loadProducts(this.data.productPageNum + 1, false)
  },

  async loadHome() {
    this.setData({ loading: true })
    try {
      const home = await getUserHome(this.data.userId)
      this.setData({
        home,
        avatarUrl: imgUrl(home.avatar),
        avatarText: (home.nickname || 'U').slice(0, 1),
        following: !!home.followed
      })
      wx.setNavigationBarTitle({ title: (home.nickname || '校园用户') + '的主页' })
      if (this.data.activeTab === 0) {
        await this.loadProducts(1, true)
      }
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * 切换 Tab。在售商品与评价各自独立分页，切回来时保留已加载的数据。
   */
  onTabChange(e) {
    const tab = Number(e.currentTarget.dataset.tab)
    if (tab === this.data.activeTab) return
    this.setData({ activeTab: tab })
    if (tab === 0) {
      if (!this.data.left.length && !this.data.right.length) {
        this.loadProducts(1, true)
      }
    } else if (!this.data.reviews.length) {
      this.loadReviews(1, true)
    }
  },

  async loadProducts(pageNum, reset) {
    this.setData({ loadingMore: !reset })
    try {
      const data = await getUserProducts(this.data.userId, {
        pageNum,
        pageSize: 10
      })
      const records = (data.records || []).map(item => ({
        ...item,
        cover: imgUrl(item.cover),
        priceText: money(item.price)
      }))
      const base = reset ? [] : this.data.left.concat(this.data.right)
      const columns = splitColumns(records, reset ? [] : this.data.left, reset ? [] : this.data.right)
      const total = Number(data.total || 0)
      this.setData({
        left: columns.left,
        right: columns.right,
        productTotal: total,
        productPageNum: pageNum,
        hasMore: base.length + records.length < total
      })
    } catch (e) {
      if (reset) {
        this.setData({ left: [], right: [], productTotal: 0, hasMore: false })
      }
    } finally {
      this.setData({ loadingMore: false })
    }
  },

  /**
   * 加载收到的评价。复用评价列表接口，匿名评价由服务端脱敏成「匿名用户」，
   * 前端不做匿名判断——否则接口一旦泄露就能看到实名。
   */
  async loadReviews(pageNum, reset) {
    if (this.data.reviewLoading) return
    this.setData({ reviewLoading: true })
    try {
      const data = await pageUserReviews(this.data.userId, {
        pageNum: pageNum || 1,
        pageSize: 20
      })
      const records = (data.records || []).map(item => ({
        ...item,
        avatar: item.reviewerAvatar ? imgUrl(item.reviewerAvatar) : '',
        avatarText: (item.reviewerNickname || '匿').slice(0, 1),
        images: (item.images || []).map(imgUrl),
        timeText: timeAgo(item.createTime)
      }))
      const total = Number(data.total || 0)
      const list = reset ? records : this.data.reviews.concat(records)
      this.setData({
        reviews: list,
        reviewPageNum: pageNum || 1,
        reviewHasMore: list.length < total
      })
    } catch (e) {
      if (reset) {
        this.setData({ reviews: [], reviewHasMore: false })
      }
    } finally {
      this.setData({ reviewLoading: false })
    }
  },

  /**
   * 关注 / 取消关注。未登录时引导登录。
   */
  async handleFollow() {
    if (!getApp().isLogin()) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => wx.navigateTo({ url: '/pages/login/index' }), 600)
      return
    }
    if (this.data.home && this.data.home.isSelf) {
      wx.showToast({ title: '不能关注自己', icon: 'none' })
      return
    }
    try {
      const state = await toggleFollow(this.data.userId)
      this.setData({ following: !!state.followed })
      wx.showToast({ title: state.followed ? '已关注' : '已取消关注', icon: 'none' })
    } catch (e) {
      // 错误提示由请求封装统一处理
    }
  },

  /**
   * 私信。聊天会话按「商品 + 对方」唯一，这里没有商品上下文，
   * 传 sellerId 让后端建一条与该卖家的会话。
   */
  async handleChat() {
    if (!getApp().isLogin()) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => wx.navigateTo({ url: '/pages/login/index' }), 600)
      return
    }
    if (this.data.chatLoading) return
    this.setData({ chatLoading: true })
    try {
      const session = await createSession({ sellerId: this.data.userId })
      wx.navigateTo({ url: '/pages/chat/index?sessionId=' + session.id })
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ chatLoading: false })
    }
  },

  goProduct(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/product/detail?id=' + id })
  },

  /**
   * 更多操作：举报该用户 / 我的举报。
   * <p>
   * 同样放在「更多」里而不是页面显眼位置，理由与商品详情一致：
   * 举报是低频且需要理由的动作，一键直达会让队列里多出一堆
   * 「不喜欢这个卖家」式的无效举报。
   */
  showMoreActions() {
    if (!getApp().isLogin()) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => wx.navigateTo({ url: '/pages/login/index' }), 600)
      return
    }
    if (this.data.home && this.data.home.isSelf) {
      wx.navigateTo({ url: '/pages/report/my' })
      return
    }
    wx.showActionSheet({
      itemList: ['举报该用户（欺诈 / 辱骂骚扰 / 广告导流）', '我的举报记录'],
      success: res => {
        if (res.tapIndex === 0) {
          wx.navigateTo({ url: '/pages/report/index?type=2&targetId=' + this.data.userId })
        } else {
          wx.navigateTo({ url: '/pages/report/my' })
        }
      }
    })
  },

  /** 跳到评价人主页 */
  goReviewer(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '/pages/user/home?id=' + id })
  },

  previewImage(e) {
    const urls = e.currentTarget.dataset.urls
    const current = e.currentTarget.dataset.url
    wx.previewImage({ urls, current })
  }
})
