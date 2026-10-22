// 文件路径: miniprogram/pages/report/my.js
const { myReports, myReportDetail, HANDLE_ACTIONS, STATUS_META, reasonLabel, typeMeta } = require('../../api/report')
const { imgUrl } = require('../../utils/format')

const PAGE_SIZE = 10

/**
 * 我的举报列表。
 * <p>
 * 展示重点是「我提的问题被处理成什么样了」，所以每条都直接显示处理结论摘要，
 * 不必点进详情才知道结果——用户提交举报时最关心的就是这个。
 */
Page({
  data: {
    list: [],
    pageNum: 1,
    hasMore: true,
    loading: false,
    loadingMore: false,
    refreshing: false,

    detailVisible: false,
    detail: null,
    statusFilter: -1
  },

  onLoad(options) {
    if (!getApp().isLogin()) {
      wx.redirectTo({ url: '/pages/login/index' })
      return
    }
    this.reload()
  },

  onShow() {
    // 从举报提交页跳回来时状态可能变了，需要刷新。
    // _loaded 标记保证首次进入不会重复请求（onLoad 已调过一次 reload）
    if (this._loaded) {
      this.reload(true)
    }
    this._loaded = true
  },

  async reload(silent = false) {
    if (silent) {
      this.setData({ refreshing: true })
    } else {
      this.setData({ loading: true, pageNum: 1, hasMore: true, list: [] })
    }
    await this.fetch(1, false)
  },

  async fetch(pageNum, isLoadMore) {
    try {
      const page = await myReports({ pageNum, pageSize: PAGE_SIZE })
      const records = (page && page.records) || []
      const decorated = records.map(this.decorate)
      const list = isLoadMore ? this.data.list.concat(decorated) : decorated
      this.setData({
        list,
        hasMore: list.length < Number((page && page.total) || 0),
        loading: false,
        loadingMore: false,
        refreshing: false
      })
    } catch (e) {
      this.setData({ loading: false, loadingMore: false, refreshing: false })
    }
  },

  /** 预生成模板要用的文案，避免 wxml 里做字符串处理 */
  decorate(item) {
    const meta = typeMeta(item.reportType)
    const status = STATUS_META[item.status] || STATUS_META[3]
    return {
      id: item.id,
      reportType: item.reportType,
      typeLabel: meta.typeLabel,
      color: meta.color,
      bg: meta.bg,
      statusLabel: item.statusLabel || status.label,
      statusColor: status.color,
      statusBg: status.bg,
      reasonText: reasonLabel(item.reason),
      content: item.content,
      targetTitle: item.targetTitle || '（对象已不存在）',
      targetCover: imgUrl(item.targetCover),
      timeText: (item.createTime || '').slice(5, 16),
      // 处理结论摘要：已处理/已驳回才显示，未终结的显示一个进度提示
      resultText: item.handleResult || '',
      actionText: HANDLE_ACTIONS[item.handleAction] || '未处理',
      finished: item.status === 2 || item.status === 3
    }
  },

  onPullDownRefresh() {
    this.reload(true).then(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loadingMore || this.data.loading) {
      return
    }
    this.setData({ loadingMore: true, pageNum: this.data.pageNum + 1 })
    this.fetch(this.data.pageNum, true)
  },

  /** 点击列表项：展开该条的处理详情 */
  async openDetail(e) {
    const id = e.currentTarget.dataset.id
    try {
      const detail = await myReportDetail(id)
      const meta = typeMeta(detail.reportType)
      const status = STATUS_META[detail.status] || STATUS_META[3]
      this.setData({
        detailVisible: true,
        detail: {
          id: detail.id,
          typeLabel: meta.typeLabel,
          color: meta.color,
          targetTitle: detail.targetTitle || '（对象已不存在）',
          targetCover: imgUrl(detail.targetCover),
          reasonText: reasonLabel(detail.reason),
          content: detail.content,
          images: (detail.images || []).map(imgUrl),
          statusLabel: detail.statusLabel || status.label,
          statusColor: status.color,
          statusBg: status.bg,
          actionText: HANDLE_ACTIONS[detail.handleAction] || '未处理',
          resultText: detail.handleResult || '',
          createTime: detail.createTime,
          acceptTime: detail.acceptTime || '',
          handleTime: detail.handleTime || '',
          finished: detail.status === 2 || detail.status === 3
        }
      })
    } catch (e) {
      // 统一提示由 request 给出
    }
  },

  closeDetail() {
    this.setData({ detailVisible: false })
  },

  stopPropagation() {
    // 阻止点击弹层内容时冒泡到遮罩
  },

  previewImage(e) {
    const urls = (this.data.detail && this.data.detail.images) || []
    if (!urls.length) {
      return
    }
    const index = Number(e.currentTarget.dataset.index)
    wx.previewImage({ current: urls[index], urls })
  }
})