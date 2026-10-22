// 文件路径: miniprogram/pages/chat/index.js
const { getSessionDetail, pageMessages, sendMessage, readSession, deleteSession } = require('../../api/chat')
const { uploadImage } = require('../../utils/request')
const { imgUrl, timeAgo } = require('../../utils/format')
const ws = require('../../utils/ws')

const EMOJIS = ['😀', '😄', '😊', '😉', '😍', '🤔', '😅', '😭', '👍', '🙏', '👌', '❤️', '🎉', '🌹', '💰', '📦']

/**
 * 聊天页。
 * <p>
 * 消息渲染遵循三条规则：
 * 1. 自己的消息靠右、橙色渐变气泡；对方靠左、白色气泡；
 * 2. 系统消息（订单状态变更等）居中灰色小字，不占左右布局；
 * 3. 两条消息间隔超过 5 分钟才显示时间，避免时间戳把气泡列表撑得很碎。
 * <p>
 * 实时性来自 WebSocket；进入页面时先拉一页历史（保证离线期间的消息也能看到），
 * 之后 WS 推来的新消息直接 append，收到已读回执则把对面发来的消息标成已读。
 */
Page({
  data: {
    sessionId: 0,
    session: null,
    messages: [],
    hasMore: false,
    loadingMore: false,
    loading: true,
    inputValue: '',
    sending: false,
    showEmoji: false,
    emojis: EMOJIS,
    peerTyping: false,
    wsStatus: 'closed',
    scrollInto: '',
    peerAvatarText: '?'
  },

  onLoad(options) {
    const sessionId = Number(options.sessionId || 0)
    if (!sessionId) {
      wx.showToast({ title: '参数错误', icon: 'none' })
      return
    }
    this.setData({ sessionId })

    this.handleIncoming = data => {
      if (!data || Number(data.sessionId) !== this.data.sessionId) return
      this.appendMessages([data])
      // 正在看这个会话，收到的消息直接标记已读，避免角标残留
      this.markRead()
    }
    this.handleRead = data => {
      if (!data || Number(data.sessionId) !== this.data.sessionId) return
      const messages = this.data.messages.map(item =>
        item.self && item.isRead === 0 ? { ...item, isRead: 1 } : item
      )
      this.setData({ messages })
    }
    this.handleTyping = data => {
      if (!data || Number(data.sessionId) !== this.data.sessionId) return
      this.setData({ peerTyping: true })
      clearTimeout(this.typingTimer)
      this.typingTimer = setTimeout(() => this.setData({ peerTyping: false }), 3000)
    }
    this.handleStatus = status => this.setData({ wsStatus: status })

    ws.on('message', this.handleIncoming)
    ws.on('read', this.handleRead)
    ws.on('typing', this.handleTyping)
    ws.on('status', this.handleStatus)

    ws.connect()
    this.loadDetail()
  },

  onShow() {
    ws.connect()
  },

  onUnload() {
    ws.off('message', this.handleIncoming)
    ws.off('read', this.handleRead)
    ws.off('typing', this.handleTyping)
    ws.off('status', this.handleStatus)
    clearTimeout(this.typingTimer)
  },

  /** 首屏：会话信息 + 最近 50 条消息 */
  loadDetail() {
    getSessionDetail(this.data.sessionId)
      .then(data => {
        const session = data.session || {}
        wx.setNavigationBarTitle({ title: session.peerNickname || '聊天' })
        const messages = this.decorate(data.messages || [])
        this.setData({
          session,
          messages,
          hasMore: !!data.hasMore,
          loading: false,
          peerAvatarText: (session.peerNickname || '?').slice(0, 1)
        })
        this.markRead()
        this.scrollToBottom()
      })
      .catch(() => this.setData({ loading: false }))
  },

  /**
   * 给消息补上渲染需要的字段：图片绝对地址、卡片价格、是否显示时间。
   */
  decorate(rows) {
    let lastTime = null
    return rows.map(item => {
      const current = item.createTime ? new Date(String(item.createTime).replace(/-/g, '/')).getTime() : Date.now()
      const showTime = !lastTime || current - lastTime > 5 * 60 * 1000
      lastTime = current
      const decorated = {
        ...item,
        showTime,
        timeText: showTime ? timeAgo(item.createTime) : '',
        imageUrl: item.msgType === 2 ? imgUrl(item.content) : '',
        cardCover: item.card && item.card.cover ? imgUrl(item.card.cover) : ''
      }
      return decorated
    })
  },

  appendMessages(rows) {
    if (!rows || !rows.length) return
    const decorated = this.decorate(rows)
    const messages = this.data.messages.concat(decorated)
    this.setData({ messages })
    this.scrollToBottom()
  },

  /** 向上翻更早的消息：游标 = 当前最小消息 ID */
  loadMore() {
    if (!this.data.hasMore || this.data.loadingMore) return
    const first = this.data.messages[0]
    if (!first) return
    this.setData({ loadingMore: true })
    pageMessages(this.data.sessionId, { cursor: first.id, size: 20 })
      .then(rows => {
        const older = this.decorate((rows || []).slice().reverse())
        const messages = older.concat(this.data.messages)
        this.setData({
          messages,
          hasMore: (rows || []).length >= 20,
          loadingMore: false
        })
      })
      .catch(() => this.setData({ loadingMore: false }))
  },

  markRead() {
    readSession(this.data.sessionId).catch(() => {})
  },

  scrollToBottom() {
    setTimeout(() => {
      this.setData({ scrollInto: '' })
      wx.nextTick(() => {
        this.setData({ scrollInto: 'bottom-anchor' })
      })
    }, 60)
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value })
    // 正在输入提示：1.5 秒内只发一次，避免每个字符都推一帧
    if (this.typingSent) return
    this.typingSent = true
    ws.send({ type: 'typing', data: { sessionId: this.data.sessionId } })
    setTimeout(() => {
      this.typingSent = false
    }, 1500)
  },

  toggleEmoji() {
    this.setData({ showEmoji: !this.data.showEmoji })
  },

  pickEmoji(e) {
    const { emoji } = e.currentTarget.dataset
    this.setData({ inputValue: this.data.inputValue + emoji })
  },

  /** 发送文本 */
  sendText() {
    const content = (this.data.inputValue || '').trim()
    if (!content) {
      wx.showToast({ title: '说点什么吧', icon: 'none' })
      return
    }
    if (this.data.sending) return
    this.setData({ sending: true })
    sendMessage({ sessionId: this.data.sessionId, msgType: 1, content })
      .then(vo => {
        this.setData({ inputValue: '', showEmoji: false })
        this.appendMessages([vo])
      })
      .catch(() => {})
      .then(() => this.setData({ sending: false }))
  },

  /** 发送图片：先上传拿到 URL，再作为 msgType=2 的消息发出 */
  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: res => {
        const file = res.tempFiles && res.tempFiles[0]
        if (!file) return
        wx.showLoading({ title: '上传中', mask: true })
        uploadImage(file.tempFilePath)
          .then(url => sendMessage({ sessionId: this.data.sessionId, msgType: 2, content: url }))
          .then(vo => {
            wx.hideLoading()
            this.appendMessages([vo])
          })
          .catch(() => wx.hideLoading())
      }
    })
  },

  /** 发送关联商品卡片（服务端按商品 ID 重新生成卡片内容） */
  sendProductCard() {
    const productId = this.data.session && this.data.session.productId
    if (!productId) return
    sendMessage({ sessionId: this.data.sessionId, msgType: 4, content: String(productId) })
      .then(vo => this.appendMessages([vo]))
      .catch(() => {})
  },

  openProduct() {
    const productId = this.data.session && this.data.session.productId
    if (!productId) return
    wx.navigateTo({ url: '/pages/product/detail?id=' + productId })
  },

  previewImage(e) {
    const { url } = e.currentTarget.dataset
    wx.previewImage({ current: url, urls: this.data.messages.filter(m => m.msgType === 2).map(m => m.imageUrl) })
  },

  openCard(e) {
    const { type, no, id } = e.currentTarget.dataset
    if (type === 'order' && no) {
      wx.navigateTo({ url: '/pages/order/detail?orderNo=' + no })
      return
    }
    if (type === 'product' && id) {
      wx.navigateTo({ url: '/pages/product/detail?id=' + id })
    }
  },

  /** 右上角「更多」：查看商品 / 标记已读 / 删除会话 */
  onMore() {
    const session = this.data.session
    const items = ['标记为已读', '删除会话']
    if (session && session.productId) {
      items.unshift('查看关联商品')
    }
    // 举报入口只在「对方不是自己」时出现：会话接口返回的 peerId 就是对方用户ID
    if (session && session.peerId && !session.peerIsSelf) {
      items.unshift('举报对方用户')
    }
    wx.showActionSheet({
      itemList: items,
      success: res => {
        const label = items[res.tapIndex]
        if (label === '举报对方用户') {
          wx.navigateTo({ url: '/pages/report/index?type=2&targetId=' + session.peerId })
        } else if (label === '查看关联商品') {
          this.openProduct()
        } else if (label === '标记为已读') {
          this.markRead()
          wx.showToast({ title: '已标记', icon: 'success' })
        } else {
          deleteSession(this.data.sessionId)
            .then(() => {
              wx.showToast({ title: '已删除', icon: 'success' })
              setTimeout(() => wx.navigateBack(), 600)
            })
            .catch(() => {})
        }
      }
    })
  }
})
