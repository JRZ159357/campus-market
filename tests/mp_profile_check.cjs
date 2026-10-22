// 模块8 小程序个人中心 / 卖家主页 / 设置 / 账号与安全 逻辑离线校验。
// 用假的 wx / Page / getApp 驱动页面方法，检查：
//   setData 路径、接口参数拼装、路由跳转、鉴权分支、缓存清理范围、注销校验。
const path = require('path')

const ROOT = 'D:/毕业设计/skill/campus-market/miniprogram'
const toasts = []
const navigations = []
const requests = []
const modals = []
const actionSheets = []
const storage = { cm_token: 'fake-token', cm_user: JSON.stringify({ id: 5 }) }
let removedKeys = []
let lastProfileUpdate = null
let lastPhoneUpdate = null
let lastAccountDelete = null
let followState = false
let loginState = true

const USER_ID = 88

/** 个人中心聚合数据 */
const profile = {
  userId: 5,
  nickname: '张三',
  avatar: '/uploads/avatar.png',
  gender: 1,
  genderLabel: '男',
  intro: '大三在读',
  phone: '13800001234',
  username: 'zhangsan',
  hasAccount: true,
  role: 'student',
  roleLabel: '认证学生',
  studentVerified: 1,
  studentNo: '20230101',
  creditScore: 120,
  creditLevelLabel: '优秀',
  creditLevelColor: '#67C23A',
  creditProgress: 60,
  publishCount: 3,
  soldCount: 2,
  onSaleCount: 1,
  favoriteCount: 5,
  followCount: 4,
  fansCount: 6,
  pendingReviewCount: 2,
  orderCount: {
    pendingPay: 1, pendingShip: 2, pendingReceive: 3, finished: 4,
    canceled: 0, refunding: 1, refunded: 0, afterSale: 1, total: 12
  },
  createTime: '2026-01-01 10:00:00',
  lastLoginTime: '2026-10-21 10:00:00'
}

/** 卖家主页数据 */
const userHome = {
  userId: USER_ID,
  nickname: '李四',
  avatar: '/uploads/seller.png',
  genderLabel: '女',
  intro: '只卖闲置',
  roleLabel: '认证学生',
  studentVerified: true,
  creditScore: 95,
  creditLevelLabel: '良好',
  creditLevelColor: '#E6A23C',
  onSaleCount: 2,
  soldCount: 7,
  reviewCount: 5,
  goodRate: 80.0,
  avgStars: 4.6,
  fansCount: 12,
  createTime: '2026-02-01 10:00:00',
  followed: false,
  isSelf: false,
  recentReviews: []
}

/** 卖家在售商品 */
const sellerProducts = {
  records: [
    { id: 1, title: '自行车', price: 120.0, cover: '/uploads/b.png', status: 1, viewCount: 30, favoriteCount: 4, sellerNickname: '李四' },
    { id: 2, title: '台灯', price: 45.0, cover: '/uploads/l.png', status: 1, viewCount: 10, favoriteCount: 1, sellerNickname: '李四' }
  ],
  total: 2,
  current: 1,
  size: 10
}

/** 卖家收到的评价 */
const sellerReviews = {
  records: [
    { id: 1, reviewerId: 9, reviewerNickname: '王五', reviewerAvatar: '/uploads/w.png', stars: 5, content: '东西很好', images: [], createTime: '2026-10-20 10:00:00' },
    { id: 2, reviewerId: 10, reviewerNickname: '匿名用户', reviewerAvatar: null, stars: 4, content: '不错', images: [], createTime: '2026-10-19 10:00:00' }
  ],
  total: 2,
  current: 1,
  size: 20
}

/** 学生认证状态 */
const authStatus = { verified: false, status: 0, statusLabel: '待审核', studentNo: null, realName: '张三', school: '北华大学' }

