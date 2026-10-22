// 文件路径: tests/mp_chat_check.cjs
/**
 * 模块6 小程序页面逻辑离线校验。
 * <p>
 * 用假的 wx / Page / getApp 驱动真实页面代码，验证：
 * 消息首页（双 Tab、未读角标、跳转、全部已读）、聊天页（消息装饰、发送、表情、卡片跳转、加载更早）、
 * 通知详情页（按 target 跳转、打开即已读）。
 * 不需要后端与微信开发者工具，直接 node 运行：
 *
 *   node tests/mp_chat_check.cjs
 */

const path = require('path')

const ROOT = 'D:/毕业设计/skill/campus-market/miniprogram'
const toasts = []
const navigations = []
const requests = []
const titles = []
let actionSheetIndex = 0

/* ------------------------------------------------------------------ */
/* 预置接口数据                                                        */
/* ------------------------------------------------------------------ */

const sessionRows = [
  {
    id: 7, peerId: 9, peerNickname: '李同学', peerAvatar: null, peerVerified: true,
    productId: 41, productTitle: '九成新山地自行车', productCover: '/uploads/bike.png',
    lastMessage: '这本书还在吗？', lastTime: '2026-10-21 12:00:00', unread: 2, buyer: true
  },
  {
    id: 8, peerId: 10, peerNickname: '王同学', peerAvatar: null, peerVerified: false,
    productId: null, productTitle: null, productCover: null,
    lastMessage: null, lastTime: '2026-10-21 10:00:00', unread: 0, buyer: false
  }
]

const noticeRows = [
  {
    id: 101, type: 1, typeLabel: '订单通知', title: '买家已付款',
    content: '订单 202610211200001234 已付款，请尽快发货。', relatedId: 5,
    relatedNo: '202610211200001234', target: 'order', isRead: 0,
    createTime: '2026-10-21 12:00:00'
  },
  {
    id: 102, type: 3, typeLabel: '系统公告', title: '国庆假期快递点调整',
    content: '10 月 1 日至 3 日快递点暂停服务。', relatedId: null, relatedNo: null,
    target: 'none', isRead: 1, createTime: '2026-10-20 09:00:00'
  }
]

const chatMessages = [
  {
    id: 1, sessionId: 7, senderId: 5, receiverId: 9, msgType: 4, msgTypeLabel: '商品卡片',
    content: '{"productId":41,"title":"九成新山地自行车","price":120.00,"cover":"/uploads/bike.png","status":1,"statusLabel":"已上架"}',
    card: { productId: 41, title: '九成新山地自行车', price: 120.0, cover: '/uploads/bike.png', status: 1, statusLabel: '已上架' },
    isRead: 1, self: true, fromBuyer: true, createTime: '2026-10-21 11:00:00'
  },
  {
    id: 2, sessionId: 7, senderId: 9, receiverId: 5, msgType: 1, msgTypeLabel: '文本',
    content: '在的，随时可以来看车', isRead: 1, self: false, fromBuyer: false,
    createTime: '2026-10-21 11:02:00'
  },
  {
    id: 3, sessionId: 7, senderId: 0, receiverId: 0, msgType: 3, msgTypeLabel: '系统消息',
    content: '买家已下单，成交价 120.00 元', isRead: 1, self: false, fromBuyer: false,
    createTime: '2026-10-21 11:05:00'
  },
  {
    id: 4, sessionId: 7, senderId: 9, receiverId: 5, msgType: 2, msgTypeLabel: '图片',
    content: '/uploads/photo.png', isRead: 1, self: false, fromBuyer: false,
    createTime: '2026-10-21 11:08:00'
  },
  {
    id: 5, sessionId: 7, senderId: 9, receiverId: 5, msgType: 5, msgTypeLabel: '订单卡片',
    content: '{"orderNo":"202610211200001234","price":120.00,"status":3,"statusLabel":"已完成"}',
    card: { orderNo: '202610211200001234', price: 120.0, status: 3, statusLabel: '已完成', title: '九成新山地自行车' },
    isRead: 0, self: false, fromBuyer: false, createTime: '2026-10-21 12:00:00'
  }
]

const chatDetail = {
  session: sessionRows[0],
  messages: chatMessages,
  hasMore: false
}

