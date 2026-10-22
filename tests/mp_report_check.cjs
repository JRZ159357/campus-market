// 模块9 小程序举报相关页面逻辑离线校验：用假的 wx / Page / getApp 驱动页面方法，
// 检查 setData 路径、接口参数拼装、前端校验规则与后端 ReportRule 是否一致，
// 以及 4 处举报入口（商品详情 / 卖家主页 / 评价长按 / 聊天更多）跳转是否正确。
const path = require('path')

const ROOT = 'D:/毕业设计/skill/campus-market/miniprogram'
const toasts = []
const navigations = []
const requests = []
const actionSheets = []
let lastSubmit = null
let uploadCount = 0

const PRODUCT_ID = 41
const USER_ID = 9
const REVIEW_ID = 7
const REPORT_ID = 31

/** 商品详情：被举报对象 */
const productDetail = {
  id: PRODUCT_ID,
  title: '九成新山地自行车',
  cover: '/uploads/bike.png',
  price: 120.0,
  status: 1,
  statusLabel: '在售',
  isOwner: false,
  sellerId: 9
}

/** 卖家主页 */
const userHome = {
  id: USER_ID,
  nickname: '李同学',
  avatar: '/uploads/avatar1.png',
  creditScore: 95,
  creditLevelLabel: '优秀',
  isSelf: false
}

/** 我的举报列表 */
const myReportPage = {
  records: [
    {
      id: REPORT_ID, reportType: 1, targetId: PRODUCT_ID,
      targetTitle: '九成新山地自行车', targetCover: '/uploads/bike.png',
      reason: 1, content: '车架有裂纹，卖家说是正品',
      status: 2, statusLabel: '已处理', handleAction: 1,
      handleResult: '经核实商品与描述不符，已强制下架',
      createTime: '2026-10-21 16:20:31', acceptTime: '2026-10-21 16:30:00',
      handleTime: '2026-10-21 17:00:00'
    },
    {
      id: REPORT_ID + 1, reportType: 2, targetId: USER_ID,
      targetTitle: '李同学', targetCover: '/uploads/avatar1.png',
      reason: 4, content: '对方在聊天中辱骂我',
      status: 0, statusLabel: '待处理', handleAction: 0, handleResult: null,
      createTime: '2026-10-21 18:00:00'
    }
  ],
  total: 2,
  current: 1,
  size: 10
}

/** 单条举报详情 */
const reportDetail = {
  id: REPORT_ID, reportType: 1, targetId: PRODUCT_ID,
  targetTitle: '九成新山地自行车', targetCover: '/uploads/bike.png',
  reason: 1, content: '车架有裂纹，卖家说是正品',
  images: ['/uploads/proof1.png'],
  status: 2, statusLabel: '已处理', handleAction: 1,
  handleResult: '经核实商品与描述不符，已强制下架',
  createTime: '2026-10-21 16:20:31', acceptTime: '2026-10-21 16:30:00',
  handleTime: '2026-10-21 17:00:00'
}

