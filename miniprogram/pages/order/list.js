// 文件路径: miniprogram/pages/order/list.js
const {
  pageMyOrders,
  pageMySales,
  payOrder,
  cancelOrder,
  receiveOrder,
  shipOrder,
  agreeRefund,
  applyRefund,
  rejectRefund,
  cancelRefund
} = require('../../api/order')
const { imgUrl, money, orderStatusMeta } = require('../../utils/format')

const TABS = [
  { value: '', label: '全部' },
  { value: 0, label: '待付款' },
  { value: 1, label: '待发货' },
  { value: 2, label: '待收货' },
  { value: 3, label: '已完成' },
  { value: 4, label: '已取消' },
  { value: 5, label: '退款/售后' }
]

const PAGE_SIZE = 10

Page({
  data: {
    tabs: TABS,
    active: '',
    role: 'buyer',
    list: [],
    pageNum: 1,
    hasMore: false,
    total: 0,
    loading: true,
    loadingMore: false,
    empty: false,
    reasonModal: { visible: false, title: '', placeholder: '', value: '', action: '', orderNo: '' }
  },

  onLoad(options) {
    const status = options.status
    this.setData({
      active: status === undefined || status === '' ? '' : Number(status),
      role: options.role === 'seller' ? 'seller' : 'buyer'
    })
    this.load(true)
  },

  onShow() {
    // 详情页操作后返回，列表需要同步状态
    if (this.loadedOnce) {
      this.load(true)
    }
  },

  onPullDownRefresh() {
    this.load(true).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loading || this.data.loadingMore) return
    this.load(false)
  },

  async load(reset) {
    const pageNum = reset ? 1 : this.data.pageNum + 1
    this.setData(reset ? { loading: true } : { loadingMore: true })
    try {
      const fetcher = this.data.role === 'seller' ? pageMySales : pageMyOrders
      const page = await fetcher({
        pageNum,
        pageSize: PAGE_SIZE,
        status: this.data.active === '' ? null : this.data.active
      })
      const records = (page.records || []).map(item => this.decorate(item))
      const list = reset ? records : this.data.list.concat(records)
      this.setData({
        list,
        pageNum,
        total: Number(page.total || 0),
        hasMore: list.length < Number(page.total || 0),
        empty: list.length === 0
      })
      this.loadedOnce = true
    } catch (e) {
      if (reset) {
        this.setData({ list: [], empty: true, hasMore: false })
      }
    } finally {
      this.setData({ loading: false, loadingMore: false })
    }
  },

  /** 列表卡片装饰：状态元数据、图片完整地址、价格文案、可用操作 */
  decorate(order) {
    return {
      ...order,
      statusMeta: orderStatusMeta(order.status),
      coverUrl: imgUrl(order.productCover),
      priceText: money(order.price),
      timeText: (order.createTime || '').slice(0, 16),
      actions: this.buildActions(order)
    }
  },

  /**
   * 卡片上的操作按钮。与详情页共用同一套状态规则，
   * 只是列表里最多展示两个，避免卡片过高。
   */
  buildActions(order) {
    if (this.data.role === 'seller') {
      if (order.status === 1) return [{ key: 'ship', text: '确认发货', primary: true }]
      if (order.status === 5) {
        return [
          { key: 'rejectRefund', text: '拒绝退款', primary: false },
          { key: 'agreeRefund', text: '同意退款', primary: true }
        ]
      }
      return []
    }
    if (order.status === 0) {
      return [
        { key: 'cancel', text: '取消订单', primary: false },
        { key: 'pay', text: '立即付款', primary: true }
      ]
    }
    if (order.status === 1) {
      return [{ key: 'refund', text: '申请退款', primary: false }]
    }
    if (order.status === 2) {
      return [
        { key: 'refund', text: '申请退款', primary: false },
        { key: 'receive', text: '确认收货', primary: true }
      ]
    }
    if (order.status === 3) {
      return [{ key: 'review', text: '评价晒单', primary: false }]
    }
    if (order.status === 5) {
      return [{ key: 'cancelRefund', text: '撤销退款', primary: false }]
    }
    return []
  },

  switchTab(e) {
    const value = e.currentTarget.dataset.value
    if (value === this.data.active) return
    this.setData({ active: value, list: [], empty: false })
    this.load(true)
  },

  switchRole(e) {
    const role = e.currentTarget.dataset.role
    if (role === this.data.role) return
    this.setData({ role, list: [], empty: false })
    this.load(true)
  },

  goDetail(e) {
    const orderNo = e.currentTarget.dataset.no
    wx.navigateTo({ url: '/pages/order/detail?orderNo=' + orderNo })
  },

  goProduct(e) {
    const productId = e.currentTarget.dataset.id
    if (!productId) return
    wx.navigateTo({ url: '/pages/product/detail?id=' + productId })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  onAction(e) {
    const { key, no } = e.currentTarget.dataset
    const handlers = {
      pay: () => this.handlePay(no),
      cancel: () => this.handleCancel(no),
      receive: () => this.handleReceive(no),
      ship: () => this.handleShip(no),
      agreeRefund: () => this.handleAgreeRefund(no),
      cancelRefund: () => this.handleCancelRefund(no),
      refund: () => this.openReasonModal('refund', no),
      rejectRefund: () => this.openReasonModal('rejectRefund', no),
      review: () => wx.showToast({ title: '评价功能将在评价模块上线', icon: 'none' })
    }
    const handler = handlers[key]
    if (handler) handler()
  },

  async handlePay(orderNo) {
    wx.showLoading({ title: '支付中…', mask: true })
    const start = Date.now()
    try {
      await payOrder(orderNo)
      const wait = Math.max(0, 900 - (Date.now() - start))
      setTimeout(() => {
        wx.hideLoading()
        wx.showToast({ title: '支付成功', icon: 'success' })
        this.load(true)
      }, wait)
    } catch (e) {
      wx.hideLoading()
    }
  },

  async handleCancel(orderNo) {
    if (!(await this.confirm('取消订单', '确定取消这笔订单吗？取消后商品会重新上架'))) return
    await this.run(() => cancelOrder(orderNo, ''), '订单已取消')
  },

  async handleReceive(orderNo) {
    if (!(await this.confirm('确认收货', '确认已收到商品？确认后交易完成'))) return
    await this.run(() => receiveOrder(orderNo), '交易完成')
  },

  async handleShip(orderNo) {
    if (!(await this.confirm('确认发货', '确认已发货或已完成当面交付？'))) return
    await this.run(() => shipOrder(orderNo), '已确认发货')
  },

  async handleAgreeRefund(orderNo) {
    if (!(await this.confirm('同意退款', '同意后商品会重新上架，确认继续？'))) return
    await this.run(() => agreeRefund(orderNo), '已同意退款')
  },

  async handleCancelRefund(orderNo) {
    if (!(await this.confirm('撤销退款', '确定撤销本次退款申请吗？'))) return
    await this.run(() => cancelRefund(orderNo), '已撤销退款申请')
  },

  openReasonModal(action, orderNo) {
    const isReject = action === 'rejectRefund'
    this.setData({
      reasonModal: {
        visible: true,
        title: isReject ? '拒绝退款' : '申请退款',
        placeholder: isReject
          ? '请说明拒绝理由（至少 5 个字），买家可据此申请客服介入'
          : '请描述退款原因（至少 5 个字）',
        value: '',
        action,
        orderNo
      }
    })
  },

  onReasonInput(e) {
    this.setData({ 'reasonModal.value': e.detail.value })
  },

  closeReasonModal() {
    this.setData({ 'reasonModal.visible': false })
  },

  async submitReason() {
    const modal = this.data.reasonModal
    const value = (modal.value || '').trim()
    if (value.length < 5) {
      wx.showToast({ title: '原因至少 5 个字', icon: 'none' })
      return
    }
    this.setData({ 'reasonModal.visible': false })
    if (modal.action === 'rejectRefund') {
      await this.run(() => rejectRefund(modal.orderNo, value), '已拒绝退款')
    } else {
      await this.run(() => applyRefund(modal.orderNo, value), '退款申请已提交')
    }
  },

  confirm(title, content) {
    return new Promise(resolve => {
      wx.showModal({
        title,
        content,
        confirmColor: '#FF6B35',
        success: res => resolve(!!res.confirm),
        fail: () => resolve(false)
      })
    })
  },

  async run(task, successText) {
    try {
      await task()
      wx.showToast({ title: successText, icon: 'success' })
      setTimeout(() => this.load(true), 500)
    } catch (e) {
      // 错误提示由请求封装统一处理
    }
  },

  noop() {}
})
