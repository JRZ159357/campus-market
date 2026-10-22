// 文件路径: miniprogram/pages/product/list.js
const { listProducts, listCategories } = require('../../api/product')
const { splitColumns } = require('../../utils/waterfall')

const PRICE_RANGES = [
  { label: '不限', min: null, max: null },
  { label: '0-50', min: 0, max: 50 },
  { label: '50-200', min: 50, max: 200 },
  { label: '200-500', min: 200, max: 500 },
  { label: '500以上', min: 500, max: null }
]

Page({
  data: {
    categories: [],
    categoryIndex: 0,
    categoryNames: ['全部分类'],
    priceRanges: PRICE_RANGES,
    priceIndex: 0,
    sortKey: 'default',
    sort: 'default',
    priceAsc: true,
    showPrice: false,
    left: [],
    right: [],
    pageNum: 1,
    total: 0,
    hasMore: false,
    loading: true,
    loadingMore: false,
    showTop: false
  },

  onLoad(options) {
    if (options && options.categoryId) {
      this.initialCategoryId = Number(options.categoryId)
    }
    if (options && options.keyword) {
      this.initialKeyword = decodeURIComponent(options.keyword)
    }
    this.loadCategories()
  },

  onPullDownRefresh() {
    this.fetchPage(1).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadMore()
  },

  onPageScroll(e) {
    const showTop = e.scrollTop > 700
    if (showTop !== this.data.showTop) {
      this.setData({ showTop })
    }
  },

  async loadCategories() {
    try {
      const list = (await listCategories()) || []
      const names = ['全部分类', ...list.map(item => item.name)]
      let index = 0
      if (this.initialCategoryId) {
        const found = list.findIndex(item => item.id === this.initialCategoryId)
        index = found >= 0 ? found + 1 : 0
      }
      this.setData({ categories: list, categoryNames: names, categoryIndex: index })
    } catch (e) {
      this.setData({ categories: [], categoryNames: ['全部分类'] })
    }
    this.fetchPage(1)
  },

  resolveCategoryId() {
    if (this.data.categoryIndex === 0) return null
    const item = this.data.categories[this.data.categoryIndex - 1]
    return item ? item.id : null
  },

  async fetchPage(pageNum) {
    this.setData({ loading: pageNum === 1, loadingMore: pageNum > 1 })
    try {
      const data = await listProducts({
        pageNum,
        pageSize: 10,
        keyword: this.initialKeyword || undefined,
        categoryId: this.resolveCategoryId(),
        minPrice: PRICE_RANGES[this.data.priceIndex].min,
        maxPrice: PRICE_RANGES[this.data.priceIndex].max,
        sort: this.data.sort
      })
      const records = data.records || []
      const columns = pageNum === 1
        ? splitColumns(records)
        : splitColumns(records, this.data.left, this.data.right)
      this.setData({
        left: columns.left,
        right: columns.right,
        pageNum,
        total: data.total || 0,
        hasMore: pageNum * 10 < (data.total || 0)
      })
    } catch (e) {
      this.setData({ hasMore: false })
    } finally {
      this.setData({ loading: false, loadingMore: false })
    }
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loading || this.data.loadingMore) return
    this.fetchPage(this.data.pageNum + 1)
  },

  onCategoryChange(e) {
    this.setData({ categoryIndex: Number(e.detail.value) })
    this.fetchPage(1)
  },

  onPriceChange(e) {
    this.setData({ priceIndex: Number(e.currentTarget.dataset.index) })
    this.fetchPage(1)
  },

  togglePrice() {
    this.setData({ showPrice: !this.data.showPrice })
  },

  onSortTap(e) {
    const key = e.currentTarget.dataset.key
    let sort = key
    let priceAsc = this.data.priceAsc
    if (key === 'price') {
      priceAsc = this.data.sortKey === 'price' ? !this.data.priceAsc : true
      sort = priceAsc ? 'priceAsc' : 'priceDesc'
    }
    this.setData({ sortKey: key, sort, priceAsc })
    this.fetchPage(1)
  },

  resetFilter() {
    this.setData({ categoryIndex: 0, priceIndex: 0, sortKey: 'default', sort: 'default', priceAsc: true })
    this.fetchPage(1)
  },

  backToTop() {
    wx.pageScrollTo({ scrollTop: 0, duration: 300 })
  }
})