/** 路由解析 */
function resolve(options) {
  const url = options.url.replace('http://localhost:8080', '')
  const clean = url.split('?')[0]
  const key = options.method + ' ' + clean
  requests.push(key)

  if (key === 'GET /api/product/' + PRODUCT_ID) {
    return { code: 200, data: JSON.parse(JSON.stringify(productDetail)) }
  }
  if (key === 'GET /api/user/home/' + USER_ID) {
    return { code: 200, data: JSON.parse(JSON.stringify(userHome)) }
  }
  if (key === 'GET /api/report/my') {
    return { code: 200, data: JSON.parse(JSON.stringify(myReportPage)) }
  }
  if (key === 'GET /api/report/my/' + REPORT_ID) {
    return { code: 200, data: JSON.parse(JSON.stringify(reportDetail)) }
  }
  if (key === 'POST /api/report') {
    lastSubmit = options.data
    return { code: 200, msg: '举报已提交', data: REPORT_ID }
  }
  return { code: 500, msg: '未预置响应: ' + key }
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
  uploadFile(options) {
    uploadCount++
    options.success && options.success({ data: JSON.stringify({ code: 200, data: '/uploads/proof' + uploadCount + '.png' }) })
  },
  chooseMedia: () => Promise.resolve({ tempFiles: [{ tempFilePath: '/tmp/1.png' }] }),
  showToast: opt => toasts.push(opt.title),
  showLoading: () => {},
  hideLoading: () => {},
  showModal: opt => opt.success && opt.success({ confirm: true, cancel: false }),
  showActionSheet: opt => {
    actionSheets.push(opt.itemList)
    // 默认选中第一项；调用方可在 triggerSheet 覆盖
    if (global.__sheetHandler) {
      const handler = global.__sheetHandler
      global.__sheetHandler = null
      handler(opt)
    } else if (opt.success) {
      opt.success({ tapIndex: 0 })
    }
  },
  navigateTo: opt => navigations.push(opt.url),
  redirectTo: opt => navigations.push(opt.url),
  switchTab: opt => navigations.push(opt.url),
  navigateBack: () => navigations.push('back'),
  setNavigationBarTitle: opt => navigations.push('title:' + opt.title),
  previewImage: () => {},
  stopPullDownRefresh: () => {}
}

const appStub = {
  globalData: { token: 'fake-token', userInfo: { id: 5 } },
  isLogin: () => true,
  clearLogin: () => {}
}
global.getApp = () => appStub

let captured = null
global.Page = obj => { captured = obj }

function setByPath(target, p, value) {
  const tokens = p.replace(/\[(\d+)\]/g, '.$1').split('.')
  let node = target
  for (let i = 0; i < tokens.length - 1; i++) {
    if (node[tokens[i]] === undefined) node[tokens[i]] = {}
    node = node[tokens[i]]
  }
  node[tokens[tokens.length - 1]] = value
}

function instantiate(file) {
  captured = null
  delete require.cache[require.resolve(path.join(ROOT, file))]
  require(path.join(ROOT, file))
  const page = Object.assign({}, captured)
  page.data = JSON.parse(JSON.stringify(captured.data))
  page.setData = function (patch) {
    Object.keys(patch).forEach(k => setByPath(this.data, k, patch[k]))
  }
  return page
}

const sleep = ms => new Promise(r => setTimeout(r, ms))
const results = []
function check(name, ok, detail) {
  results.push([name, ok, detail])
  console.log((ok ? '  [PASS] ' : '  [FAIL] ') + name + (detail !== undefined ? '  -> ' + JSON.stringify(detail) : ''))
}