function resolve(options) {
  const url = String(options.url).replace('http://localhost:8080', '')
  const clean = url.split('?')[0]
  const query = url.split('?')[1] || ''
  const key = (options.method || 'GET') + ' ' + clean
  requests.push(key)

  if (key === 'GET /api/chat/session/list') {
    return { code: 200, data: { records: sessionRows, total: 2, current: 1, size: 20 } }
  }
  if (key === 'GET /api/chat/session/7') {
    return { code: 200, data: chatDetail }
  }
  if (key === 'POST /api/chat/session') {
    return { code: 200, msg: '会话已创建', data: { id: 7 } }
  }
  if (key === 'GET /api/chat/message/7') {
    // 带 cursor 视为加载更早消息
    if (query.indexOf('cursor=') >= 0) {
      return {
        code: 200,
        data: [{
          id: 0, sessionId: 7, senderId: 5, receiverId: 9, msgType: 1, msgTypeLabel: '文本',
          content: '更早的一条消息', isRead: 1, self: true, fromBuyer: true,
          createTime: '2026-10-21 10:50:00'
        }]
      }
    }
    return { code: 200, data: chatMessages.slice().reverse() }
  }
  if (key === 'POST /api/chat/message') {
    const body = options.data || {}
    return {
      code: 200,
      msg: '已发送',
      data: {
        id: 99, sessionId: 7, senderId: 5, receiverId: 9, msgType: body.msgType,
        msgTypeLabel: '文本', content: body.content, isRead: 0, self: true, fromBuyer: true,
        createTime: '2026-10-21 12:30:00'
      }
    }
  }
  if (key === 'PUT /api/chat/message/read/7') {
    return { code: 200, msg: '已读', data: null }
  }
  if (key === 'GET /api/chat/unread') {
    return { code: 200, data: { chatUnread: 3, unreadSessions: 2, noticeUnread: 1, total: 4 } }
  }
  if (key === 'GET /api/notice/list') {
    return { code: 200, data: { records: noticeRows, total: 2, current: 1, size: 20 } }
  }
  if (key === 'GET /api/notice/101') {
    return { code: 200, data: noticeRows[0] }
  }
  if (key === 'GET /api/notice/102') {
    return { code: 200, data: noticeRows[1] }
  }
  if (key.indexOf('PUT /api/notice/read/') === 0) {
    return { code: 200, msg: '已读', data: null }
  }
  if (key === 'PUT /api/notice/read-all') {
    return { code: 200, msg: '已全部标记为已读', data: 2 }
  }
  if (key === 'DELETE /api/chat/session/7') {
    return { code: 200, msg: '会话已删除', data: null }
  }
  return { code: 500, msg: '未预置响应: ' + key }
}

/* ------------------------------------------------------------------ */
/* 假 wx / getApp / Page                                              */
/* ------------------------------------------------------------------ */

function fakeSocketTask() {
  return {
    onOpen() {},
    onMessage() {},
    onClose() {},
    onError() {},
    send() {},
    close() {}
  }
}

global.wx = {
  getStorageSync: () => 'fake-token',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  request(options) {
    const body = resolve(options)
    options.success && options.success({ data: body })
    options.complete && options.complete()
  },
  connectSocket: () => fakeSocketTask(),
  showToast: opt => toasts.push(opt.title),
  showLoading: () => {},
  hideLoading: () => {},
  showModal: opt => opt.success && opt.success({ confirm: true }),
  showActionSheet: opt => opt.success && opt.success({ tapIndex: actionSheetIndex }),
  navigateTo: opt => navigations.push(opt.url),
  redirectTo: opt => navigations.push(opt.url),
  switchTab: opt => navigations.push(opt.url),
  navigateBack: () => navigations.push('back'),
  setNavigationBarTitle: opt => titles.push(opt.title),
  previewImage: () => {},
  nextTick: fn => fn(),
  chooseMedia: opt => opt.success && opt.success({ tempFiles: [{ tempFilePath: '/tmp/a.png' }] }),
  stopPullDownRefresh: () => {},
  uploadFile: opt => {
    opt.success && opt.success({ data: JSON.stringify({ code: 200, data: '/uploads/a.png' }) })
  }
}

const appStub = {
  globalData: { token: 'fake-token', userInfo: { id: 5 } },
  isLogin: () => true
}
global.getApp = () => appStub

let captured = null
global.Page = obj => {
  captured = obj
}

function setByPath(target, p, value) {
  const tokens = p.replace(/\[(\d+)\]/g, '.$1').split('.')
  let node = target
  for (let i = 0; i < tokens.length - 1; i++) {
    if (node[tokens[i]] === undefined) node[tokens[i]] = {}
    node = node[tokens[i]]
  }
  node[tokens[tokens.length - 1]] = value
}

function instantiate(file, options = {}) {
  captured = null
  const full = path.join(ROOT, file)
  delete require.cache[require.resolve(full)]
  require(full)
  const page = Object.assign({}, captured)
  page.data = JSON.parse(JSON.stringify(captured.data))
  page.setData = function (patch) {
    Object.keys(patch).forEach(k => setByPath(this.data, k, patch[k]))
  }
  page.getTabBar = () => ({ setData() {}, refresh() {} })
  if (page.onLoad) page.onLoad(options)
  return page
}

const sleep = ms => new Promise(r => setTimeout(r, ms))

