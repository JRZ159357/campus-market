// 文件路径: miniprogram/pages/review/edit.js
const { getOrderReviews, submitReview } = require('../../api/review')
const { uploadImage } = require('../../utils/request')
const { imgUrl, money, starText, creditDeltaOfStars } = require('../../utils/format')

/**
 * 评价页。
 * <p>
 * 维度按评价方向动态生成：买家评卖家三个维度（描述相符 / 发货速度 / 沟通态度），
 * 卖家评买家两个维度（付款速度 / 沟通态度）。用一个 dimensions 数组驱动模板，
 * 避免在 wxml 里写两套几乎相同的打星区块。
 * <p>
 * 方向不信任页面参数，而是用 getOrderReviews 返回的 myReviewType：
 * 用户手动改 URL 传错 type 也不会评错方向（后端同样会校验）。
 */
Page({
  data: {
    orderId: null,
    loading: true,
    error: '',
    product: null,
    reviewType: 1,
    reviewTypeLabel: '评价卖家',
    dimensions: [],
    ratings: {},
    labels: {},
    content: '',
    charCount: 0,
    images: [],
    isAnonymous: false,
    uploading: false,
    submitting: false,
    maxImages: 3,
    maxLength: 500
  },

  onLoad(options) {
    const orderId = Number(options.orderId || 0)
    if (!orderId) {
      this.setData({ loading: false, error: '缺少订单信息' })
      return
    }
    this.setData({ orderId })
    this.loadOrder(orderId)
  },

  async loadOrder(orderId) {
    try {
      const detail = await getOrderReviews(orderId)
      if (!detail.canReview) {
        this.setData({ loading: false, error: '这笔订单当前不可评价（可能已完成评价或订单状态不符）' })
        return
      }
      const reviewType = detail.myReviewType || 1
      const ratings = this.buildRatings(reviewType)
      this.setData({
        product: {
          title: detail.productTitle || '商品',
          cover: imgUrl(detail.productCover),
          price: money(detail.price),
          orderNo: detail.orderNo
        },
        reviewType,
        reviewTypeLabel: reviewType === 1 ? '评价卖家' : '评价买家',
        dimensions: this.buildDimensions(reviewType),
        ratings,
        labels: this.buildLabels(ratings),
        loading: false
      })
      wx.setNavigationBarTitle({ title: reviewType === 1 ? '评价卖家' : '评价买家' })
    } catch (e) {
      this.setData({ loading: false, error: e.message || '订单加载失败' })
    }
  },

  /**
   * 维度定义。两个方向共有「沟通态度」，所以它放在两块里都要出现，
   * key 保持唯一（attitudeStars），不要为了模板复用而改名。
   */
  buildDimensions(reviewType) {
    if (reviewType === 1) {
      return [
        { key: 'descStars', label: '商品描述相符', tip: '实物与描述是否一致' },
        { key: 'shipStars', label: '发货速度', tip: '付款后多久发出' },
        { key: 'attitudeStars', label: '沟通态度', tip: '回复是否及时友好' }
      ]
    }
    return [
      { key: 'payStars', label: '付款速度', tip: '下单后付款是否及时' },
      { key: 'attitudeStars', label: '沟通态度', tip: '沟通是否顺畅友好' }
    ]
  },

  /** 默认全 5 星：绝大多数交易是顺利的，让用户少点几下 */
  buildRatings(reviewType) {
    if (reviewType === 1) {
      return { descStars: 5, shipStars: 5, attitudeStars: 5 }
    }
    return { payStars: 5, attitudeStars: 5 }
  },

  /**
   * 每个维度的星级文字。WXML 不能直接调用 JS 函数，
   * 所以在这里预先算好放进 labels，模板里直接取值。
   */
  buildLabels(ratings) {
    const labels = {}
    Object.keys(ratings).forEach(key => {
      labels[key] = starText(ratings[key])
    })
    return labels
  },

  onStar(e) {
    const { key, value } = e.currentTarget.dataset
    this.setData({
      ['ratings.' + key]: Number(value),
      ['labels.' + key]: starText(value)
    })
  },

  onContent(e) {
    const content = e.detail.value || ''
    this.setData({ content, charCount: content.length })
  },

  toggleAnonymous(e) {
    this.setData({ isAnonymous: !!e.detail.value })
  },

  async chooseImage() {
    const remain = this.data.maxImages - this.data.images.length
    if (remain <= 0) {
      wx.showToast({ title: '最多上传 3 张图片', icon: 'none' })
      return
    }
    const res = await wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera']
    }).catch(() => null)
    if (!res || !res.tempFiles || !res.tempFiles.length) {
      return
    }
    this.setData({ uploading: true })
    try {
      const uploaded = []
      for (const file of res.tempFiles) {
        const url = await uploadImage(file.tempFilePath)
        uploaded.push(url)
      }
      this.setData({ images: this.data.images.concat(uploaded) })
    } catch (e) {
      wx.showToast({ title: '图片上传失败', icon: 'none' })
    } finally {
      this.setData({ uploading: false })
    }
  },

  removeImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    const images = this.data.images.slice()
    images.splice(index, 1)
    this.setData({ images })
  },

  previewImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    wx.previewImage({
      current: imgUrl(this.data.images[index]),
      urls: this.data.images.map(imgUrl)
    })
  },

  /** 综合星级：与后端一致，各维度平均四舍五入 */
  computeStars() {
    const ratings = this.data.ratings
    const values = this.data.dimensions.map(item => Number(ratings[item.key]) || 0)
    const sum = values.reduce((acc, cur) => acc + cur, 0)
    return Math.round(sum / (values.length || 1))
  },

  async handleSubmit() {
    if (this.data.submitting) return
    const ratings = this.data.ratings
    const missing = this.data.dimensions.find(item => !ratings[item.key])
    if (missing) {
      wx.showToast({ title: '请先完成' + missing.label + '评分', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      // 只传当前方向用得到的维度，另一个方向的字段留空（后端会按方向忽略）
      const payload = {
        orderId: this.data.orderId,
        reviewType: this.data.reviewType,
        attitudeStars: ratings.attitudeStars,
        content: this.data.content.trim(),
        images: this.data.images,
        isAnonymous: this.data.isAnonymous
      }
      if (this.data.reviewType === 1) {
        payload.descStars = ratings.descStars
        payload.shipStars = ratings.shipStars
      } else {
        payload.payStars = ratings.payStars
      }
      await submitReview(payload)

      const delta = creditDeltaOfStars(this.computeStars())
      const toast = delta > 0
        ? '评价成功，信誉分+' + delta
        : delta < 0
          ? '评价成功，对方信誉分' + delta
          : '评价成功'
      wx.showToast({ title: toast, icon: 'success' })
      setTimeout(() => {
        wx.navigateBack()
      }, 900)
    } catch (e) {
      // 失败提示由请求封装统一 toast
    } finally {
      this.setData({ submitting: false })
    }
  },

  goBack() {
    wx.navigateBack()
  }
})