async function testApiConstants() {
  console.log('\n== API 常量：与后端枚举对齐 ==')
  const api = require(path.join(ROOT, 'api/report.js'))

  check('举报原因共 6 项', api.REPORT_REASONS.length === 6, api.REPORT_REASONS.length)
  check('原因取值 1~6 连续',
    api.REPORT_REASONS.map(r => r.value).join(',') === '1,2,3,4,5,6',
    api.REPORT_REASONS.map(r => r.value))
  check('每项原因都带说明文案',
    api.REPORT_REASONS.every(r => r.label && r.desc), api.REPORT_REASONS.map(r => r.label))

  check('处理动作 5 项（0~4）',
    Object.keys(api.HANDLE_ACTIONS).sort().join(',') === '0,1,2,3,4',
    Object.keys(api.HANDLE_ACTIONS))
  check('动作 1 为强制下架商品', api.HANDLE_ACTIONS[1] === '强制下架商品', api.HANDLE_ACTIONS[1])
  check('动作 2 为封禁用户', api.HANDLE_ACTIONS[2] === '封禁用户', api.HANDLE_ACTIONS[2])
  check('动作 3 为删除违规评价', api.HANDLE_ACTIONS[3] === '删除违规评价', api.HANDLE_ACTIONS[3])

  check('状态 4 项（0~3）',
    Object.keys(api.STATUS_META).sort().join(',') === '0,1,2,3',
    Object.keys(api.STATUS_META))
  check('待处理为红', api.STATUS_META[0].color === '#F56C6C', api.STATUS_META[0])
  check('处理中为橙', api.STATUS_META[1].color === '#E6A23C', api.STATUS_META[1])
  check('已处理为绿', api.STATUS_META[2].color === '#67C23A', api.STATUS_META[2])
  check('已驳回为灰', api.STATUS_META[3].color === '#909399', api.STATUS_META[3])

  check('原因文案映射正确', api.reasonLabel(1) === '虚假描述', api.reasonLabel(1))
  check('未知原因兜底文案', api.reasonLabel(99) === '未选择原因', api.reasonLabel(99))

  check('类型元信息：商品为蓝', api.typeMeta(1).color === '#409EFF', api.typeMeta(1))
  check('类型元信息：用户为橙', api.typeMeta(2).color === '#FF6B35', api.typeMeta(2))
  check('类型元信息：评价为紫', api.typeMeta(3).color === '#7C4DFF', api.typeMeta(3))
  check('未知类型不报错', api.typeMeta(99).typeLabel === '未知类型', api.typeMeta(99))
}