const results = []
function check(name, ok, detail) {
  results.push([name, ok, detail])
  console.log((ok ? '  [PASS] ' : '  [FAIL] ') + name
    + (detail !== undefined ? '  -> ' + JSON.stringify(detail) : ''))
}

/* ------------------------------------------------------------------ */
/* 用例                                                               */
/* ------------------------------------------------------------------ */

async function testMessagePage() {
  console.log('\n== 1. 消息首页 ==')
  const page = instantiate('pages/message/index.js')
  await page.reload(1)
  await sleep(50)

  check('默认停在「消息」Tab', page.data.activeIndex === 0, page.data.activeIndex)
  check('加载到 2 个会话', page.data.sessions.length === 2, page.data.sessions.length)
  check('会话补齐头像首字', page.data.sessions[0].avatarText === '李', page.data.sessions[0].avatarText)
  check('会话补齐时间文案', !!page.data.sessions[0].timeText, page.data.sessions[0].timeText)
  check('无摘要时补默认文案',
    page.data.sessions[1].preview === '开始聊聊这件闲置吧', page.data.sessions[1].preview)
  check('角标取未读总数', page.data.unread.total === 4, page.data.unread.total)

  page.openChat({ currentTarget: { dataset: { id: 7 } } })
  check('点击会话跳聊天页', navigations.includes('/pages/chat/index?sessionId=7'),
    navigations[navigations.length - 1])

  page.onTabTap({ currentTarget: { dataset: { index: 1 } } })
  await sleep(50)
  check('切到通知 Tab', page.data.activeIndex === 1, page.data.activeIndex)
  check('加载到 2 条通知', page.data.notices.length === 2, page.data.notices.length)
  check('通知按类型选图标', page.data.notices[0].icon === '📦', page.data.notices[0].icon)
  check('公告类图标为喇叭', page.data.notices[1].icon === '📢', page.data.notices[1].icon)

  page.openNotice({ currentTarget: { dataset: { id: 101 } } })
  check('点击通知跳详情页', navigations.includes('/pages/notice/detail?id=101'),
    navigations[navigations.length - 1])

  page.readAll()
  await sleep(50)
  check('全部已读调用接口后刷新列表', requests.includes('PUT /api/notice/read-all'))
}

