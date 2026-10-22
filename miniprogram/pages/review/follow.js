// 文件路径: miniprogram/pages/review/follow.js
const { getReviewDetail, followReview } = require('../../api/review')
const { uploadImage } = require('../../utils/request')
const { imgUrl, starText } = require('../../utils/format')

/**
 * 追评页。
 * <p>
 * 追评只追加内容，不重新打分：星级代表这次交易的整体感受，
 * 允许事后改分等于给了「不满意就威胁改一星」的手段，对卖家不公平。
 * <p>
 * 窗口期由后端判定（交易完成 7 天内），前端只负责展示剩余时间；
 * 即使有人绕过前端直接调接口，后端仍会拒绝超期追评。
 */
Page({
  data: {
    reviewId: null,
    loading: true,
    error: '',
    review: null,
    content: '',
    charCount: 0,
    images: [],
    uploading: false,
    submitting: false,
    maxImages: 3,
    maxLength: 500
  },

  onLoad(options) {
    const reviewId = Number(options.reviewId || 0)
    if (!reviewId) {
      this.setData({ loading: false, error: '缺少评价信息' })
      return
    }
    this.setData({ reviewId })
    this.loadReview(reviewId)
  },

  async loadReview(reviewId) {
    try {
      const review = await getReviewDetail(reviewId)
      if (!review.canFollow) {
        this.setData({
          loading: false,
          error: review.followContent ? '该评价已经追评过了' : '已超过追评期限（交易完成后 7 天内可追评）'
        })
        return
      }
      this.setData({
        review: {
          id: review.id,
          reviewerNickname: review.reviewerNickname,
          stars: review.stars,
          starsText: starText(review.stars),
          content: review.content || '（未填写文字）',
          images: (review.images || []).map(imgUrl),
          createTime: review.createTime,
          productTitle: review.productTitle
        },
        loading: false
      })
    } catch (e) {
      this.setData({ loading: false, error: e.message || '评价加载失败' })
    }
  },

  onContent(e) {
    const content = e.detail.value || ''
    this.setData({ content, charCount: content.length })
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

  async handleSubmit() {
    if (this.data.submitting) return
    if (!this.data.content.trim() && !this.data.images.length) {
      wx.showToast({ title: '追评内容和图片至少填一项', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      await followReview({
        reviewId: this.data.reviewId,
        content: this.data.content.trim(),
        images: this.data.images
      })
      wx.showToast({ title: '追评成功', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 900)
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
