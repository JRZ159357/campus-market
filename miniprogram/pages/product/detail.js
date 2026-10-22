// 文件路径: miniprogram/pages/product/detail.js
const { getProduct } = require('../../api/product')
const { toggleFavorite } = require('../../api/favorite')
const { toggleFollow } = require('../../api/follow')
const { createSession } = require('../../api/chat')
const { pageProductReviews, getProductReviewStat } = require('../../api/review')
const { imgUrl, money, statusMeta, timeAgo } = require('../../utils/format')

Page({
  data: {
    loading: true,
    error: false,
    detail: null,
    current: 0,
    favAnim: false,
    favLoading: false,
    followLoading: false,
    // 评价区只展示摘要与最新两条，完整列表跳 pages/review/list
    reviewStat: { total: 0, avgStars: 0, goodRate: 0 },
    reviews: []
  },

  onLoad(options) {
    this.id = options.id
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  /**
   * 商品评价摘要。统计与列表两个请求并行发，
   * 任一失败都只是评价区不显示，不影响商品主体信息。
   */
  async loadReviews() {
    try {
      const [stat, page] = await Promise.all([
        getProductReviewStat(this.id),
        pageProductReviews(this.id, { pageNum: 1, pageSize: 2 })
      ])
      this.setData({
        reviewStat: stat || { total: 0, avgStars: 0, goodRate: 0 },
        reviews: (page.records || []).map(item => Object.assign({}, item, {
          avatar: item.reviewerAvatar ? imgUrl(item.reviewerAvatar) : '',
          avatarText: (item.reviewerNickname || '匿').slice(0, 1),
          images: (item.images || []).map(imgUrl),
          timeText: timeAgo(item.createTime)
        }))
      })
    } catch (e) {
      this.setData({ reviewStat: { total: 0, avgStars: 0, goodRate: 0 }, reviews: [] })
    }
  },

  goReviews() {
    const title = encodeURIComponent((this.data.detail && this.data.detail.title) || '')
    wx.navigateTo({ url: '/pages/review/list?productId=' + this.id + '&title=' + title })
  },

  previewReviewImage(e) {
    const index = Number(e.currentTarget.dataset.review)
    const imgIndex = Number(e.currentTarget.dataset.imgIndex)
    const review = this.data.reviews[index]
    if (!review || !review.images.length) return
    wx.previewImage({ current: review.images[imgIndex] || review.images[0], urls: review.images })
  },

  async load() {
    this.setData({ loading: true, error: false })
    try {
      const data = await getProduct(this.id)
      const meta = statusMeta(data.status)
      const images = (data.images && data.images.length ? data.images : []).map(imgUrl)
      const seller = data.seller || {}
      this.setData({
        detail: {
          ...data,
          images,
          priceText: money(data.price),
          originalPriceText: data.originalPrice ? money(data.originalPrice) : '',
          statusLabel: meta.label,
          timeText: timeAgo(data.createTime),
          favoriteCount: data.favoriteCount || 0,
          favorited: !!data.favorited,
          favLabel: this.favLabel(!!data.favorited, data.favoriteCount || 0),
          seller: {
            ...seller,
            avatarUrl: imgUrl(seller.avatar),
            initial: (seller.nickname || 'U').slice(0, 1),
            fansCount: seller.fansCount || 0,
            followed: !!seller.followed
          }
        },
        current: 0
      })
      this.loadReviews()
    } catch (e) {
      this.setData({ error: true })
    } finally {
      this.setData({ loading: false })
    }
  },

  /** 底部收藏文案：有收藏数时一并展示，避免单独占一行 */
  favLabel(favorited, count) {
    return count > 0 ? '收藏 ' + count : '收藏'
  },

  onSwiperChange(e) {
    this.setData({ current: e.detail.current })
  },

  previewImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    const urls = this.data.detail.images
    if (!urls.length) return
    wx.previewImage({ current: urls[index], urls })
  },

  retry() {
    this.load()
  },

  requireLogin() {
    if (getApp().isLogin()) return true
    wx.showToast({ title: '登录后才能操作', icon: 'none' })
    setTimeout(() => wx.navigateTo({ url: '/pages/login/index' }), 400)
    return false
  },

  /**
   * 收藏 / 取消收藏。
   * 服务端返回最新收藏态与收藏数，前端直接覆盖，避免再发一次详情请求；
   * 播放一次 scale 0.8 -> 1 的缩放动画。
   */
  async favorite() {
    if (!this.requireLogin() || this.data.favLoading) return
    this.setData({ favLoading: true, favAnim: true })
    try {
      const state = await toggleFavorite(this.data.detail.id)
      const count = state.favoriteCount || 0
      this.setData({
        'detail.favorited': !!state.favorited,
        'detail.favoriteCount': count,
        'detail.favLabel': this.favLabel(!!state.favorited, count)
      })
      wx.showToast({ title: state.favorited ? '收藏成功' : '已取消收藏', icon: 'none' })
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ favLoading: false })
      setTimeout(() => this.setData({ favAnim: false }), 320)
    }
  },

  /** 关注卖家：卖家本人不展示该按钮 */
  async follow() {
    if (!this.requireLogin() || this.data.followLoading) return
    const seller = this.data.detail.seller
    if (!seller || this.data.detail.isOwner) return
    this.setData({ followLoading: true })
    try {
      const state = await toggleFollow(seller.id)
      this.setData({
        'detail.seller.followed': !!state.followed,
        'detail.seller.fansCount': state.fansCount || 0
      })
      wx.showToast({ title: state.followed ? '关注成功' : '已取消关注', icon: 'none' })
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ followLoading: false })
    }
  },

  contact() {
    wx.showToast({ title: '平台客服会话将在后续模块上线，先和卖家聊聊吧', icon: 'none' })
  },

  /** 跳到卖家主页（模块 8 提供，免登录可看） */
  goSellerHome(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '/pages/user/home?id=' + id })
  },

  /**
   * 更多操作：举报商品 / 不感兴趣。
   * <p>
   * 举报入口放在「更多」而不是页面上显眼的位置：举报是低频动作，
   * 放显眼会诱导用户把「我不想要」也当成「我要举报」，
   * 客服队列会被大量无效举报淹没，反而伤害真正需要处理的人。
   */
  showMoreActions() {
    if (!this.requireLogin()) return
    const detail = this.data.detail
    if (!detail) return
    const isOwner = !!detail.isOwner
    const actions = isOwner
      ? ['不感兴趣']
      : ['举报商品（虚假描述 / 违禁品 / 欺诈）']
    wx.showActionSheet({
      itemList: actions,
      success: res => {
        if (isOwner) {
          wx.showToast({ title: '已减少此类推荐', icon: 'none' })
          return
        }
        this.goReport()
      }
    })
  },

  goReport() {
    wx.navigateTo({ url: '/pages/report/index?type=1&targetId=' + this.data.detail.id })
  },

  /**
   * 长按评价 → 举报评价。与评价列表页同一套交互：
   * 长按是「更多操作」的手势，不会让每条评价都挂满按钮。
   */
  onReviewLongPress(e) {
    if (!this.requireLogin()) return
    const index = Number(e.currentTarget.dataset.index)
    const review = (this.data.reviews || [])[index]
    if (!review) return
    if (review.isAnonymous || !review.reviewerId) {
      wx.showActionSheet({
        itemList: ['该评价为匿名评价，无法举报', '我的举报记录'],
        success: res => {
          if (res.tapIndex === 1) {
            wx.navigateTo({ url: '/pages/report/my' })
          }
        }
      })
      return
    }
    wx.showActionSheet({
      itemList: ['举报该评价（恶意差评 / 广告 / 辱骂）'],
      success: () => {
        wx.navigateTo({ url: '/pages/report/index?type=3&targetId=' + review.id })
      }
    })
  },

  /**
   * 联系卖家：创建或获取「我 → 卖家 + 当前商品」的会话，首次会自动发一张商品卡片。
   * 已售出的商品也允许咨询（比如问能不能再进一件），只有自己被封禁/未登录才拦。
   */
  contactSeller() {
    if (!this.requireLogin()) return
    const detail = this.data.detail
    if (!detail || !detail.seller || !detail.seller.id) {
      wx.showToast({ title: '卖家信息不存在', icon: 'none' })
      return
    }
    wx.showLoading({ title: '正在打开会话', mask: true })
    createSession({ sellerId: detail.seller.id, productId: detail.id })
      .then(session => {
        wx.hideLoading()
        wx.navigateTo({ url: '/pages/chat/index?sessionId=' + session.id })
      })
      .catch(() => wx.hideLoading())
  },

  want() {
    if (!this.requireLogin()) return
    const detail = this.data.detail
    if (!detail) return
    if (detail.status === 3) {
      wx.showToast({ title: '该商品已售出', icon: 'none' })
      return
    }
    if (detail.status !== 1) {
      wx.showToast({ title: '该商品暂不可购买', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/order/confirm?productId=' + detail.id })
  },

  goMyProducts() {
    wx.redirectTo({ url: '/pages/product/my' })
  },

  onShareAppMessage() {
    const detail = this.data.detail
    return {
      title: detail ? detail.title : '校园二手交易平台',
      path: '/pages/product/detail?id=' + this.id,
      imageUrl: detail && detail.images.length ? detail.images[0] : ''
    }
  }
})