async function testChatPage() {
  console.log('\n== 2. 聊天页 ==')
  const page = instantiate('pages/chat/index.js', { sessionId: '7' })
  await sleep(120)

  check('会话ID解析正确', page.data.sessionId === 7, page.data.sessionId)
  check('加载到 5 条消息', page.data.messages.length === 5, page.data.messages.length)
  check('设置导航栏为对方昵称', titles.includes('李同学'), titles[titles.length - 1])
  check('进入即调用标记已读', requests.includes('PUT /api/chat/message/read/7'))
  check('第一条显示时间分隔', page.data.messages[0].showTime === true)
  check('间隔小于5分钟不重复显示时间', page.data.messages[1].showTime === false,
    page.data.messages[1].showTime)
  check('间隔超过5分钟重新显示时间', page.data.messages[4].showTime === true,
    page.data.messages[4].showTime)
  check('图片消息转绝对地址',
    page.data.messages[3].imageUrl.indexOf('/uploads/photo.png') > 0,
    page.data.messages[3].imageUrl)
  check('商品卡片封面转绝对地址',
    page.data.messages[0].cardCover.indexOf('/uploads/bike.png') > 0,
    page.data.messages[0].cardCover)
  check('系统消息识别为 msgType=3', page.data.messages[2].msgType === 3)
  check('自己的消息 self=true', page.data.messages[0].self === true)

  // 发文本
  page.onInput({ detail: { value: '好的，我下午过去' } })
  check('输入框取值', page.data.inputValue === '好的，我下午过去')
  page.sendText()
  await sleep(80)
  check('发送后清空输入框', page.data.inputValue === '')
  check('发送后消息数 +1', page.data.messages.length === 6, page.data.messages.length)
  check('新消息追加在末尾', page.data.messages[5].content === '好的，我下午过去',
    page.data.messages[5].content)

  // 表情
  page.toggleEmoji()
  check('打开表情面板', page.data.showEmoji === true)
  page.pickEmoji({ currentTarget: { dataset: { emoji: '👍' } } })
  check('点击表情拼进输入框', page.data.inputValue === '👍', page.data.inputValue)

  // 空内容发送
  page.setData({ inputValue: '   ' })
  const before = toasts.length
  page.sendText()
  await sleep(30)
  check('空白内容不发送并提示', toasts.length > before && toasts[toasts.length - 1] === '说点什么吧',
    toasts[toasts.length - 1])

  // 卡片跳转
  navigations.length = 0
  page.openCard({ currentTarget: { dataset: { type: 'product', id: 41 } } })
  check('商品卡片跳商品详情', navigations.includes('/pages/product/detail?id=41'),
    navigations[navigations.length - 1])
  page.openCard({ currentTarget: { dataset: { type: 'order', no: '202610211200001234' } } })
  check('订单卡片跳订单详情', navigations.includes('/pages/order/detail?orderNo=202610211200001234'),
    navigations[navigations.length - 1])

  // 关联商品快捷入口
  page.openProduct()
  check('顶部商品条跳商品详情', navigations.includes('/pages/product/detail?id=41'))

  // 更多操作：查看关联商品（actionSheet 第 0 项）
  actionSheetIndex = 0
  page.onMore()
  check('更多菜单可查看关联商品', navigations.includes('/pages/product/detail?id=41'))

  // 收到对方新消息
  page.handleIncoming({
    id: 120, sessionId: 7, senderId: 9, msgType: 1, content: '好的，我在图书馆等你',
    self: false, fromBuyer: false, isRead: 0, createTime: '2026-10-21 12:40:00'
  })
  await sleep(30)
  check('收到新消息追加到列表', page.data.messages.length === 7, page.data.messages.length)
  check('收到的消息 self=false', page.data.messages[6].self === false)
  check('收到消息后自动已读',
    requests.filter(r => r === 'PUT /api/chat/message/read/7').length >= 2,
    requests.filter(r => r === 'PUT /api/chat/message/read/7').length)

  // 无关会话的消息不应插入
  const countBefore = page.data.messages.length
  page.handleIncoming({ id: 121, sessionId: 999, msgType: 1, content: '别的会话' })
  check('其他会话的消息被忽略', page.data.messages.length === countBefore)

  // 已读回执
  page.handleRead({ sessionId: 7, readerId: 9 })
  check('收到已读回执后把自己消息标记已读',
    page.data.messages.filter(m => m.self && m.isRead === 0).length === 0)

  // typing
  page.handleTyping({ sessionId: 7, userId: 9 })
  check('显示对方正在输入', page.data.peerTyping === true)

  // 加载更早消息
  page.setData({ hasMore: true })
  page.loadMore()
  await sleep(60)
  check('加载更早消息成功', page.data.messages.length === countBefore + 1,
    page.data.messages.length)
  check('更早的消息插到最前面', page.data.messages[0].content === '更早的一条消息',
    page.data.messages[0].content)

  // 发送商品卡片
  const before2 = page.data.messages.length
  page.sendProductCard()
  await sleep(60)
  check('发送商品卡片后消息 +1', page.data.messages.length === before2 + 1)
  check('卡片消息类型为 4', page.data.messages[page.data.messages.length - 1].msgType === 4)

  // 图片消息
  const before3 = page.data.messages.length
  page.chooseImage()
  await sleep(100)
  check('发送图片后消息 +1', page.data.messages.length === before3 + 1)
  check('上传后图片消息类型为 2',
    page.data.messages[page.data.messages.length - 1].msgType === 2,
    page.data.messages[page.data.messages.length - 1].msgType)
}

async function testNoticeDetail() {
  console.log('\n== 3. 通知详情页 ==')
  navigations.length = 0
  const page = instantiate('pages/notice/detail.js', { id: '101' })
  await sleep(80)

  check('加载通知详情', !!page.data.notice && page.data.notice.id === 101)
  check('标题正确', page.data.notice.title === '买家已付款', page.data.notice.title)
  check('按类型选图标', page.data.icon === '📦', page.data.icon)
  check('未读通知打开即标记已读', requests.includes('PUT /api/notice/read/101'))
  check('按钮文案取决于 target=order',
    page.data.notice.target === 'order', page.data.notice.target)

  page.goRelated()
  check('订单通知跳订单详情',
    navigations.includes('/pages/order/detail?orderNo=202610211200001234'),
    navigations[navigations.length - 1])

  const noticePage = instantiate('pages/notice/detail.js', { id: '102' })
  await sleep(80)
  check('公告类通知已读状态保留', noticePage.data.notice.isRead === 1)
  check('公告类通知 target=none', noticePage.data.notice.target === 'none')
  check('公告类通知不重复标记已读',
    requests.filter(r => r === 'PUT /api/notice/read/102').length === 0)
}

async function main() {
  await testMessagePage()
  await testChatPage()
  await testNoticeDetail()

  const failed = results.filter(r => !r[1])
  console.log('\n' + '='.repeat(56))
  console.log('结果：' + (results.length - failed.length) + '/' + results.length + ' 通过')
  if (!failed.length) {
    console.log('全部通过')
  } else {
    console.log('失败用例：')
    failed.forEach(([name, , detail]) => console.log('  - ' + name
      + (detail !== undefined ? '  -> ' + JSON.stringify(detail) : '')))
  }
  console.log('='.repeat(56))
  process.exit(failed.length ? 1 : 0)
}

main()
