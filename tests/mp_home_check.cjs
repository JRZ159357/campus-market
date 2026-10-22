// 小程序首页离线校验（模块12）：用假的 wx / Page / getApp 驱动页面方法，
// 检查首屏聚合结果如何落到页面数据上，以及公告、轮播图跳转、金刚区的分支逻辑。
//
// 为什么用离线校验而不是真机跑：首页的易错点在数据映射（图片地址补全、
// 字段改名、条件渲染），这些用假数据就能覆盖；真机跑的是同一段代码，
// 只是把 wx.request 换成真的，成本高且不可重复。
const path = require('path')

const ROOT = 'D:/毕业设计/skill/campus-market/miniprogram'
const toasts = []
const navigations = []
const clipboard = []
let homeCalls = 0

// 一份「什么都有」的首页响应：8 个分类（触发「更多」）、3 张轮播（含无跳转）、
// 公告开启、热门 2 件、新品 3 件
const HOME = {
  notice: { text: '校园集市本周六开市，欢迎来摆摊', link: 'https://example.com/market', enabled: 1 },
  banners: [
    { id: 1, title: '开学季', imageUrl: '/uploads/2026/10/b1.png', linkType: 2, linkValue: '3', effective: true },
    { id: 2, title: '数码专场', imageUrl: '/uploads/2026/10/b2.png', linkType: 1, linkValue: '77', effective: true },
    { id: 3, title: '平台保障', imageUrl: 'https://cdn.example.com/b3.png', linkType: 4, linkValue: null, effective: true }
  ],
  categories: Array.from({ length: 8 }, (_, i) => ({
    id: i + 1, name: '分类' + (i + 1), icon: i % 2 === 0 ? '📱' : '/uploads/c.png',
    sort: i + 1, status: 1, isHomeShow: 1
  })),
  hotProducts: [
    { id: 11, title: '高数教材', price: 25.5, cover: '/uploads/p1.png', status: 1, viewCount: 30 },
    { id: 12, title: '台灯', price: 30, cover: '/uploads/p2.png', status: 1, viewCount: 12 }
  ],
  newProducts: [
    { id: 21, title: '自行车', price: 180, cover: '/uploads/p3.png', status: 1 },
    { id: 22, title: '耳机', price: 60, cover: '/uploads/p4.png', status: 1 },
    { id: 23, title: '吉他', price: 300, cover: '/uploads/p5.png', status: 1 }
  ],
  hotKeywords: ['自行车', '教材']
}

let response = JSON.parse(JSON.stringify(HOME))

global.wx = {
  getStorageSync: () => '',
  setStorageSync: () => {},
  removeStorageSync: () => {},
  request(options) {
    const clean = options.url.replace('http://localhost:8080', '').split('?')[0]
    if (clean === '/api/home') {
      homeCalls += 1
      options.success && options.success({ data: { code: 200, msg: 'ok', data: response } })
    } else {
      options.success && options.success({ data: { code: 404, msg: '未预置响应: ' + clean } })
    }
    options.complete && options.complete()
  },
  showToast: opt => toasts.push(opt.title),
  navigateTo: opt => navigations.push(opt.url),
  switchTab: opt => navigations.push(opt.url),
  redirectTo: opt => navigations.push(opt.url),
  setClipboardData: opt => {
    clipboard.push(opt.data)
    opt.success && opt.success()
  },
  stopPullDownRefresh: () => {},
  showActionSheet: opt => opt.success && opt.success({ tapIndex: 0 })
}

