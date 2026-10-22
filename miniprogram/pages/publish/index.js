// 文件路径: miniprogram/pages/publish/index.js
const {
  listCategories,
  createProduct,
  updateProduct,
  getProduct,
  pageMyProducts
} = require('../../api/product')
const { getAuthStatus, getMe } = require('../../api/user')
const { uploadImage } = require('../../utils/request')
const { imgUrl } = require('../../utils/format')

Page({
  data: {
    loading: true,
    verified: false,
    editId: null,
    categories: [],
    images: [],
    title: '',
    categoryId: null,
    price: '',
    originalPrice: '',
    description: '',
    submitting: false,
    publishLimit: 20,
    publishUsed: 0,
    publishRemain: 20
  },

  onLoad(options) {
    if (options && options.id) {
      this.setData({ editId: Number(options.id) })
      wx.setNavigationBarTitle({ title: '编辑商品' })
    }
    this.init()
  },

  async init() {
    if (!getApp().isLogin()) {
      wx.navigateTo({ url: '/pages/login/index' })
      return
    }
    this.setData({ loading: true })
    try {
      const status = await getAuthStatus()
      const verified = !!(status && status.verified)
      this.setData({ verified })
      if (!verified) {
        this.setData({ loading: false })
        this.guideAuth()
        return
      }

      const categories = (await listCategories()) || []
      this.setData({
        categories: categories.map(item => ({
          ...item,
          isImage: !!item.icon && /^(\/|https?:)/.test(item.icon),
          iconFull: imgUrl(item.icon)
        }))
      })
      this.loadQuota()

      if (this.data.editId) {
        await this.loadProduct()
      }
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ loading: false })
    }
  },

  /** 未认证引导：弹窗说明后跳转认证页 */
  guideAuth() {
    wx.showModal({
      title: '需要学生认证',
      content: '发布商品需要先完成学生认证，认证通过后即可发布闲置。',
      confirmText: '去认证',
      cancelText: '返回',
      success: res => {
        if (res.confirm) {
          wx.redirectTo({ url: '/pages/auth/student' })
        } else {
          wx.navigateBack({
            // 首页是 tabBar 页面，只能用 switchTab 返回
            fail: () => wx.switchTab({ url: '/pages/index/index' })
          })
        }
      }
    })
  },

  /** 剩余发布额度：待审核 + 已上架 都占额度 */
  async loadQuota() {
    try {
      const [me, pending, onSale] = await Promise.all([
        getMe(),
        pageMyProducts({ pageNum: 1, pageSize: 1, status: 0 }),
        pageMyProducts({ pageNum: 1, pageSize: 1, status: 1 })
      ])
      const limit = me.publishLimit || 20
      const used = Number(pending.total || 0) + Number(onSale.total || 0)
      this.setData({
        publishLimit: limit,
        publishUsed: used,
        publishRemain: Math.max(0, limit - used)
      })
    } catch (e) {
      // 额度提示失败不影响发布
    }
  },

  async loadProduct() {
    const detail = await getProduct(this.data.editId)
    if (detail.status !== 0 && detail.status !== 2) {
      wx.showModal({
        title: '无法编辑',
        content: '只有待审核或已下架的商品可以编辑，请先下架商品。',
        showCancel: false,
        success: () => wx.navigateBack()
      })
      return
    }
    this.setData({
      images: (detail.images || []).map(url => ({ url, full: imgUrl(url) })),
      title: detail.title || '',
      categoryId: detail.categoryId,
      price: detail.price !== null && detail.price !== undefined ? String(detail.price) : '',
      originalPrice:
        detail.originalPrice !== null && detail.originalPrice !== undefined
          ? String(detail.originalPrice)
          : '',
      description: detail.description || ''
    })
  },

  // ---------- 图片 ----------
  chooseImage() {
    const remain = 9 - this.data.images.length
    if (remain <= 0) {
      wx.showToast({ title: '最多上传9张图片', icon: 'none' })
      return
    }
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: async res => {
        const files = (res.tempFiles || []).map(f => f.tempFilePath)
        if (!files.length) return
        wx.showLoading({ title: '上传中', mask: true })
        try {
          const added = []
          for (const file of files) {
            const url = await uploadImage(file)
            added.push({ url, full: imgUrl(url) })
          }
          this.setData({ images: this.data.images.concat(added) })
        } catch (e) {
          // 单张失败已提示，保留已成功的图片
        } finally {
          wx.hideLoading()
        }
      }
    })
  },

  removeImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    const images = [...this.data.images]
    images.splice(index, 1)
    this.setData({ images })
  },

  /** 左右移动调整顺序，第一张为主图 */
  moveImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    const dir = Number(e.currentTarget.dataset.dir)
    const target = index + dir
    if (target < 0 || target >= this.data.images.length) return
    const images = [...this.data.images]
    const temp = images[index]
    images[index] = images[target]
    images[target] = temp
    this.setData({ images })
  },

  previewImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    wx.previewImage({
      current: this.data.images[index].full,
      urls: this.data.images.map(item => item.full)
    })
  },

  // ---------- 表单 ----------
  onTitleInput(e) {
    this.setData({ title: e.detail.value })
  },

  onDescInput(e) {
    this.setData({ description: e.detail.value })
  },

  selectCategory(e) {
    this.setData({ categoryId: Number(e.currentTarget.dataset.id) })
  },

  /** 价格只允许数字与两位小数 */
  onPriceInput(e) {
    const value = this.normalizePrice(e.detail.value)
    this.setData({ price: value })
    return value
  },

  onOriginalPriceInput(e) {
    const value = this.normalizePrice(e.detail.value)
    this.setData({ originalPrice: value })
    return value
  },

  normalizePrice(input) {
    let value = String(input || '').replace(/[^\d.]/g, '')
    const parts = value.split('.')
    if (parts.length > 2) {
      value = parts[0] + '.' + parts.slice(1).join('')
    }
    const segments = value.split('.')
    if (segments[1] && segments[1].length > 2) {
      value = segments[0] + '.' + segments[1].slice(0, 2)
    }
    if (value.startsWith('.')) {
      value = '0' + value
    }
    return value
  },

  // ---------- 提交 ----------
  async submit() {
    if (this.data.submitting) return
    const { images, title, categoryId, price, originalPrice, description, editId } = this.data

    if (!images.length) {
      wx.showToast({ title: '请至少上传一张图片', icon: 'none' })
      return
    }
    if (!title.trim()) {
      wx.showToast({ title: '请填写商品标题', icon: 'none' })
      return
    }
    if (title.trim().length > 30) {
      wx.showToast({ title: '标题最多30个字', icon: 'none' })
      return
    }
    if (!categoryId) {
      wx.showToast({ title: '请选择商品分类', icon: 'none' })
      return
    }
    if (!price || Number(price) <= 0) {
      wx.showToast({ title: '请填写正确的价格', icon: 'none' })
      return
    }
    if (originalPrice && Number(originalPrice) <= 0) {
      wx.showToast({ title: '原价需大于0', icon: 'none' })
      return
    }

    const payload = {
      title: title.trim(),
      categoryId,
      price: Number(price),
      originalPrice: originalPrice ? Number(originalPrice) : null,
      description: description.trim(),
      images: images.map(item => item.url)
    }

    this.setData({ submitting: true })
    wx.showLoading({ title: editId ? '保存中' : '发布中', mask: true })
    try {
      if (editId) {
        await updateProduct(editId, payload)
        wx.hideLoading()
        wx.showToast({ title: '已提交审核', icon: 'success' })
      } else {
        await createProduct(payload)
        wx.hideLoading()
        wx.showToast({ title: '发布成功', icon: 'success' })
      }
      setTimeout(() => {
        wx.redirectTo({ url: '/pages/product/my' })
      }, 700)
    } catch (e) {
      wx.hideLoading()
    } finally {
      this.setData({ submitting: false })
    }
  }
})
