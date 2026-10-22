// 文件路径: miniprogram/pages/category/index.js
const { listCategories, listProducts } = require('../../api/product')
const { splitColumns } = require('../../utils/waterfall')

const SORTS = [
  { key: 'default', label: '综合' },
  { key: 'new', label: '最新' },
  { key: 'price', label: '价格' }
]

Page({
  data: {
    categories: [],
    activeId: null,
    activeName: '全部',
    sorts: SORTS,
    sortKey: 'default',
    sort: 'default',
    priceAsc: true,
    left: [],
    right: [],
    pageNum: 1,
    hasMore: true,
    loading: true,
    loadingMore: false,
    refreshing: false,
    scrollTop: 0
  },

  onLoad() {
    this.loadCategories()
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 1 })
      this.getTabBar().refresh()
    }
    const app = getApp()
    const pending = app.globalData.pendingCategoryId
    if (pending !== undefined && pending !== null) {
      app.globalData.pendingCategoryId = null
      if (this.data.categories.length) {
        this.selectCategoryById(pending)
      } else {
        this.pendingCategoryId = pending
      }
    }
  },

  async loadCategories() {
    try {
      const list = (await listCategories()) || []
      this.setData({ categories: list })
      if (this.pendingCategoryId) {
        this.selectCategoryById(this.pendingCategoryId)
        this.pendingCategoryId = null
        return
      }
      if (this.data.activeId) {
        const current = list.find(item => item.id === this.data.activeId)
        this.setData({ activeName: current ? current.name : '全部' })
      }
      this.reload()
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  selectCategoryById(id) {
    const category = this.data.categories.find(item => item.id === Number(id))
    this.setData({
      activeId: category ? category.id : null,
      activeName: category ? category.name : '全部'
    })
    this.reload()
  },

  onCategoryTap(e) {
    const raw = e.currentTarget.dataset.id
    const id = raw === '' || raw === undefined || raw === null ? null : Number(raw)
    const category = this.data.categories.find(item => item.id === id)
    this.setData({
      activeId: id,
      activeName: category ? category.name : '全部'
    })
    this.reload()
  },

  onSortTap(e) {
    const key = e.currentTarget.dataset.key
    if (key === 'price') {
      // 价格排序：再次点击同一按钮切换升降
      const asc = this.data.sortKey === 'price' ? !this.data.priceAsc : true
      this.setData({ sortKey: 'price', priceAsc: asc, sort: asc ? 'priceAsc' : 'priceDesc' })
    } else {
      this.setData({ sortKey: key, sort: key })
    }
    this.reload()
  },

  async reload() {
    this.setData({
      loading: true,
      pageNum: 1,
      hasMore: true,
      left: [],
      right: [],
      scrollTop: 0
    })
    await this.fetchPage(1)
  },

  async fetchPage(pageNum) {
    try {
      const data = await listProducts({
        pageNum,
        pageSize: 10,
        categoryId: this.data.activeId,
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
        hasMore: pageNum * 10 < (data.total || 0)
      })
    } catch (e) {
      this.setData({ hasMore: false })
    } finally {
      this.setData({ loading: false, loadingMore: false, refreshing: false })
    }
  },

  onScrollToLower() {
    if (!this.data.hasMore || this.data.loading || this.data.loadingMore) return
    this.setData({ loadingMore: true })
    this.fetchPage(this.data.pageNum + 1)
  },

  onRefresh() {
    this.setData({ refreshing: true })
    this.fetchPage(1).then(() => this.setData({ pageNum: 1 }))
  },

  goSearch() {
    wx.navigateTo({ url: '/pages/search/index' })
  },

  goList() {
    wx.navigateTo({ url: '/pages/product/list' })
  }
})