async function testReportPage() {
  console.log('\n== 举报提交页 ==')
  const page = instantiate('pages/report/index.js')
  page.onLoad({ type: '1', targetId: String(PRODUCT_ID) })
  await sleep(80)
  check('被举报对象加载成功', !!page.data.target, page.data.target)
  check('对象标题回填', page.data.target && page.data.target.title === '九成新山地自行车',
    page.data.target && page.data.target.title)
  check('对象封面转绝对地址',
    page.data.target && page.data.target.cover.indexOf('http') === 0,
    page.data.target && page.data.target.cover)
  check('对象副信息含价格与状态',
    page.data.target && page.data.target.extra.indexOf('120.00') >= 0,
    page.data.target && page.data.target.extra)
  check('类型元信息为商品举报', page.data.typeMeta.typeLabel === '商品举报', page.data.typeMeta)

  // 未选原因 → 拦截
  await page.handleSubmit()
  await sleep(40)
  check('未选原因时拦截提交', page.data.errors.reason === '请选择举报原因', page.data.errors.reason)

  // 描述过短 → 拦截
  page.onReasonTap({ currentTarget: { dataset: { index: 0 } } })
  check('选择原因后清空原因错误', page.data.errors.reason === '', page.data.errors.reason)
  check('原因选中项文案为虚假描述', page.data.reasonSelected && page.data.reasonSelected.label === '虚假描述',
    page.data.reasonSelected)

  page.onContentInput({ detail: { value: '短短' } })
  check('字数统计同步', page.data.charCount === 2, page.data.charCount)
  await page.handleSubmit()
  await sleep(40)
  check('描述不足5字时拦截提交',
    (page.data.errors.content || '').indexOf('至少 5 字') >= 0, page.data.errors.content)

  // 正常填写 → 提交
  page.onContentInput({ detail: { value: '车架有明显裂纹，卖家却说是正品' } })
  check('字数统计同步', page.data.charCount === 15, page.data.charCount)
  const before = requests.length
  await page.handleSubmit()
  await sleep(60)
  check('提交调用举报接口', requests.includes('POST /api/report'), requests.slice(before))
  check('提交带上 reportType', lastSubmit && lastSubmit.reportType === 1, lastSubmit)
  check('提交带上 targetId', lastSubmit && lastSubmit.targetId === PRODUCT_ID, lastSubmit)
  check('提交带原因取值（不是文案）', lastSubmit && lastSubmit.reason === 1, lastSubmit)
  check('提交带上描述', lastSubmit && lastSubmit.content === '车架有明显裂纹，卖家却说是正品', lastSubmit)
  check('提交带上 images 数组', lastSubmit && Array.isArray(lastSubmit.images), lastSubmit)
  check('提交成功 toast 提示', toasts.includes('举报已提交'), toasts.slice(-1))

  // 上传证据图
  const page2 = instantiate('pages/report/index.js')
  page2.onLoad({ type: '1', targetId: String(PRODUCT_ID) })
  await sleep(60)
  await page2.chooseImage()
  await sleep(40)
  check('证据图上传成功并入列', page2.data.images.length === 1, page2.data.images)
  check('证据图为服务端相对地址', page2.data.images[0] && page2.data.images[0].indexOf('/uploads/') === 0,
    page2.data.images[0])

  // 满 3 张后再点 → 提示而不是继续加
  page2.setData({ images: ['/uploads/a.png', '/uploads/b.png', '/uploads/c.png'] })
  await page2.chooseImage()
  await sleep(40)
  check('证据图最多 3 张，超出被拒', page2.data.images.length === 3, page2.data.images.length)
  check('超量时给出提示', toasts.includes('最多上传 3 张图片'), toasts.slice(-1))

  // 删除一张
  page2.removeImage({ currentTarget: { dataset: { index: 1 } } })
  check('可删除已选证据图', page2.data.images.length === 2, page2.data.images)

  // 用户举报类型
  const page3 = instantiate('pages/report/index.js')
  page3.onLoad({ type: '2', targetId: String(USER_ID) })
  await sleep(80)
  check('用户举报对象加载成功', !!page3.data.target, page3.data.target)
  check('用户对象展示昵称', page3.data.target && page3.data.target.title === '李同学',
    page3.data.target && page3.data.target.title)
  check('用户对象副信息含信誉分',
    page3.data.target && page3.data.target.extra.indexOf('信誉分 95') >= 0,
    page3.data.target && page3.data.target.extra)
  check('类型元信息为用户举报', page3.data.typeMeta.typeLabel === '用户举报', page3.data.typeMeta)

  // 评价举报类型
  const page4 = instantiate('pages/report/index.js')
  page4.onLoad({ type: '3', targetId: String(REVIEW_ID) })
  await sleep(60)
  check('评价举报对象加载成功', !!page4.data.target, page4.data.target)
  check('评价对象展示为「评价 #id」', page4.data.target && page4.data.target.title === '评价 #' + REVIEW_ID,
    page4.data.target && page4.data.target.title)
  check('类型元信息为评价举报', page4.data.typeMeta.typeLabel === '评价举报', page4.data.typeMeta)

  // 缺 targetId → 明确空态而不是报错
  const page5 = instantiate('pages/report/index.js')
  page5.onLoad({ type: '1' })
  await sleep(40)
  check('缺 targetId 时进入空态', page5.data.notFound === true, page5.data)

  // 未登录 → 跳登录页
  const appOrig = global.getApp
  global.getApp = () => ({ isLogin: () => false, clearLogin: () => {} })
  const page6 = instantiate('pages/report/index.js')
  page6.onLoad({ type: '1', targetId: String(PRODUCT_ID) })
  await sleep(40)
  check('未登录时跳转登录页',
    navigations[navigations.length - 1] === '/pages/login/index', navigations.slice(-1))
  global.getApp = appOrig
}

