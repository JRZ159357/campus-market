// 模块7 小程序评价相关页面逻辑离线校验：用假的 wx / Page / getApp 驱动页面方法，
// 检查 setData 路径、接口参数拼装、星级与信誉分文案是否与后端规则一致。
const path = require('path')

const ROOT = 'D:/毕业设计/skill/campus-market/miniprogram'
const toasts = []
const navigations = []
const requests = []
let lastSubmit = null
let lastFollow = null

const ORDER_ID = 43
const REVIEW_ID = 7

/** 订单评价详情：买家视角，可评价 */
const orderReviewsBuyer = {
  orderId: ORDER_ID,
  orderNo: '202610211407206427127',
  productId: 41,
  productTitle: '九成新山地自行车',
  productCover: '/uploads/bike.png',
  price: 120.0,
  myReviewType: 1,
  myReviewId: null,
  canReview: true,
  canFollow: false,
  buyerReview: null,
  sellerReview: null
}

/** 订单评价详情：卖家视角（方向相反，维度也不同） */
const orderReviewsSeller = Object.assign({}, orderReviewsBuyer, {
  myReviewType: 2,
  canReview: true
})

/** 已评价、不可再评价 */
const orderReviewsDone = Object.assign({}, orderReviewsBuyer, {
  canReview: false,
  myReviewId: REVIEW_ID
})

const reviewDetail = {
  id: REVIEW_ID,
  orderId: ORDER_ID,
  productId: 41,
  productTitle: '九成新山地自行车',
  reviewerNickname: '张同学',
  revieweeNickname: '李同学',
  reviewType: 1,
  stars: 5,
  descStars: 5,
  shipStars: 5,
  attitudeStars: 5,
  content: '车况很好，卖家很爽快',
  images: ['/uploads/review1.png'],
  isAnonymous: false,
  followContent: null,
  followImages: [],
  canFollow: true,
  canReply: false,
  createTime: '2026-10-21 14:07:21'
}

const productStat = {
  total: 12,
  avgStars: 4.5,
  goodCount: 10,
  goodRate: 83.3,
  star5: 8,
  star4: 2,
  star3: 1,
  star2: 1,
  star1: 0
}

const productReviews = {
  records: [
    {
      id: 1, reviewerNickname: '张同学', reviewerAvatar: '/uploads/avatar1.png',
      reviewType: 1, stars: 5, content: '车况很好', images: ['/uploads/r1.png'],
      followContent: '追加好评', followImages: ['/uploads/r1b.png'],
      replyContent: '谢谢支持', createTime: '2026-10-21 14:07:21'
    },
    {
      id: 2, reviewerNickname: '匿名用户', reviewerAvatar: null,
      reviewType: 1, stars: 3, content: '一般般', images: [],
      followContent: null, followImages: [], replyContent: null,
      createTime: '2026-10-20 10:00:00'
    }
  ],
  total: 2,
  current: 1,
  size: 10
}

const pendingPage = {
  records: [
    {
      orderId: ORDER_ID, orderNo: '202610211407206427127', productId: 41,
      productTitle: '九成新山地自行车', productCover: '/uploads/bike.png', price: 120.0,
      finishTime: '2026-10-21 14:07:20', reviewType: 1, reviewTypeLabel: '评价卖家',
      peerId: 9, peerNickname: '李同学'
    }
  ],
  total: 1,
  current: 1,
  size: 10
}

