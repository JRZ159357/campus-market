// 文件路径: miniprogram/pages/order/detail.js
const {
  getOrder,
  payOrder,
  cancelOrder,
  receiveOrder,
  applyRefund,
  cancelRefund,
  shipOrder,
  agreeRefund,
  rejectRefund
} = require('../../api/order')
const { createSession } = require('../../api/chat')
const { getOrderReviews } = require('../../api/review')
const { imgUrl, money, orderStatusMeta } = require('../../utils/format')

Page({
  data: {
    loading: true,
    error: '',
    order: null,
    logs: [],
    buttons: [],
    actionLoading: false,
    reviewInfo: null,
    reasonModal: {
      visible: false,
      title: '',
      placeholder: '',
      min: 5,
      value: '',
      action: ''
    }
  },

  onLoad(options) {
    this.orderNo = options.orderNo
    if (!this.orderNo) {
      this.setData({ loading: false, error: '缺少订单号' })
      return
    }
    this.load()
  },

  onShow() {
    // 从商品详情 / 地址页返回时刷新，保证状态与按钮最新
    if (this.loadedOnce) {
      this.load()
    }
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  async load() {
    this.setData({ loading: !this.loadedOnce, error: '' })
    try {
      const order = await getOrder(this.orderNo)
      const meta = orderStatusMeta(order.status)
      const product = order.product || {}
      const logs = (order.logs || []).map(item => ({
        ...item,
        dotClass: 'dot--' + (orderStatusMeta(item.toStatus).className || '').replace('order-tag--', '')
      }))
      this.setData({
        order: {
          ...order,
          statusMeta: meta,
          priceText: money(order.price),
          product: {
            ...product,
            coverUrl: imgUrl(product.cover),
            priceText: money(product.price)
          }
        },
        // 时间线倒序：最近一次操作在最上面
        logs: logs.reverse(),
        buttons: this.buildButtons(order)
      })
      this.loadedOnce = true
      // 评价信息单独拉取：只有已完成订单才有意义，失败也不影响订单主体展示
      if (order.status === 3) {
        this.loadReviewInfo(order.id)
      } else {
        this.setData({ reviewInfo: null })
      }
    } catch (e) {
      this.setData({ error: e.message || '订单加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * 按服务端下发的 canXxx 组装底部按钮。
   * 规则：次要操作（描边）在左，主操作（渐变）在右，符合单手操作习惯。
   */
  buildButtons(order) {
    const ghost = []
    const primary = []
    if (order.canCancel) ghost.push({ key: 'cancel', text: '取消订单' })
    if (order.canApplyRefund) ghost.push({ key: 'refund', text: '申请退款' })
    if (order.canRejectRefund) ghost.push({ key: 'rejectRefund', text: '拒绝退款' })
    if (order.canCancelRefund) ghost.push({ key: 'cancelRefund', text: '撤销退款' })
    if (order.status === 1 && order.isBuyer) ghost.push({ key: 'remind', text: '提醒发货' })
    // 评价入口三态：未评价 → 评价；已评价可追评 → 追评；已评价且超期/已追评 → 查看评价
    if (order.canReview) {
      ghost.push({ key: 'review', text: '评价' })
    } else if (order.myReviewId && order.canFollowReview) {
      ghost.push({ key: 'follow', text: '追评' })
    } else if (order.myReviewId) {
      ghost.push({ key: 'viewReview', text: '查看评价' })
    }
    // 联系对方：买家联系卖家 / 卖家联系买家，会话对象随角色互换
    if (order.isBuyer || order.isSeller) {
      ghost.push({ key: 'chat', text: order.isBuyer ? '联系卖家' : '联系买家' })
    }

    if (order.canPay) primary.push({ key: 'pay', text: '立即付款' })
    if (order.canShip) primary.push({ key: 'ship', text: '确认发货' })
    if (order.canReceive) primary.push({ key: 'receive', text: '确认收货' })
    if (order.canAgreeRefund) primary.push({ key: 'agreeRefund', text: '同意退款' })
    if ((order.status === 3 || order.status === 4 || order.status === 6) && order.isBuyer) {
      primary.push({ key: 'rebuy', text: '再次购买' })
    }
    return ghost.concat(primary)
  },

  onAction(e) {
    if (this.data.actionLoading) return
    const key = e.currentTarget.dataset.key
    const handlers = {
      pay: () => this.handlePay(),
      cancel: () => this.handleCancel(),
      receive: () => this.handleReceive(),
      ship: () => this.handleShip(),
      refund: () => this.openReasonModal('refund'),
      rejectRefund: () => this.openReasonModal('rejectRefund'),
      cancelRefund: () => this.handleCancelRefund(),
      agreeRefund: () => this.handleAgreeRefund(),
      remind: () => wx.showToast({ title: '已提醒卖家尽快发货', icon: 'none' }),
      review: () => this.handleReview(),
      follow: () => this.handleFollow(),
      viewReview: () => this.scrollToReview(),
      rebuy: () => this.handleRebuy(),
      chat: () => this.handleChat()
    }
    const handler = handlers[key]
    if (handler) handler()
  },

  /** 模拟支付：接口调用 + 至少 900ms 的 loading，避免一闪而过没有反馈 */
  async handlePay() {
    this.setData({ actionLoading: true })
    wx.showLoading({ title: '支付中…', mask: true })
    const start = Date.now()
    try {
      await payOrder(this.orderNo)
      const wait = Math.max(0, 900 - (Date.now() - start))
      setTimeout(() => {
        wx.hideLoading()
        wx.showToast({ title: '支付成功', icon: 'success' })
        this.setData({ actionLoading: false })
        this.load()
      }, wait)
    } catch (e) {
      wx.hideLoading()
      this.setData({ actionLoading: false })
    }
  },

  async handleCancel() {
    const confirm = await this.confirm('取消订单', '确定取消这笔订单吗？取消后商品会重新上架')
    if (!confirm) return
    await this.run(() => cancelOrder(this.orderNo, ''), '订单已取消')
  },

  async handleReceive() {
    const confirm = await this.confirm('确认收货', '确认已收到商品？确认后交易完成，款项将结算给卖家')
    if (!confirm) return
    await this.run(() => receiveOrder(this.orderNo), '交易完成')
  },

  async handleShip() {
    const confirm = await this.confirm('确认发货', '确认已发货或已完成当面交付？确认后等待买家收货')
    if (!confirm) return
    await this.run(() => shipOrder(this.orderNo), '已确认发货')
  },

  async handleAgreeRefund() {
    const confirm = await this.confirm('同意退款', '同意后订单将完成退款，商品会重新上架，确认继续？')
    if (!confirm) return
    await this.run(() => agreeRefund(this.orderNo), '已同意退款')
  },

  async handleCancelRefund() {
    const confirm = await this.confirm('撤销退款', '确定撤销本次退款申请吗？订单会回到退款前的状态')
    if (!confirm) return
    await this.run(() => cancelRefund(this.orderNo), '已撤销退款申请')
  },

  handleRebuy() {
    const productId = this.data.order && this.data.order.product && this.data.order.product.productId
    if (!productId) {
      wx.showToast({ title: '商品信息缺失', icon: 'none' })
      return
    }
    wx.navigateTo({ url: '/pages/product/detail?id=' + productId })
  },

  /**
   * 联系对方：打开与订单关联商品的会话。
   * 买家点「联系卖家」时 sellerId 取订单里的卖家；卖家点「联系买家」时同理取买家。
   */
  handleChat() {
    const order = this.data.order
    if (!order) return
    const peerId = order.isBuyer ? order.sellerId : order.buyerId
    if (!peerId) {
      wx.showToast({ title: '对方信息缺失', icon: 'none' })
      return
    }
    wx.showLoading({ title: '正在打开会话', mask: true })
    createSession({ sellerId: peerId, productId: order.product && order.product.productId })
      .then(session => {
        wx.hideLoading()
        wx.navigateTo({ url: '/pages/chat/index?sessionId=' + session.id })
      })
      .catch(() => wx.hideLoading())
  },

  /**
   * 拉取订单评价。界面上要同时展示「我的评价」与「对方的评价」，
   * 所以用 order 维度的接口一次拿回两条，而不是各查一次。
   */
  async loadReviewInfo(orderId) {
    try {
      const info = await getOrderReviews(orderId)
      const decorate = review => (review ? Object.assign({}, review, {
        images: (review.images || []).map(imgUrl),
        followImages: (review.followImages || []).map(imgUrl)
      }) : null)
      this.setData({
        reviewInfo: {
          ...info,
          buyerReview: decorate(info.buyerReview),
          sellerReview: decorate(info.sellerReview)
        }
      })
    } catch (e) {
      this.setData({ reviewInfo: null })
    }
  },

  handleReview() {
    const order = this.data.order
    if (!order) return
    wx.navigateTo({ url: '/pages/review/edit?orderId=' + order.id })
  },

  handleFollow() {
    const order = this.data.order
    if (!order || !order.myReviewId) return
    wx.navigateTo({ url: '/pages/review/follow?reviewId=' + order.myReviewId })
  },

  /** 「查看评价」把页面滚到评价区，不额外开一个只读页面 */
  scrollToReview() {
    wx.pageScrollTo({ selector: '.review-section', duration: 260 })
  },

  previewReviewImage(e) {
    const { field, imgIndex } = e.currentTarget.dataset
    const review = this.data.reviewInfo && this.data.reviewInfo[field]
    if (!review) return
    const urls = review.images || []
    wx.previewImage({ current: urls[Number(imgIndex) || 0], urls })
  },

  openReasonModal(action) {
    const isReject = action === 'rejectRefund'
    this.setData({
      reasonModal: {
        visible: true,
        title: isReject ? '拒绝退款' : '申请退款',
        placeholder: isReject
          ? '请说明拒绝理由，买家可据此申请客服介入（至少 5 个字）'
          : '请描述退款原因，如商品与描述不符、有损坏等（至少 5 个字）',
        min: 5,
        value: '',
        action
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
    if (value.length < modal.min) {
      wx.showToast({ title: '原因至少 ' + modal.min + ' 个字', icon: 'none' })
      return
    }
    this.setData({ 'reasonModal.visible': false })
    if (modal.action === 'rejectRefund') {
      await this.run(() => rejectRefund(this.orderNo, value), '已拒绝退款')
    } else {
      await this.run(() => applyRefund(this.orderNo, value), '退款申请已提交')
    }
  },

  /** 统一的二次确认，返回 Promise<boolean> */
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

  /** 统一执行写操作：loading → 提示 → 刷新详情 */
  async run(task, successText) {
    this.setData({ actionLoading: true })
    try {
      await task()
      wx.showToast({ title: successText, icon: 'success' })
      setTimeout(() => this.load(), 500)
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ actionLoading: false })
    }
  },

  copyOrderNo() {
    wx.setClipboardData({ data: this.data.order.orderNo })
  },

  /** 阻止弹窗内容区的点击穿透到遮罩 */
  noop() {},

  contactSeller() {
    wx.showToast({ title: '聊天功能将在消息模块上线', icon: 'none' })
  },

  goProduct() {
    const productId = this.data.order && this.data.order.product && this.data.order.product.productId
    if (!productId) return
    wx.navigateTo({ url: '/pages/product/detail?id=' + productId })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  }
})