async function testMyReportPage() {
  console.log('\n== 我的举报页 ==')
  const page = instantiate('pages/report/my.js')
  page.onLoad({})
  await sleep(120)
  check('我的举报列表加载', page.data.list.length === 2, page.data.list.length)
  check('列表类型标签为商品举报', page.data.list[0] && page.data.list[0].typeLabel === '商品举报',
    page.data.list[0] && page.data.list[0].typeLabel)
  check('列表状态标签为已处理', page.data.list[0] && page.data.list[0].statusLabel === '已处理',
    page.data.list[0] && page.data.list[0].statusLabel)
  check('列表原因文案正确', page.data.list[0] && page.data.list[0].reasonText === '虚假描述',
    page.data.list[0] && page.data.list[0].reasonText)
  check('列表展示处理结论摘要',
    page.data.list[0] && page.data.list[0].resultText.indexOf('强制下架') >= 0,
    page.data.list[0] && page.data.list[0].resultText)
  check('已处理标记为终态', page.data.list[0] && page.data.list[0].finished === true,
    page.data.list[0] && page.data.list[0].finished)
  check('待处理项未标记终态', page.data.list[1] && page.data.list[1].finished === false,
    page.data.list[1] && page.data.list[1].finished)
  check('待处理项无处理结论', page.data.list[1] && page.data.list[1].resultText === '',
    page.data.list[1] && page.data.list[1].resultText)
  check('时间已预生成（模板不能调函数）',
    page.data.list[0] && page.data.list[0].timeText === '10-21 16:20',
    page.data.list[0] && page.data.list[0].timeText)
  check('对象封面转绝对地址',
    page.data.list[0] && page.data.list[0].targetCover.indexOf('http') === 0,
    page.data.list[0] && page.data.list[0].targetCover)

  // 详情
  await page.openDetail({ currentTarget: { dataset: { id: REPORT_ID } } })
  await sleep(80)
  check('详情弹层打开', page.data.detailVisible === true, page.data.detailVisible)
  check('详情展示对象标题', page.data.detail && page.data.detail.targetTitle === '九成新山地自行车',
    page.data.detail && page.data.detail.targetTitle)
  check('详情展示处理动作文案', page.data.detail && page.data.detail.actionText === '强制下架商品',
    page.data.detail && page.data.detail.actionText)
  check('详情展示处理结果', page.data.detail && page.data.detail.resultText.indexOf('强制下架') >= 0,
    page.data.detail && page.data.detail.resultText)
  check('详情证据图转绝对地址',
    page.data.detail && page.data.detail.images[0].indexOf('http') === 0,
    page.data.detail && page.data.detail.images)
  check('详情含接单与处理时间',
    page.data.detail && !!page.data.detail.acceptTime && !!page.data.detail.handleTime,
    page.data.detail && [page.data.detail.acceptTime, page.data.detail.handleTime])
  page.closeDetail()
  check('详情弹层可关闭', page.data.detailVisible === false, page.data.detailVisible)
}