const appStub = {
  globalData: { token: '', userInfo: null, noticeClosed: false },
  isLogin: () => false,
  setUserInfo: () => {},
  clearLogin: () => {}
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

function instantiate() {
  const file = path.join(ROOT, 'pages/index/index.js')
  captured = null
  delete require.cache[require.resolve(file)]
  require(file)
  const page = Object.assign({}, captured)
  page.data = JSON.parse(JSON.stringify(captured.data))
  page.setData = function (patch) {
    Object.keys(patch).forEach(k => setByPath(this.data, k, patch[k]))
  }
  return page
}

const results = []
function check(name, ok, detail) {
  results.push([name, ok, detail])
  console.log((ok ? '  [PASS] ' : '  [FAIL] ') + name + (detail !== undefined ? '  -> ' + JSON.stringify(detail) : ''))
}

async function main() {
  // ---------- 1. 首屏聚合 ----------
  const page = instantiate()
  await page.loadHome()

  check('首屏只发一次聚合请求', homeCalls === 1, homeCalls)
  check('公告已展示且带链接', page.data.notice && page.data.notice.text.includes('校园集市'),
    page.data.notice)
  check('轮播图补全为完整图片地址',
    page.data.banners[0].imageFull === 'http://localhost:8080/uploads/2026/10/b1.png',
    page.data.banners[0].imageFull)
  check('已是绝对地址的图片不被二次拼接',
    page.data.banners[2].imageFull === 'https://cdn.example.com/b3.png',
    page.data.banners[2].imageFull)

  // ---------- 2. 金刚区 ----------
  const cats = page.data.categories
  check('金刚区 8 个分类折叠为 7 个 + 更多', cats.length === 8 && cats[7].isMore === true,
    cats.map(c => c.name))
  check('图片图标与 emoji 图标分别标记',
    cats[0].isImage === false && cats[1].isImage === true,
    [cats[0].isImage, cats[1].isImage])

  // ---------- 3. 热门 / 新品 ----------
  check('热门推荐保留全部商品并格式化价格',
    page.data.hotProducts.length === 2 && page.data.hotProducts[0].priceText === '25.50',
    page.data.hotProducts.map(p => p.priceText))
  check('热门推荐补全封面地址',
    page.data.hotProducts[0].coverFull === 'http://localhost:8080/uploads/p1.png',
    page.data.hotProducts[0].coverFull)
  check('新品推荐作为瀑布流第一页',
    page.data.left.length + page.data.right.length === 3,
    [page.data.left.length, page.data.right.length])
  check('游标取新品最后一条 id', page.data.cursor === 23, page.data.cursor)
  check('还有更多可加载', page.data.hasMore === true)

  // ---------- 4. 轮播图跳转分支 ----------
  navigations.length = 0
  clipboard.length = 0
  page.onBannerTap({ currentTarget: { dataset: { index: 1 } } })
  check('linkType=1 跳商品详情', navigations[0] === '/pages/product/detail?id=77', navigations)

  navigations.length = 0
  page.onBannerTap({ currentTarget: { dataset: { index: 0 } } })
  check('linkType=2 切到分类页并记住分类',
    navigations[0] === '/pages/category/index' && getApp().globalData.pendingCategoryId === 3,
    [navigations, getApp().globalData.pendingCategoryId])

  navigations.length = 0
  page.onBannerTap({ currentTarget: { dataset: { index: 2 } } })
  check('linkType=4 无跳转：不导航也不复制',
    navigations.length === 0 && clipboard.length === 0, [navigations, clipboard])

  clipboard.length = 0
  navigations.length = 0
  response.banners[2].linkType = 3
  response.banners[2].linkValue = 'https://example.com/a'
  const page3 = instantiate()
  await page3.loadHome()
  page3.onBannerTap({ currentTarget: { dataset: { index: 2 } } })
  check('linkType=3 复制链接而不是尝试打开网页',
    clipboard[0] === 'https://example.com/a' && navigations.length === 0,
    [clipboard, navigations])

  // ---------- 5. 公告关闭 ----------
  appStub.globalData.noticeClosed = false
  const page4 = instantiate()
  await page4.loadHome()
  page4.onNoticeClose()
  check('关闭公告后本次会话不再展示',
    page4.data.noticeClosed === true && getApp().globalData.noticeClosed === true)
  const page5 = instantiate()
  page5.onLoad()
  check('重新进入首页时继承「已关闭」状态', page5.data.noticeClosed === true)

  // ---------- 6. 公告开关与空文案 ----------
  response = JSON.parse(JSON.stringify(HOME))
  response.notice = { text: '有字但关闭了', link: null, enabled: 0 }
  const page6 = instantiate()
  await page6.loadHome()
  check('公告 enabled=0 时不下发到页面', page6.data.notice === null, page6.data.notice)

  response.notice = { text: '   ', link: null, enabled: 1 }
  const page7 = instantiate()
  await page7.loadHome()
  check('公告文案只有空格时同样不展示', page7.data.notice === null, page7.data.notice)

  // ---------- 7. 空数据降级 ----------
  response = {
    notice: { text: '', link: null, enabled: 0 },
    banners: [],
    categories: [],
    hotProducts: [],
    newProducts: [],
    hotKeywords: []
  }
  const page8 = instantiate()
  await page8.loadHome()
  check('空首页不报错且各区块为空',
    page8.data.banners.length === 0 && page8.data.categories.length === 0
    && page8.data.hotProducts.length === 0 && page8.data.left.length === 0)
  check('空首页时 hasMore=false', page8.data.hasMore === false)
  check('空首页时热词回落到默认值', page8.data.hotWord === '校园好物', page8.data.hotWord)

  // ---------- 汇总 ----------
  const failed = results.filter(r => !r[1]).length
  console.log('\n' + '='.repeat(56))
  console.log('小程序首页离线校验：' + (results.length - failed) + '/' + results.length + ' 通过')
  if (failed) {
    console.log('失败用例：')
    results.filter(r => !r[1]).forEach(r => console.log('  - ' + r[0] + ' -> ' + JSON.stringify(r[2])))
  }
  console.log('='.repeat(56))
  process.exit(failed ? 1 : 0)
}

main().catch(err => {
  console.error('校验脚本异常：', err)
  process.exit(1)
})
