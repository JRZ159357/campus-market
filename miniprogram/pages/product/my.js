// 文件路径: miniprogram/pages/product/my.js
const { pageMyProducts, offlineProduct, deleteProduct } = require('../../api/product')
const { listSessionsByProduct } = require('../../api/chat')
const { imgUrl, money, statusMeta, timeAgo } = require('../../utils/format')

const TABS = [
  { label: '全部', value: null },
  { label: '待审核', value: 0 },
  { label: '已上架', value: 1 },
  { label: '已下架', value: 2 },
  { label: '已售出', value: 3 }
]

Page({
  data: {
    tabs: TABS,
    activeIndex: 0,
    list: [],
    pageNum: 1,
    pageSize: 10,
    total: 0,
    hasMore: true,
    loading: true,
    loadingMore: false,
    error: false
  },

  onShow() {
    this.reload()
  },

  /**
   * 支持从外部指定初始状态标签：个人中心的「发布 / 已售」统计行会跳过来，
   * status=3 表示已售出，status=1 表示已上架，不传则显示全部。
   */
  onLoad(options) {
    if (options && options.status !== undefined && options.status !== '') {
      const status = Number(options.status)
      const index = TABS.findIndex(t => t.value === status)
      if (index > 0) {
        this.setData({ activeIndex: index })
      }
    }
  },

  onPullDownRefresh() {
    this.reload().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadMore()
  },

  onTabChange(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (index === this.data.activeIndex) return
    this.setData({ activeIndex: index, list: [] })
    this.reload()
  },

  reload() {
    return this.fetchPage(1, true)
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loading || this.data.loadingMore) return
    this.fetchPage(this.data.pageNum + 1, false)
  },

  async fetchPage(pageNum, reset) {
    if (reset) {
      this.setData({ loading: true, error: false })
    } else {
      this.setData({ loadingMore: true })
    }

    const status = this.data.tabs[this.data.activeIndex].value
    try {
      const data = await pageMyProducts({
        pageNum,
        pageSize: this.data.pageSize,
        status
      })
      const records = (data.records || []).map(row => this.decorate(row))
      const list = reset ? records : this.data.list.concat(records)
      const total = Number(data.total || 0)
      this.setData({
        list,
        total,
        pageNum,
        hasMore: list.length < total,
        error: false
      })
    } catch (e) {
      if (reset) {
        this.setData({ list: [], total: 0, error: true })
      }
    } finally {
      this.setData({ loading: false, loadingMore: false })
    }
  },

  decorate(row) {
    const meta = statusMeta(row.status)
    return {
      ...row,
      cover: imgUrl(row.cover),
      priceText: money(row.price),
      statusLabel: meta.label,
      statusClass: meta.className,
      timeText: timeAgo(row.createTime)
    }
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/product/detail?id=' + id })
  },

  goPublish() {
    wx.navigateTo({ url: '/pages/publish/index' })
  },

  /** 更多操作：按状态给出可用动作 */
  showActions(e) {
    const item = e.currentTarget.dataset.item
    // 「查看咨询」放在最前面：卖家最常关心的是「有没有人来问」
    const actions = [{ key: 'chat', label: '查看咨询消息' }]
    if (item.status === 0) {
      actions.push({ key: 'edit', label: '编辑商品' })
    }
    if (item.status === 2) {
      // 已下架商品重新上架要走审核，所以复用编辑入口（保存后回到待审核）
      actions.push({ key: 'edit', label: '编辑并重新上架' })
    }
    if (item.status === 1) {
      actions.push({ key: 'offline', label: '下架商品' })
    }
    // 已售出的商品不能删除：它对应着已完成订单，删了会让订单失去追溯对象
    if (item.status !== 1 && item.status !== 3) {
      actions.push({ key: 'delete', label: '删除商品' })
    }

    wx.showActionSheet({
      itemList: actions.map(a => a.label),
      success: res => {
        const action = actions[res.tapIndex]
        if (action.key === 'chat') {
          this.openConsult(item)
        } else if (action.key === 'edit') {
          wx.navigateTo({ url: '/pages/publish/index?id=' + item.id })
        } else if (action.key === 'offline') {
          this.confirmOffline(item)
        } else if (action.key === 'delete') {
          this.confirmDelete(item)
        }
      }
    })
  },

  /**
   * 查看该商品的咨询会话：一个商品可能被多个买家咨询，
   * 只有一个会话就直接进聊天页，多个则让卖家选买家。
   */
  openConsult(item) {
    wx.showLoading({ title: '加载中', mask: true })
    listSessionsByProduct(item.id)
      .then(rows => {
        wx.hideLoading()
        const list = rows || []
        if (!list.length) {
          wx.showToast({ title: '还没有买家咨询', icon: 'none' })
          return
        }
        if (list.length === 1) {
          wx.navigateTo({ url: '/pages/chat/index?sessionId=' + list[0].id })
          return
        }
        wx.showActionSheet({
          itemList: list.map(s => s.peerNickname + '（' + (s.lastMessage || '暂无消息').slice(0, 12) + '）'),
          success: res => {
            wx.navigateTo({ url: '/pages/chat/index?sessionId=' + list[res.tapIndex].id })
          }
        })
      })
      .catch(() => wx.hideLoading())
  },

  confirmOffline(item) {
    wx.showModal({
      title: '下架商品',
      content: '下架后商品将从商城隐藏，可随时编辑重新提交审核。',
      confirmText: '确认下架',
      success: async res => {
        if (!res.confirm) return
        try {
          await offlineProduct(item.id)
          wx.showToast({ title: '已下架', icon: 'success' })
          this.reload()
        } catch (e) {
          // 错误提示已统一处理
        }
      }
    })
  },

  confirmDelete(item) {
    wx.showModal({
      title: '删除商品',
      content: '删除后无法恢复，确定要删除「' + item.title + '」吗？',
      confirmText: '删除',
      confirmColor: '#F56C6C',
      success: async res => {
        if (!res.confirm) return
        try {
          await deleteProduct(item.id)
          wx.showToast({ title: '已删除', icon: 'success' })
          this.reload()
        } catch (e) {
          // 错误提示已统一处理
        }
      }
    })
  },

  retry() {
    this.reload()
  }
})