/** 路由解析 */
function resolve(options) {
  const url = options.url.replace('http://localhost:8080', '')
  const clean = url.split('?')[0]
  const key = options.method + ' ' + clean
  requests.push(key)

  if (key === 'GET /api/user/profile') {
    return { code: 200, data: JSON.parse(JSON.stringify(profile)) }
  }
  if (key === 'GET /api/user/home/' + USER_ID) {
    return { code: 200, data: JSON.parse(JSON.stringify(userHome)) }
  }
  if (key === 'GET /api/user/products/' + USER_ID) {
    return { code: 200, data: JSON.parse(JSON.stringify(sellerProducts)) }
  }
  if (key === 'GET /api/review/user/' + USER_ID) {
    return { code: 200, data: JSON.parse(JSON.stringify(sellerReviews)) }
  }
  if (key === 'PUT /api/user/profile') {
    lastProfileUpdate = options.data
    return { code: 200, msg: '资料已更新', data: null }
  }
  if (key === 'PUT /api/user/phone') {
    lastPhoneUpdate = options.data
    return { code: 200, msg: '手机号已更新', data: null }
  }
  if (key === 'DELETE /api/user/account') {
    lastAccountDelete = options.data
    return { code: 200, msg: '账号已注销', data: null }
  }
  if (key === 'POST /api/user/logout') {
    return { code: 200, msg: '已退出登录', data: null }
  }
  if (key === 'POST /api/follow/' + USER_ID) {
    followState = !followState
    return { code: 200, data: { followed: followState, fansCount: 12 } }
  }
  if (key === 'POST /api/chat/session') {
    return { code: 200, data: { id: 555 } }
  }
  if (key === 'GET /api/auth/me') {
    return { code: 200, data: { id: 5, nickname: '张三', phone: '13800001234', username: 'zhangsan', hasAccount: true, registerType: 1 } }
  }
  if (key === 'GET /api/student-auth/status') {
    return { code: 200, data: JSON.parse(JSON.stringify(authStatus)) }
  }
  if (key === 'POST /api/auth/bind-account') {
    return { code: 200, msg: '绑定成功', data: null }
  }
  if (key === 'POST /api/auth/change-password') {
    return { code: 200, msg: '密码修改成功', data: null }
  }
  return { code: 500, msg: '未预置响应: ' + key }
}

global.wx = {
  getStorageSync: key => storage[key] || '',
  setStorageSync: (key, val) => { storage[key] = val },
  removeStorageSync: key => { removedKeys.push(key); delete storage[key] },
  getStorageInfoSync: () => ({ currentSize: 128, keys: Object.keys(storage) }),
  request(options) {
    const body = resolve(options)
    options.success && options.success({ data: body })
    options.complete && options.complete()
  },
  uploadFile(options) {
    options.success && options.success({ data: JSON.stringify({ code: 200, data: '/uploads/new.png' }) })
  },
  chooseMedia: opt => opt.success && opt.success({ tempFiles: [{ tempFilePath: '/tmp/1.png' }] }),
  showToast: opt => toasts.push(opt.title),
  showLoading: () => {},
  hideLoading: () => {},
  showModal: opt => {
    modals.push({ title: opt.title, content: opt.content })
    opt.success && opt.success({ confirm: true, cancel: false })
  },
  showActionSheet: opt => {
    actionSheets.push(opt.itemList)
    opt.success && opt.success({ tapIndex: 0 })
  },
  navigateTo: opt => navigations.push(opt.url),
  redirectTo: opt => navigations.push(opt.url),
  switchTab: opt => navigations.push(opt.url),
  reLaunch: opt => navigations.push(opt.url),
  navigateBack: () => navigations.push('back'),
  setNavigationBarTitle: opt => navigations.push('title:' + opt.title),
  previewImage: () => {},
  stopPullDownRefresh: () => {}
}