async function testEntries() {
  console.log('\n== 举报入口 ==')
  // 商品详情「更多」→ 举报商品
  const detail = instantiate('pages/product/detail.js')
  detail.setData({ detail: Object.assign({}, productDetail) })
  actionSheets.length = 0
  detail.showMoreActions()
  check('商品详情弹出「更多」菜单', actionSheets.length === 1, actionSheets.length)
  check('菜单含举报商品项',
    actionSheets[0] && actionSheets[0][0].indexOf('举报商品') >= 0, actionSheets[0])
  check('选择举报后跳转举报页',
    navigations[navigations.length - 1] === '/pages/report/index?type=1&targetId=' + PRODUCT_ID,
    navigations.slice(-1))

  // 本人商品 → 不给举报入口
  const ownDetail = instantiate('pages/product/detail.js')
  ownDetail.setData({ detail: Object.assign({}, productDetail, { isOwner: true }) })
  actionSheets.length = 0
  ownDetail.showMoreActions()
  check('本人商品不给举报入口',
    actionSheets[0] && actionSheets[0][0] === '不感兴趣', actionSheets[0])

  // 评价长按 → 举报评价
  const reviewPage = instantiate('pages/review/list.js')
  reviewPage.setData({
    list: [{ id: REVIEW_ID, reviewerId: 12, isAnonymous: false, content: '恶意差评' }]
  })
  actionSheets.length = 0
  reviewPage.onReviewLongPress({ currentTarget: { dataset: { index: 0 } } })
  check('评价长按弹出举报菜单', actionSheets.length === 1, actionSheets.length)
  check('菜单含举报评价项',
    actionSheets[0] && actionSheets[0][0].indexOf('举报该评价') >= 0, actionSheets[0])
  check('举报评价跳 type=3',
    navigations[navigations.length - 1] === '/pages/report/index?type=3&targetId=' + REVIEW_ID,
    navigations.slice(-1))

  // 匿名评价 → 不给举报入口
  const anonPage = instantiate('pages/review/list.js')
  anonPage.setData({
    list: [{ id: REVIEW_ID, reviewerId: null, isAnonymous: true, content: '匿名差评' }]
  })
  actionSheets.length = 0
  anonPage.onReviewLongPress({ currentTarget: { dataset: { index: 0 } } })
  check('匿名评价不提供举报入口',
    actionSheets[0] && actionSheets[0][0].indexOf('无法举报') >= 0, actionSheets[0])

  // 卖家主页「更多」→ 举报用户
  const home = instantiate('pages/user/home.js')
  home.setData({ home: Object.assign({}, userHome), userId: USER_ID })
  actionSheets.length = 0
  home.showMoreActions()
  check('卖家主页弹出「更多」菜单', actionSheets.length === 1, actionSheets.length)
  check('菜单含举报用户与我的举报两项',
    actionSheets[0] && actionSheets[0].length === 2
      && actionSheets[0][0].indexOf('举报该用户') >= 0
      && actionSheets[0][1] === '我的举报记录', actionSheets[0])
  check('举报用户跳 type=2',
    navigations[navigations.length - 1] === '/pages/report/index?type=2&targetId=' + USER_ID,
    navigations.slice(-1))

  // 自己的主页 → 直接进「我的举报」
  const selfHome = instantiate('pages/user/home.js')
  selfHome.setData({ home: Object.assign({}, userHome, { isSelf: true }), userId: USER_ID })
  actionSheets.length = 0
  selfHome.showMoreActions()
  check('自己主页不进举报菜单', actionSheets.length === 0, actionSheets.length)
  check('自己主页直接跳我的举报',
    navigations[navigations.length - 1] === '/pages/report/my', navigations.slice(-1))

  // 聊天「更多」→ 举报对方
  const chat = instantiate('pages/chat/index.js')
  chat.setData({ session: { id: 3, peerId: USER_ID, peerIsSelf: false, productId: 41 } })
  actionSheets.length = 0
  chat.onMore()
  check('聊天菜单含举报对方用户',
    actionSheets[0] && actionSheets[0].indexOf('举报对方用户') >= 0, actionSheets[0])
  check('举报对方跳 type=2',
    navigations[navigations.length - 1] === '/pages/report/index?type=2&targetId=' + USER_ID,
    navigations.slice(-1))

  // 聊天里跟自己 → 不给举报入口
  const chatSelf = instantiate('pages/chat/index.js')
  chatSelf.setData({ session: { id: 4, peerId: 5, peerIsSelf: true } })
  actionSheets.length = 0
  chatSelf.onMore()
  check('与自己会话不给举报入口',
    actionSheets[0] && actionSheets[0].indexOf('举报对方用户') < 0, actionSheets[0])

  // 「我的」页宫格入口
  const profile = instantiate('pages/profile/index.js')
  const hasReport = profile.data.menuItems && profile.data.menuItems.some(m => m.key === 'report')
  check('我的页宫格含「我的举报」入口', hasReport, profile.data.menuItems && profile.data.menuItems.map(m => m.key))
}

async function main() {
  await testApiConstants()
  await testReportPage()
  await testMyReportPage()
  await testEntries()

  const failed = results.filter(r => !r[1])
  console.log('\n' + '='.repeat(56))
  console.log('结果：%d/%d 通过', results.length - failed.length, results.length)
  if (failed.length) {
    console.log('失败项：')
    failed.forEach(r => console.log('  - ' + r[0]))
    process.exit(1)
  }
  console.log('全部通过')
}

main().catch(e => {
  console.error('ERR', e)
  process.exit(1)
})
