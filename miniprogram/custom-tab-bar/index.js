// 文件路径: miniprogram/custom-tab-bar/index.js
const { getAuthStatus } = require('../api/user')
const { getUnread } = require('../api/chat')
const ws = require('../utils/ws')

/**
 * 自定义 tabBar。
 * <p>
 * 为什么不用原生 tabBar：中间的「发布」不是普通页面入口——未认证用户点击要先引导认证，
 * 且发布页需要支持编辑模式（navigateTo 带 id）。原生 tabBar 既不能拦截点击，
 * 也不允许对 tab 页使用 navigateTo。自定义 tabBar 把「发布」做成独立按钮，
 * 其余 4 项仍走 switchTab，编辑流程不受影响。
 * <p>
 * 未读角标：tabBar 自己订阅 WebSocket 的 unread 帧，同时暴露 refresh() 给各页面在 onShow 时调用。
 * 这样不管用户从哪个页面切回来，角标都是最新的；WS 在线时收到新消息也会立刻变化。
 * 超过 99 显示 99+，与小程序惯例一致。
 */
Component({
  data: {
    selected: 0,
    unread: 0,
    unreadText: '',
    list: [
      { key: 'home', label: '首页', icon: '🏠', path: '/pages/index/index' },
      { key: 'category', label: '分类', icon: '🗂', path: '/pages/category/index' },
      { key: 'publish', label: '发布', icon: '＋', center: true },
      { key: 'message', label: '消息', icon: '💬', path: '/pages/message/index' },
      { key: 'profile', label: '我的', icon: '👤', path: '/pages/profile/index' }
    ]
  },

  lifetimes: {
    attached() {
      this.handleUnread = summary => this.applyUnread(summary)
      ws.on('unread', this.handleUnread)
      this.refresh()
    },
    detached() {
      ws.off('unread', this.handleUnread)
    }
  },

  methods: {
    /**
     * 重新拉一次未读数。未登录时不请求，角标清零。
     */
    refresh() {
      const app = getApp()
      if (!app || !app.isLogin()) {
        this.setData({ unread: 0, unreadText: '' })
        return
      }
      getUnread()
        .then(summary => this.applyUnread(summary))
        .catch(() => {})
    },

    applyUnread(summary) {
      const total = Number((summary && summary.total) || 0)
      this.setData({
        unread: total,
        unreadText: total > 99 ? '99+' : total > 0 ? String(total) : ''
      })
    },

    onTap(e) {
      const index = e.currentTarget.dataset.index
      const item = this.data.list[index]
      if (!item) return
      if (item.center) {
        this.handlePublish()
        return
      }
      if (index === this.data.selected) return
      wx.switchTab({ url: item.path })
    },

    async handlePublish() {
      const app = getApp()
      if (!app.isLogin()) {
        wx.navigateTo({ url: '/pages/login/index' })
        return
      }

      let verified = app.globalData.verified
      if (verified === undefined) {
        try {
          const status = await getAuthStatus()
          verified = !!(status && status.verified)
          app.globalData.verified = verified
        } catch (e) {
          verified = false
        }
      }

      if (!verified) {
        wx.showModal({
          title: '需要学生认证',
          content: '发布商品需要先完成学生认证，认证通过后即可发布闲置。',
          confirmText: '去认证',
          cancelText: '再看看',
          success: res => {
            if (res.confirm) {
              wx.navigateTo({ url: '/pages/auth/student' })
            }
          }
        })
        return
      }
      wx.navigateTo({ url: '/pages/publish/index' })
    }
  }
})