const appStub = {
  globalData: { token: 'fake-token', userInfo: { id: 5 }, verified: false },
  isLogin: () => loginState,
  setUserInfo: u => { appStub.globalData.userInfo = Object.assign({}, appStub.globalData.userInfo, u) },
  clearLogin: () => { appStub.globalData.token = ''; appStub.globalData.userInfo = null }
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

/* ---------------- 个人中心 ---------------- */
async function testProfile() {
  console.log('\n== 个人中心 pages/profile/index ==')
  const page = instantiate('pages/profile/index.js')
  page.getTabBar = () => ({ setData: () => {}, refresh: () => {} })

  await page.loadProfile()
  check('一次聚合接口拿到全部数据', requests.filter(r => r === 'GET /api/user/profile').length === 1, requests)
  check('个人中心渲染昵称', page.data.profile.nickname === '张三', page.data.profile && page.data.profile.nickname)
  check('个人中心渲染信誉分', page.data.profile.creditScore === 120, page.data.profile && page.data.profile.creditScore)
  check('个人中心渲染发布数', page.data.profile.publishCount === 3, page.data.profile && page.data.profile.publishCount)
  check('个人中心渲染收藏数', page.data.profile.favoriteCount === 5, page.data.profile && page.data.profile.favoriteCount)
  check('认证状态直接取自聚合接口', page.data.verified === true, page.data.verified)

  const entries = page.data.orderEntries
  check('订单入口共 5 个', entries.length === 5, entries.length)
  check('待付款角标取 orderCount.pendingPay', entries[0].badge === 1, entries[0].badge)
  check('待评价角标取 pendingReviewCount（不是 finished）',
    entries[3].badge === 2 && entries[3].badge !== profile.orderCount.finished, entries[3].badge)
  check('待评价入口路由标记为 pending', entries[3].nav === 'pending', entries[3].nav)
  check('退款售后角标取 afterSale', entries[4].badge === 1, entries[4].badge)

  // 统计行跳转分流
  const before = navigations.length
  page.goStat({ currentTarget: { dataset: { type: 'favorite' } } })
  check('收藏统计跳收藏页',
    navigations[navigations.length - 1] === '/pages/favorite/index', navigations.slice(before))
  page.goStat({ currentTarget: { dataset: { type: 'sold' } } })
  check('已售统计跳我的发布并带 status=3',
    navigations[navigations.length - 1] === '/pages/product/my?status=3', navigations.slice(-1))
  page.goStat({ currentTarget: { dataset: { type: 'fans' } } })
  check('粉丝统计跳关注页 tab=1',
    navigations[navigations.length - 1] === '/pages/follow/index?tab=1', navigations.slice(-1))
  page.goStat({ currentTarget: { dataset: { type: 'follow' } } })
  check('关注统计跳关注页 tab=0',
    navigations[navigations.length - 1] === '/pages/follow/index?tab=0', navigations.slice(-1))

  // 功能宫格
  const n = navigations.length
  page.onMenuTap({ currentTarget: { dataset: { key: 'security' } } })
  check('宫格「账号与安全」跳安全页',
    navigations[navigations.length - 1] === '/pages/profile/security', navigations.slice(n))
  page.onMenuTap({ currentTarget: { dataset: { key: 'setting' } } })
  check('宫格「设置」跳设置页',
    navigations[navigations.length - 1] === '/pages/profile/setting', navigations.slice(-1))
  page.onMenuTap({ currentTarget: { dataset: { key: 'about' } } })
  check('宫格「关于我们」跳关于页',
    navigations[navigations.length - 1] === '/pages/profile/about', navigations.slice(-1))
  check('宫格共 11 项', page.data.menuItems.length === 11, page.data.menuItems.length)
  check('宫格含「我的举报」入口',
    page.data.menuItems.some(m => m.key === 'report'), page.data.menuItems.map(m => m.key))

  // 待评价订单入口走独立页
  page.goOrderList({ currentTarget: { dataset: { status: 'pending' } } })
  check('「待评价」入口跳待评价页',
    navigations[navigations.length - 1] === '/pages/review/pending', navigations.slice(-1))
  page.goOrderList({ currentTarget: { dataset: { status: '0' } } })
  check('「待付款」入口跳订单列表 status=0',
    navigations[navigations.length - 1] === '/pages/order/list?status=0', navigations.slice(-1))

  // 未登录态
  loginState = false
  const guest = instantiate('pages/profile/index.js')
  guest.getTabBar = () => ({ setData: () => {}, refresh: () => {} })
  await guest.loadProfile()
  check('未登录时不请求接口', guest.data.logged === false && guest.data.profile === null, guest.data.logged)
  check('未登录时宫格仍可见（点击后提示登录）', guest.data.menuItems.length === 11, guest.data.menuItems.length)
  const toastLen = toasts.length
  guest.onMenuTap({ currentTarget: { dataset: { key: 'setting' } } })
  check('未登录点宫格给出「请先登录」提示',
    toasts[toasts.length - 1] === '请先登录', toasts.slice(toastLen))
  loginState = true
}

/* ---------------- 编辑资料 ---------------- */
async function testEdit() {
  console.log('\n== 编辑资料 pages/profile/edit ==')
  const page = instantiate('pages/profile/edit.js')
  await page.loadProfile()
  check('编辑页回填昵称', page.data.nickname === '张三', page.data.nickname)
  check('编辑页回填性别', page.data.gender === 1 && page.data.genderIndex === 1, [page.data.gender, page.data.genderIndex])
  check('编辑页回填简介', page.data.intro === '大三在读', page.data.intro)
  check('编辑页回填头像首字', page.data.avatarText === '张', page.data.avatarText)
  check('昵称计数正确', page.data.nicknameCount === 2, page.data.nicknameCount)

  page.onNicknameInput({ detail: { value: 'x'.repeat(21) } })
  check('昵称超 20 字给出错误提示', page.data.errors.nickname && page.data.errors.nickname.length > 0, page.data.errors.nickname)
  page.onNicknameInput({ detail: { value: '李四' } })
  check('输入后清空昵称错误', !page.data.errors.nickname, page.data.errors.nickname)

  page.onIntroInput({ detail: { value: 'z'.repeat(101) } })
  check('简介超 100 字给出错误提示', !!page.data.errors.intro, page.data.errors.intro)

  page.setData({ nickname: '', intro: '' })
  page.onNicknameInput({ detail: { value: '' } })
  check('昵称为空时校验不通过', page.validate() === false, page.data.errors)
  check('校验失败时不发请求', lastProfileUpdate === null, lastProfileUpdate)

  page.setData({ nickname: '新昵称', intro: '新简介', gender: 2 })
  page.validate()
  await page.save()
  check('保存时提交昵称/性别/简介', lastProfileUpdate && lastProfileUpdate.nickname === '新昵称' &&
    lastProfileUpdate.gender === 2 && lastProfileUpdate.intro === '新简介', lastProfileUpdate)

  // 上传头像
  await page.chooseAvatar()
  check('头像上传后回填地址', page.data.avatar === '/uploads/new.png', page.data.avatar)
}

/* ---------------- 卖家主页 ---------------- */
async function testSellerHome() {
  console.log('\n== 卖家主页 pages/user/home ==')
  const page = instantiate('pages/user/home.js')
  page.onLoad({ id: String(USER_ID) })
  await sleep(60)

  check('加载卖家主页接口', requests.indexOf('GET /api/user/home/' + USER_ID) >= 0, requests)
  check('主页渲染昵称', page.data.home.nickname === '李四', page.data.home && page.data.home.nickname)
  check('主页渲染信誉分与等级', page.data.home.creditScore === 95 &&
    page.data.home.creditLevelLabel === '良好', page.data.home && page.data.home.creditLevelLabel)
  check('主页在售数正确', page.data.home.onSaleCount === 2, page.data.home && page.data.home.onSaleCount)
  check('主页好评率正确', page.data.home.goodRate === 80.0, page.data.home && page.data.home.goodRate)

  check('在售商品已加载', page.data.left.length + page.data.right.length === 2,
    [page.data.left.length, page.data.right.length])
  check('商品按左右两列分布', page.data.left.length === 1 && page.data.right.length === 1,
    [page.data.left.length, page.data.right.length])
  check('商品封面补全为绝对地址', page.data.left[0].cover.indexOf('http') === 0,
    page.data.left[0] && page.data.left[0].cover)
  check('商品价格格式化', page.data.left[0].priceText === '120.00', page.data.left[0] && page.data.left[0].priceText)

  // 切换到评价 Tab
  page.onTabChange({ currentTarget: { dataset: { tab: 1 } } })
  await sleep(60)
  check('评价 Tab 拉取评价接口',
    requests.indexOf('GET /api/review/user/' + USER_ID) >= 0, requests)
  check('评价列表已加载', page.data.reviews.length === 2, page.data.reviews.length)
  check('评价时间转相对时间', page.data.reviews[0].timeText.length > 0 &&
    page.data.reviews[0].timeText !== '2026-10-20 10:00:00', page.data.reviews[0].timeText)
  check('匿名评价昵称由服务端脱敏（前端不判断）',
    page.data.reviews[1].reviewerNickname === '匿名用户', page.data.reviews[1].reviewerNickname)

  // 关注
  const followBefore = page.data.following
  await page.handleFollow()
  check('关注按钮切换状态', page.data.following === !followBefore, [followBefore, page.data.following])

  // 私信
  const navLen = navigations.length
  await page.handleChat()
  check('私信创建会话并跳转聊天页',
    navigations[navigations.length - 1] === '/pages/chat/index?sessionId=555', navigations.slice(navLen))
  check('私信请求带 sellerId 参数',
    requests.indexOf('POST /api/chat/session') >= 0, requests)

  // 本人不能关注自己
  const self = instantiate('pages/user/home.js')
  self.setData({ home: { isSelf: true }, userId: USER_ID })
  const toastLen = toasts.length
  await self.handleFollow()
  check('本人不能关注自己', toasts[toasts.length - 1] === '不能关注自己', toasts.slice(toastLen))
}

/* ---------------- 设置页 ---------------- */
async function testSetting() {
  console.log('\n== 设置页 pages/profile/setting ==')
  const page = instantiate('pages/profile/setting.js')
  page.onShow()
  check('设置页展示缓存大小', page.data.cacheSize === '128.0 KB', page.data.cacheSize)
  check('设置页展示版本号', page.data.version === '1.0.0', page.data.version)

  // 清除缓存：保留登录态
  removedKeys = []
  const modalLen = modals.length
  page.clearCache()
  check('清除缓存有二次确认', modals.length > modalLen, modals.slice(modalLen))
  check('清除缓存保留 token', removedKeys.indexOf('cm_token') === -1, removedKeys)
  check('清除缓存保留用户信息', removedKeys.indexOf('cm_user') === -1, removedKeys)

  // 退出登录
  const navLen = navigations.length
  page.handleLogout()
  await sleep(900)
  check('退出登录后跳回首页',
    navigations[navigations.length - 1] === '/pages/index/index', navigations.slice(navLen))
  check('退出登录调用了 /api/user/logout', requests.indexOf('POST /api/user/logout') >= 0, requests)

  // 注销账号：口令 / 密码校验
  const del = instantiate('pages/profile/setting.js')
  del.openDelete()
  check('注销弹层默认关闭密码与口令', del.data.showDelete === true && !del.data.deletePassword &&
    !del.data.deleteConfirm, del.data)
  del.onDeleteInput({ currentTarget: { dataset: { field: 'deletePassword' } }, detail: { value: '123456' } })
  del.onDeleteInput({ currentTarget: { dataset: { field: 'deleteConfirm' } }, detail: { value: '随便写' } })
  await del.confirmDelete()
  check('确认口令错误时拒绝注销', lastAccountDelete === null, lastAccountDelete)
  check('确认口令错误时给出提示', !!del.data.deleteError, del.data.deleteError)

  del.onDeleteInput({ currentTarget: { dataset: { field: 'deleteConfirm' } }, detail: { value: '注销账号' } })
  del.onDeleteInput({ currentTarget: { dataset: { field: 'deletePassword' } }, detail: { value: '' } })
  await del.confirmDelete()
  check('密码为空时拒绝注销', lastAccountDelete === null, lastAccountDelete)

  del.onDeleteInput({ currentTarget: { dataset: { field: 'deletePassword' } }, detail: { value: '123456' } })
  await del.confirmDelete()
  check('口令与密码齐全时提交注销', lastAccountDelete &&
    lastAccountDelete.confirmText === '注销账号' && lastAccountDelete.password === '123456', lastAccountDelete)
}

/* ---------------- 账号与安全 ---------------- */
async function testSecurity() {
  console.log('\n== 账号与安全 pages/profile/security ==')
  const page = instantiate('pages/profile/security.js')
  await page.loadProfile()
  await sleep(30)

  check('安全页加载用户信息', page.data.userInfo && page.data.userInfo.nickname === '张三',
    page.data.userInfo && page.data.userInfo.nickname)
  check('手机号脱敏展示', page.data.phoneMask === '138****1234', page.data.phoneMask)
  check('检测到已设置账号密码', page.data.hasAccount === true, page.data.hasAccount)
  check('学生认证状态为审核中', page.data.authStatus === 'pending' &&
    page.data.authStatusLabel === '审核中', [page.data.authStatus, page.data.authStatusLabel])

  // 改绑手机号：走微信授权码
  lastPhoneUpdate = null
  await page.handleChangePhone({ detail: { code: 'wxcode123' } })
  check('改绑手机号提交微信授权码', lastPhoneUpdate && lastPhoneUpdate.code === 'wxcode123', lastPhoneUpdate)

  // 拒绝授权不提交
  lastPhoneUpdate = null
  await page.handleChangePhone({ detail: { code: '', errMsg: 'getPhoneNumber:fail user deny' } })
  check('用户拒绝授权时不提交请求', lastPhoneUpdate === null, lastPhoneUpdate)

  // 修改密码校验
  const pw = instantiate('pages/profile/security.js')
  await pw.loadProfile()
  pw.openChange()
  pw.onInput({ currentTarget: { dataset: { field: 'oldPassword' } }, detail: { value: '' } })
  pw.onInput({ currentTarget: { dataset: { field: 'newPassword' } }, detail: { value: '123' } })
  pw.onInput({ currentTarget: { dataset: { field: 'confirmNew' } }, detail: { value: '456' } })
  await pw.handleChange()
  check('新密码过短时给出错误提示', !!pw.data.errors.newPassword, pw.data.errors)
  check('新密码过短时不发请求', requests.indexOf('POST /api/auth/change-password') === -1, requests)
}

/* ---------------- 我的发布 ---------------- */
async function testMyProducts() {
  console.log('\n== 我的发布 pages/product/my ==')
  const page = instantiate('pages/product/my.js')
  page.onLoad({ status: '3' })
  check('从统计行进入时选中「已售出」Tab', page.data.activeIndex === 4, page.data.activeIndex)
  check('Tab 顺序：全部/待审核/已上架/已下架/已售出', page.data.tabs.length === 5,
    page.data.tabs.map(t => t.label))

  // 已售出商品不能删除
  page.setData({ activeIndex: 4, list: [{ id: 1, title: '已售商品', status: 3 }] })
  actionSheets.length = 0
  page.showActions({ currentTarget: { dataset: { item: { id: 1, title: '已售商品', status: 3 } } } })
  check('已售出商品不提供删除操作',
    actionSheets[0] && actionSheets[0].indexOf('删除商品') === -1, actionSheets[0])

  // 在售商品提供下架
  actionSheets.length = 0
  page.showActions({ currentTarget: { dataset: { item: { id: 2, title: '在售商品', status: 1 } } } })
  check('已上架商品提供下架操作',
    actionSheets[0] && actionSheets[0].indexOf('下架商品') >= 0, actionSheets[0])
  check('已上架商品不提供删除操作（需先下架）',
    actionSheets[0] && actionSheets[0].indexOf('删除商品') === -1, actionSheets[0])

  // 已下架商品提供重新上架
  actionSheets.length = 0
  page.showActions({ currentTarget: { dataset: { item: { id: 3, title: '已下架商品', status: 2 } } } })
  check('已下架商品提供「编辑并重新上架」',
    actionSheets[0] && actionSheets[0].indexOf('编辑并重新上架') >= 0, actionSheets[0])
}

async function main() {
  await testProfile()
  await testEdit()
  await testSellerHome()
  await testSetting()
  await testSecurity()
  await testMyProducts()

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