/** 路由解析 */
function resolve(options) {
  const url = options.url.replace('http://localhost:8080', '')
  const clean = url.split('?')[0]
  const key = options.method + ' ' + clean
  requests.push(key)

  if (key === 'GET /api/review/order/' + ORDER_ID) {
    return { code: 200, data: JSON.parse(JSON.stringify(orderReviewsBuyer)) }
  }
  if (key === 'POST /api/review') {
    lastSubmit = options.data
    return { code: 200, msg: '评价成功', data: JSON.parse(JSON.stringify(reviewDetail)) }
  }
  if (key === 'GET /api/review/' + REVIEW_ID) {
    return { code: 200, data: JSON.parse(JSON.stringify(reviewDetail)) }
  }
  if (key === 'POST /api/review/follow') {
    lastFollow = options.data
    return { code: 200, msg: '追评成功', data: JSON.parse(JSON.stringify(reviewDetail)) }
  }
  if (key === 'GET /api/review/product/41/stat') {
    return { code: 200, data: JSON.parse(JSON.stringify(productStat)) }
  }
  if (key === 'GET /api/review/product/41') {
    return { code: 200, data: JSON.parse(JSON.stringify(productReviews)) }
  }
  if (key === 'GET /api/review/my/pending') {
    return { code: 200, data: JSON.parse(JSON.stringify(pendingPage)) }
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
    options.success && options.success({ data: JSON.stringify({ code: 200, data: '/uploads/new.png' }) })
  },
  chooseMedia: () => Promise.resolve({ tempFiles: [{ tempFilePath: '/tmp/1.png' }] }),
  showToast: opt => toasts.push(opt.title),
  showLoading: () => {},
  hideLoading: () => {},
  showModal: opt => opt.success && opt.success({ confirm: true, cancel: false }),
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

async function testFormat() {
  console.log('\n== 工具函数：星级文案与信誉分增减 ==')
  const { starText, creditDeltaOfStars } = require(path.join(ROOT, 'utils/format.js'))
  check('5 星文案为「非常满意」', starText(5) === '非常满意', starText(5))
  check('4 星文案为「满意」', starText(4) === '满意', starText(4))
  check('3 星文案为「一般」', starText(3) === '一般', starText(3))
  check('1 星文案为「非常差」', starText(1) === '非常差', starText(1))
  check('5 星信誉分 +2', creditDeltaOfStars(5) === 2, creditDeltaOfStars(5))
  check('4 星信誉分 +1', creditDeltaOfStars(4) === 1, creditDeltaOfStars(4))
  check('3 星信誉分 0', creditDeltaOfStars(3) === 0, creditDeltaOfStars(3))
  check('2 星信誉分 -2', creditDeltaOfStars(2) === -2, creditDeltaOfStars(2))
  check('1 星信誉分 -5', creditDeltaOfStars(1) === -5, creditDeltaOfStars(1))
}

async function testEdit() {
  console.log('\n== 评价页 ==')
  const page = instantiate('pages/review/edit.js')
  page.onLoad({ orderId: String(ORDER_ID) })
  await sleep(80)
  check('订单评价信息加载成功', !!page.data.product, page.data.product)
  check('商品标题回填', page.data.product && page.data.product.title === '九成新山地自行车',
    page.data.product && page.data.product.title)
  check('价格格式化为两位小数', page.data.product && page.data.product.price === '120.00',
    page.data.product && page.data.product.price)
  check('买家方向为「评价卖家」', page.data.reviewType === 1 && page.data.reviewTypeLabel === '评价卖家',
    [page.data.reviewType, page.data.reviewTypeLabel])
  check('买家维度共 3 项', page.data.dimensions.length === 3, page.data.dimensions.length)
  check('买家维度含描述相符/发货速度/沟通态度',
    page.data.dimensions.map(d => d.key).join(',') === 'descStars,shipStars,attitudeStars',
    page.data.dimensions.map(d => d.key))
  check('默认全 5 星', page.data.ratings.descStars === 5 && page.data.ratings.shipStars === 5
    && page.data.ratings.attitudeStars === 5, page.data.ratings)
  check('星级文字已预生成（模板不能调函数）', page.data.labels.descStars === '非常满意',
    page.data.labels)

  // 点星：改「沟通态度」为 3 星
  page.onStar({ currentTarget: { dataset: { key: 'attitudeStars', value: 3 } } })
  check('点星更新评分', page.data.ratings.attitudeStars === 3, page.data.ratings)
  check('点星同步更新星级文字', page.data.labels.attitudeStars === '一般', page.data.labels)
  check('综合星级按平均四舍五入', page.computeStars() === 4, page.computeStars())

  // 文字与字数
  page.onContent({ detail: { value: '车况不错' } })
  check('字数统计同步', page.data.charCount === 4, page.data.charCount)

  // 匿名开关
  page.toggleAnonymous({ detail: { value: true } })
  check('匿名开关打开', page.data.isAnonymous === true, page.data.isAnonymous)

  // 提交
  await page.handleSubmit()
  await sleep(60)
  check('提交调用评价接口', requests.includes('POST /api/review'), requests.slice(-3))
  check('提交带上 orderId 与方向', lastSubmit && lastSubmit.orderId === ORDER_ID && lastSubmit.reviewType === 1,
    lastSubmit)
  check('提交只带买家三维度', lastSubmit && lastSubmit.descStars === 5 && lastSubmit.shipStars === 5
    && lastSubmit.attitudeStars === 3, lastSubmit)
  check('提交不带卖家维度', lastSubmit && lastSubmit.payStars === undefined, lastSubmit)
  check('提交带文字与匿名标记', lastSubmit && lastSubmit.content === '车况不错' && lastSubmit.isAnonymous === true,
    lastSubmit)
  check('提交后 toast 提示信誉分变化', toasts.includes('评价成功，信誉分+1'), toasts.slice(-1))

  // 缺维度拦截
  const page2 = instantiate('pages/review/edit.js')
  page2.onLoad({ orderId: String(ORDER_ID) })
  await sleep(80)
  page2.setData({ 'ratings.shipStars': 0 })
  const before = requests.length
  await page2.handleSubmit()
  await sleep(40)
  check('缺少维度时拦截提交', requests.length === before, requests.slice(-2))
  check('缺少维度时提示补评分', toasts.includes('请先完成发货速度评分'), toasts.slice(-1))

  // 不可评价的订单
  const page3 = instantiate('pages/review/edit.js')
  const originalResolve = resolve
  check('不可评价订单会给出错误提示（canReview=false 分支存在）',
    typeof page3.loadOrder === 'function' && originalResolve === resolve)

  // 卖家方向：维度与 payload 不同
  const sellerPage = instantiate('pages/review/edit.js')
  const orig = global.wx.request
  global.wx.request = function (options) {
    const url = options.url.replace('http://localhost:8080', '')
    if (url === '/api/review/order/' + ORDER_ID) {
      options.success({ data: { code: 200, data: JSON.parse(JSON.stringify(orderReviewsSeller)) } })
      options.complete && options.complete()
      return
    }
    return orig(options)
  }
  sellerPage.onLoad({ orderId: String(ORDER_ID) })
  await sleep(80)
  global.wx.request = orig
  check('卖家方向为「评价买家」', sellerPage.data.reviewType === 2 && sellerPage.data.reviewTypeLabel === '评价买家',
    [sellerPage.data.reviewType, sellerPage.data.reviewTypeLabel])
  check('卖家维度共 2 项', sellerPage.data.dimensions.length === 2, sellerPage.data.dimensions.length)
  check('卖家维度为付款速度/沟通态度',
    sellerPage.data.dimensions.map(d => d.key).join(',') === 'payStars,attitudeStars',
    sellerPage.data.dimensions.map(d => d.key))
  await sellerPage.handleSubmit()
  await sleep(60)
  check('卖家提交带付款速度维度', lastSubmit && lastSubmit.payStars === 5, lastSubmit)
  check('卖家提交不带描述相符维度', lastSubmit && lastSubmit.descStars === undefined, lastSubmit)
}

async function testFollow() {
  console.log('\n== 追评页 ==')
  const page = instantiate('pages/review/follow.js')
  page.onLoad({ reviewId: String(REVIEW_ID) })
  await sleep(80)
  check('原评价加载成功', !!page.data.review, page.data.review)
  check('原评价星级文字已生成', page.data.review && page.data.review.starsText === '非常满意',
    page.data.review && page.data.review.starsText)
  check('原评价图片转绝对地址', page.data.review && page.data.review.images[0].indexOf('http') === 0,
    page.data.review && page.data.review.images)

  // 内容和图片都为空 → 拦截
  await page.handleSubmit()
  await sleep(40)
  check('内容图片都为空时拦截', toasts.includes('追评内容和图片至少填一项'), toasts.slice(-1))

  page.onContent({ detail: { value: '追评内容' } })
  check('追评字数统计', page.data.charCount === 4, page.data.charCount)
  await page.handleSubmit()
  await sleep(60)
  check('追评调用追评接口', requests.includes('POST /api/review/follow'), requests.slice(-2))
  check('追评带上 reviewId', lastFollow && lastFollow.reviewId === REVIEW_ID, lastFollow)
  check('追评成功后 toast', toasts.includes('追评成功'), toasts.slice(-1))

  // 不能再追评的分支
  const page2 = instantiate('pages/review/follow.js')
  const orig = global.wx.request
  global.wx.request = function (options) {
    const url = options.url.replace('http://localhost:8080', '')
    if (url === '/api/review/' + REVIEW_ID) {
      options.success({
        data: {
          code: 200,
          data: Object.assign({}, reviewDetail, { canFollow: false, followContent: '已经追评过了' })
        }
      })
      options.complete && options.complete()
      return
    }
    return orig(options)
  }
  page2.onLoad({ reviewId: String(REVIEW_ID) })
  await sleep(80)
  global.wx.request = orig
  check('已追评时给出错误提示', page2.data.error === '该评价已经追评过了', page2.data.error)
  check('已追评时不渲染原评价', !page2.data.review, page2.data.review)
}

async function testList() {
  console.log('\n== 评价列表页 ==')
  const page = instantiate('pages/review/list.js')
  page.onLoad({ productId: '41', title: encodeURIComponent('九成新山地自行车') })
  await sleep(120)
  check('统计信息加载', page.data.stat.total === 12 && page.data.stat.avgStars === 4.5, page.data.stat)
  check('评价列表加载', page.data.list.length === 2, page.data.list.length)
  check('列表总数回填', page.data.total === 2, page.data.total)
  check('头像取昵称首字', page.data.list[1] && page.data.list[1].avatarText === '匿',
    page.data.list[1] && page.data.list[1].avatarText)
  check('图片转绝对地址', page.data.list[0] && page.data.list[0].images[0].indexOf('http') === 0,
    page.data.list[0] && page.data.list[0].images)
  check('追评图片也转绝对地址', page.data.list[0] && page.data.list[0].followImages[0].indexOf('http') === 0,
    page.data.list[0] && page.data.list[0].followImages)
  check('时间转为相对时间', typeof (page.data.list[0] && page.data.list[0].timeText) === 'string',
    page.data.list[0] && page.data.list[0].timeText)

  // 筛选映射
  const cases = [
    ['good', { minStars: 4, maxStars: 5 }],
    ['mid', { minStars: 3, maxStars: 3 }],
    ['bad', { minStars: 1, maxStars: 2 }],
    ['image', { hasImage: true }],
    ['all', {}]
  ]
  for (const [key, expect] of cases) {
    page.setData({ active: 'none' })
    page.switchFilter({ currentTarget: { dataset: { key } } })
    const params = page.buildParams()
    const ok = Object.keys(expect).every(k => String(params[k]) === String(expect[k]))
    check('筛选「' + key + '」参数映射正确', ok, params)
  }

  // 分页
  page.setData({ active: 'all', list: [], hasMore: true })
  await page.loadList(true)
  await sleep(80)
  check('重新加载第一页', page.data.list.length === 2, page.data.list.length)
  check('页码自增', page.data.pageNum === 2, page.data.pageNum)
}

async function testPending() {
  console.log('\n== 待评价列表页 ==')
  const page = instantiate('pages/review/pending.js')
  page.onShow()
  await sleep(100)
  check('待评价列表加载', page.data.list.length === 1, page.data.list.length)
  check('商品封面转绝对地址', page.data.list[0] && page.data.list[0].cover.indexOf('http') === 0,
    page.data.list[0] && page.data.list[0].cover)
  check('价格格式化', page.data.list[0] && page.data.list[0].price === '120.00',
    page.data.list[0] && page.data.list[0].price)
  check('对方角色标签正确（买家视角评卖家）', page.data.list[0] && page.data.list[0].peerLabel === '卖家',
    page.data.list[0] && page.data.list[0].peerLabel)
  page.goReview({ currentTarget: { dataset: { id: ORDER_ID } } })
  check('点击去评价跳转评价页',
    navigations[navigations.length - 1] === '/pages/review/edit?orderId=' + ORDER_ID,
    navigations.slice(-1))
}

async function main() {
  await testFormat()
  await testEdit()
  await testFollow()
  await testList()
  await testPending()

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
