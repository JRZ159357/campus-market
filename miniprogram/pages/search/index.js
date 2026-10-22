// 文件路径: miniprogram/pages/search/index.js
const { searchProducts, listCategories } = require('../../api/product')
const { getHotKeywords } = require('../../api/home')
const { splitColumns } = require('../../utils/waterfall')

const HISTORY_KEY = 'cm_search_history'
const HISTORY_MAX = 10

const PRICE_RANGES = [
  { label: '不限', min: null, max: null },
  { label: '0-50', min: 0, max: 50 },
  { label: '50-200', min: 50, max: 200 },
  { label: '200-500', min: 200, max: 500 },
  { label: '500以上', min: 500, max: null }
]

Page({
  data: {
    keyword: '',
    focus: true,
    history: [],
    hotWords: [],
    categories: [],
    priceRanges: PRICE_RANGES,
    priceIndex: 0,
    categoryId: null,
    sortKey: 'default',
    sort: 'default',
    priceAsc: true,
    showFilter: false,
    searched: false,
    loading: false,
    loadingMore: false,
    left: [],
    right: [],
    pageNum: 1,
    hasMore: false,
    total: 0,
    scrollTop: 0
  },

  onLoad(options) {
    this.setData({
      history: wx.getStorageSync(HISTORY_KEY) || []
    })
    this.loadHotKeywords()
    this.loadCategories()
    if (options && options.keyword) {
      const keyword = decodeURIComponent(options.keyword)
      this.setData({ keyword, focus: false })
      this.doSearch()
    }
  },

  onReachBottom() {
    if (!this.data.searched) return
    this.loadMore()
  },

  async loadHotKeywords() {
    try {
      const words = (await getHotKeywords()) || []
      this.setData({ hotWords: words })
    } catch (e) {
      this.setData({ hotWords: [] })
    }
  },

  async loadCategories() {
    try {
      this.setData({ categories: (await listCategories()) || [] })
    } catch (e) {
      this.setData({ categories: [] })
    }
  },

  onInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onKeywordTap(e) {
    const keyword = e.currentTarget.dataset.keyword
    if (!keyword) return
    this.setData({ keyword, focus: false })
    this.doSearch()
  },

  clearKeyword() {
    this.setData({ keyword: '', focus: true, searched: false, left: [], right: [], total: 0 })
  },

  onSearchTap() {
    this.doSearch()
  },

  /** 记录搜索历史：去重、置顶、最多 10 条 */
  saveHistory(keyword) {
    const list = [keyword, ...this.data.history.filter(item => item !== keyword)].slice(0, HISTORY_MAX)
    this.setData({ history: list })
    wx.setStorageSync(HISTORY_KEY, list)
  },

  clearHistory() {
    wx.showModal({
      title: '清空搜索历史',
      content: '确定要清空全部搜索历史吗？',
      success: res => {
        if (res.confirm) {
          this.setData({ history: [] })
          wx.removeStorageSync(HISTORY_KEY)
        }
      }
    })
  },

  async doSearch() {
    const keyword = (this.data.keyword || '').trim()
    if (!keyword) {
      wx.showToast({ title: '请输入搜索关键词', icon: 'none' })
      return
    }
    this.saveHistory(keyword)
    this.setData({
      keyword,
      focus: false,
      searched: true,
      pageNum: 1,
      hasMore: false,
      left: [],
      right: [],
      scrollTop: 0
    })
    await this.fetchPage(1)
  },

  async fetchPage(pageNum) {
    this.setData({ loading: pageNum === 1, loadingMore: pageNum > 1 })
    try {
      const data = await searchProducts({
        keyword: this.data.keyword,
        pageNum,
        pageSize: 10,
        categoryId: this.data.categoryId,
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

  toggleFilter() {
    this.setData({ showFilter: !this.data.showFilter })
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
    if (this.data.searched) {
      this.setData({ pageNum: 1 })
      this.fetchPage(1)
    }
  },

  onCategoryTap(e) {
    const raw = e.currentTarget.dataset.id
    const id = raw === '' || raw === undefined || raw === null ? null : Number(raw)
    this.setData({ categoryId: id, pageNum: 1 })
    if (this.data.searched) this.fetchPage(1)
  },

  onPriceTap(e) {
    const index = Number(e.currentTarget.dataset.index)
    this.setData({ priceIndex: index, pageNum: 1 })
    if (this.data.searched) this.fetchPage(1)
  },

  resetFilter() {
    this.setData({
      categoryId: null,
      priceIndex: 0,
      sortKey: 'default',
      sort: 'default',
      priceAsc: true,
      pageNum: 1
    })
    if (this.data.searched) this.fetchPage(1)
  },

  back() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      wx.navigateBack()
    } else {
      wx.switchTab({ url: '/pages/index/index' })
    }
  }
})
