// 文件路径: miniprogram/pages/message/index.js
const { pageSessions, readSession } = require('../../api/chat')
const { pageNotices, readAllNotices } = require('../../api/notice')
const { timeAgo } = require('../../utils/format')
const ws = require('../../utils/ws')

/**
 * 消息首页：会话 + 系统通知两个 Tab。
 * <p>
 * 两个列表共用一套「下拉刷新 + 上拉加载」逻辑，用 activeTab 区分数据源；
 * WebSocket 收到新消息 / 新通知时只做「静默重载第一页」，避免把用户正在翻的列表顶掉。
 */
Page({
  data: {
    tabs: [
      { key: 'chat', label: '消息' },
      { key: 'notice', label: '通知' }
    ],
    activeIndex: 0,
    sessions: [],
    notices: [],
    loading: false,
    unread: { chatUnread: 0, noticeUnread: 0, total: 0 },
    wsStatus: 'closed',
    pageNum: 1,
    hasMore: true
  },

  onLoad() {
    this.handleNewMessage = () => this.reload(1)
    this.handleNewNotice = () => this.reload(1)
    this.handleUnread = summary => {
      if (summary) this.setData({ unread: summary })
    }
    this.handleStatus = status => this.setData({ wsStatus: status })
    ws.on('message', this.handleNewMessage)
    ws.on('notice', this.handleNewNotice)
    ws.on('unread', this.handleUnread)
    ws.on('status', this.handleStatus)
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 3 })
      this.getTabBar().refresh()
    }
    if (!getApp().isLogin()) {
      this.setData({ sessions: [], notices: [] })
      return
    }
    ws.connect()
    this.reload(1)
  },

  onUnload() {
    ws.off('message', this.handleNewMessage)
    ws.off('notice', this.handleNewNotice)
    ws.off('unread', this.handleUnread)
    ws.off('status', this.handleStatus)
  },

  onPullDownRefresh() {
    this.reload(1).then(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loading) return
    this.reload(this.data.pageNum + 1)
  },

  onTabTap(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (index === this.data.activeIndex) return
    this.setData({ activeIndex: index, pageNum: 1, hasMore: true })
    this.reload(1)
  },

  /**
   * 拉取当前 Tab 的一页数据。pageNum=1 视为刷新（替换），否则追加。
   */
  reload(pageNum) {
    if (!getApp().isLogin()) return Promise.resolve()
    const isChat = this.data.activeIndex === 0
    this.setData({ loading: true })
    const task = isChat
      ? pageSessions({ pageNum, pageSize: 20 }).then(data => {
          const rows = (data.records || []).map(item => ({
            ...item,
            timeText: timeAgo(item.lastTime),
            avatarText: (item.peerNickname || '?').slice(0, 1),
            preview: item.lastMessage || '开始聊聊这件闲置吧'
          }))
          const list = pageNum === 1 ? rows : this.data.sessions.concat(rows)
          this.setData({
            sessions: list,
            pageNum,
            hasMore: list.length < Number(data.total || 0)
          })
        })
      : pageNotices({ pageNum, pageSize: 20 }).then(data => {
          const rows = (data.records || []).map(item => ({
            ...item,
            timeText: timeAgo(item.createTime),
            icon: this.iconOf(item.type)
          }))
          const list = pageNum === 1 ? rows : this.data.notices.concat(rows)
          this.setData({
            notices: list,
            pageNum,
            hasMore: list.length < Number(data.total || 0)
          })
        })

    return task
      .catch(() => {})
      .then(() => {
        this.setData({ loading: false })
        this.refreshUnread()
      })
  },

  refreshUnread() {
    const { getUnread } = require('../../api/chat')
    getUnread()
      .then(summary => this.setData({ unread: summary }))
      .catch(() => {})
  },

  iconOf(type) {
    switch (Number(type)) {
      case 1:
        return '📦'
      case 2:
        return '✅'
      case 4:
        return '⚠️'
      default:
        return '📢'
    }
  },

  openChat(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: '/pages/chat/index?sessionId=' + id })
  },

  /**
   * 长按会话：删除（只删自己这一侧）或标记已读
   */
  onSessionLongPress(e) {
    const { id } = e.currentTarget.dataset
    wx.showActionSheet({
      itemList: ['标记为已读', '删除会话'],
      success: res => {
        if (res.tapIndex === 0) {
          readSession(id).then(() => this.reload(1)).catch(() => {})
          return
        }
        const { deleteSession } = require('../../api/chat')
        deleteSession(id)
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' })
            this.reload(1)
          })
          .catch(() => {})
      }
    })
  },

  openNotice(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: '/pages/notice/detail?id=' + id })
  },

  readAll() {
    readAllNotices()
      .then(() => {
        wx.showToast({ title: '已全部已读', icon: 'success' })
        this.reload(1)
      })
      .catch(() => {})
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/index' })
  }
})
