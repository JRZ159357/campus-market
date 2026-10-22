// 文件路径: miniprogram/pages/review/list.js
const { pageProductReviews, getProductReviewStat } = require('../../api/review')
const { imgUrl, timeAgo } = require('../../utils/format')

/**
 * 商品评价列表。
 * <p>
 * 顶部筛选「全部 / 好评 / 中评 / 差评 / 有图」全部翻译成星级区间或布尔条件传给后端，
 * 不在前端过滤：前端只拿到一页数据，本地过滤会导致「这一页没有好评」时错误地显示空态。
 */
Page({
  data: {
    productId: null,
    title: '',
    filters: [
      { key: 'all', label: '全部' },
      { key: 'good', label: '好评' },
      { key: 'mid', label: '中评' },
      { key: 'bad', label: '差评' },
      { key: 'image', label: '有图' }
    ],
    active: 'all',
    stat: { total: 0, avgStars: 0, goodRate: 0 },
    list: [],
    pageNum: 1,
    pageSize: 10,
    total: 0,
    hasMore: true,
    loading: false,
    loaded: false
  },

  onLoad(options) {
    const productId = Number(options.productId || 0)
    if (!productId) {
      wx.showToast({ title: '缺少商品信息', icon: 'none' })
      return
    }
    this.setData({ productId, title: options.title ? decodeURIComponent(options.title) : '' })
    if (this.data.title) {
      wx.setNavigationBarTitle({ title: this.data.title + ' · 评价' })
    } else {
      wx.setNavigationBarTitle({ title: '商品评价' })
    }
    this.loadStat()
    this.loadList(true)
  },

  onPullDownRefresh() {
    Promise.all([this.loadStat(), this.loadList(true)]).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadList(false)
    }
  },

  async loadStat() {
    try {
      const stat = await getProductReviewStat(this.data.productId)
      this.setData({ stat: stat || { total: 0, avgStars: 0, goodRate: 0 } })
    } catch (e) {
      // 统计失败不影响列表展示
    }
  },

  switchFilter(e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.active) return
    this.setData({ active: key, list: [], hasMore: true })
    this.loadList(true)
  },

  /** Tab → 查询条件 */
  buildParams() {
    const params = { pageNum: this.data.pageNum, pageSize: this.data.pageSize }
    const active = this.data.active
    if (active === 'good') {
      params.minStars = 4
      params.maxStars = 5
    } else if (active === 'mid') {
      params.minStars = 3
      params.maxStars = 3
    } else if (active === 'bad') {
      params.minStars = 1
      params.maxStars = 2
    } else if (active === 'image') {
      params.hasImage = true
    }
    return params
  },

  async loadList(reset) {
    if (this.data.loading) return
    if (reset) {
      this.setData({ pageNum: 1, hasMore: true })
    }
    this.setData({ loading: true })
    try {
      const data = await pageProductReviews(this.data.productId, this.buildParams())
      const records = (data.records || []).map(this.decorate)
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

  /**
   * 评价展示装饰：图片转绝对地址、时间转相对时间、匿名头像用默认占位。
   * 昵称的脱敏在服务端做，这里只负责渲染，避免出现「前端判断匿名」的错误做法。
   */
  decorate(review) {
    return Object.assign({}, review, {
      avatar: review.reviewerAvatar ? imgUrl(review.reviewerAvatar) : '',
      avatarText: (review.reviewerNickname || '匿').slice(0, 1),
      images: (review.images || []).map(imgUrl),
      followImages: (review.followImages || []).map(imgUrl),
      timeText: timeAgo(review.createTime)
    })
  },

  previewImages(e) {
    const ds = e.currentTarget.dataset
    const review = this.data.list[Number(ds.index)]
    if (!review) return
    const urls = ds.field === 'follow' ? review.followImages : review.images
    if (!urls || !urls.length) return
    wx.previewImage({
      current: urls[Number(ds.imgIndex) || 0],
      urls
    })
  },

  /**
   * 长按评价 → 举报评价。
   * <p>
   * 用长按而不是「⋯」按钮：评价列表里每一条都挂一个举报按钮会让页面全是控件，
   * 而举报是极少数人会用的动作。长按是列表里通用的「更多操作」手势，
   * 不会干扰正常浏览。
   * <p>
   * 匿名评价不给举报入口：服务端对匿名评价已经脱敏，前端拿不到真实的 reviewerId，
   * 拿一个假的 ID 去举报只会提交一条必然失败的请求。
   */
  onReviewLongPress(e) {
    if (!getApp().isLogin()) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => wx.navigateTo({ url: '/pages/login/index' }), 600)
      return
    }
    const review = this.data.list[Number(e.currentTarget.dataset.index)]
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

  goBack() {
    wx.navigateBack()
  }
})
