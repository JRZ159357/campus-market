// 小程序页面逻辑离线校验：用假的 wx / Page / getApp 驱动页面方法，检查 setData 路径与接口数据结构
const path = require('path')

const ROOT = 'D:/毕业设计/skill/campus-market/miniprogram'
const toasts = []
const navigations = []
let actionSheetIndex = 0

const responses = {
  'GET /api/favorite/my': {
    code: 200,
    data: {
      records: [
        { favoriteId: 98, id: 41, sellerId: 12, sellerNickname: '李同学', sellerAvatar: '/uploads/a.png',
          title: '高数教材', price: 25, originalPrice: 59.8, cover: '/uploads/c1.png',
          status: 1, statusLabel: '已上架', viewCount: 3, favoriteCount: 2, isVerifiedSeller: 1,
          favoriteTime: '2026-10-21 14:00:00' },
        { favoriteId: 97, id: 42, sellerId: 12, sellerNickname: '李同学', sellerAvatar: '/uploads/a.png',
          title: '二手自行车', price: 180, originalPrice: 600, cover: '/uploads/c2.png',
          status: 3, statusLabel: '已售出', viewCount: 9, favoriteCount: 5, isVerifiedSeller: 1,
          favoriteTime: '2026-10-21 13:00:00' },
        { favoriteId: 96, id: 43, sellerId: 13, sellerNickname: '王同学', sellerAvatar: '',
          title: '台灯', price: 30, originalPrice: null, cover: '/uploads/c3.png',
          status: 2, statusLabel: '已下架', viewCount: 1, favoriteCount: 0, isVerifiedSeller: 0,
          favoriteTime: '2026-10-21 12:00:00' }
      ],
      nextCursor: null,
      hasMore: false
    }
  },
  'GET /api/follow/following': {
    code: 200,
    data: {
      records: [{ userId: 12, nickname: '李同学', avatar: '/uploads/a.png', studentVerified: true,
                  creditScore: 100, onSaleCount: 5, fansCount: 8, followed: true,
                  followTime: '2026-10-21 14:40:02' }],
      total: 1, current: 1, size: 10
    }
  },
  'GET /api/follow/followers': {
    code: 200,
    data: {
      records: [{ userId: 13, nickname: '王同学', avatar: '', studentVerified: false,
                  creditScore: 100, onSaleCount: 2, fansCount: 3, followed: false,
                  followTime: '2026-10-21 14:20:00' }],
      total: 1, current: 1, size: 10
    }
  },
  'POST /api/favorite/41': { code: 200, data: { favorited: false, favoriteCount: 1 } },
  'GET /api/product/41': {
    code: 200,
    data: {
      id: 41, sellerId: 12, categoryId: 2, categoryName: '教材书籍', title: '高数教材',
      description: '九成新', price: 25, originalPrice: 59.8, images: ['/uploads/c1.png'],
      status: 1, statusLabel: '已上架', viewCount: 3, favoriteCount: 2, favorited: true,
      isVerifiedSeller: true, createTime: '2026-10-21 10:00:00', isOwner: false,
      seller: { id: 12, nickname: '李同学', avatar: '/uploads/a.png', creditScore: 100,
                studentVerified: true, onSaleCount: 5, fansCount: 8, followed: false }
    }
  }
}

global.wx = {
  getStorageSync: key => (key === 'cm_token' ? 'fake-token' : ''),
  setStorageSync: () => {},
  removeStorageSync: () => {},
  request(options) {
    const clean = options.url.replace('http://localhost:8080', '').split('?')[0]
    const key = options.method + ' ' + clean
    const followMatch = clean.match(/^\/api\/follow\/(\d+)$/)
    let body
    if (options.method === 'POST' && followMatch) {
      body = handleFollowToggle(Number(followMatch[1]))
    } else {
      body = responses[key] || { code: 500, msg: '未预置响应: ' + key }
    }
    options.success && options.success({ data: body })
    options.complete && options.complete()
  },
  showToast: opt => toasts.push(opt.title),
  navigateTo: opt => navigations.push(opt.url),
  switchTab: opt => navigations.push(opt.url),
  redirectTo: opt => navigations.push(opt.url),
  stopPullDownRefresh: () => {},
  showActionSheet: opt => opt.success && opt.success({ tapIndex: actionSheetIndex })
}

const appStub = {
  globalData: { token: 'fake-token', userInfo: { favoriteCount: 3 } },
  isLogin: () => true,
  setUserInfo: info => Object.assign(appStub.globalData.userInfo, info)
}
global.getApp = () => appStub

let captured = null
global.Page = obj => { captured = obj }

/** 关注态是"切换"语义，用可变状态模拟，避免各页面共享同一个静态响应 */
const followStates = {}
const FANS = { 12: { off: 7, on: 9 }, 13: { off: 3, on: 4 } }
function handleFollowToggle(userId) {
  followStates[userId] = !followStates[userId]
  const fans = FANS[userId] || { off: 0, on: 1 }
  return {
    code: 200,
    data: { followed: followStates[userId], fansCount: followStates[userId] ? fans.on : fans.off }
  }
}

function setByPath(target, path, value) {
  const tokens = path.replace(/\[(\d+)\]/g, '.$1').split('.')
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

async function testFavorite() {
  console.log('\n== 我的收藏页 ==')
  const page = instantiate('pages/favorite/index.js')
  page.onShow()
  await sleep(60)
  check('首页加载出两列卡片', page.data.left.length + page.data.right.length === 3,
    [page.data.left.length, page.data.right.length])
  check('收藏总数取自用户信息', page.data.count === 3, page.data.count)
  check('加载态已关闭', page.data.loading === false)
  check('已售出商品在列表中（用于蒙层）',
    page.data.left.concat(page.data.right).some(i => i.status === 3))

  actionSheetIndex = 0
  page.onLongPress({ currentTarget: { dataset: { id: 41 } } })
  await sleep(80)
  check('长按取消收藏后卡片被移除',
    page.data.left.concat(page.data.right).every(i => i.id !== 41))
  check('取消收藏后数量同步减一', page.data.count === 2, page.data.count)
  check('取消收藏有 toast 提示', toasts.includes('已取消收藏'), toasts.slice(-1))

  page.onMaskTap({ currentTarget: { dataset: { status: 3 } } })
  check('已售出商品点击给出解释', toasts.includes('该商品已售出'))

  page.goHome()
  check('空状态去逛逛跳转首页', navigations.includes('/pages/index/index'))
}

async function testFollow() {
  console.log('\n== 关注页 ==')
  followStates[12] = true
  followStates[13] = false
  const page = instantiate('pages/follow/index.js')
  page.onShow()
  await sleep(60)
  check('关注列表加载 1 条', page.data.list.length === 1)
  check('关注总数正确', page.data.followingTotal === 1)
  check('头像地址已拼成完整 URL',
    page.data.list[0].avatarUrl.startsWith('http://localhost:8080'), page.data.list[0].avatarUrl)
  check('昵称首字用于文字头像', page.data.list[0].initial === '李')

  page.switchTab({ currentTarget: { dataset: { tab: 1 } } })
  await sleep(60)
  check('切换到粉丝 Tab 后重新加载', page.data.tab === 1 && page.data.list.length === 1)
  check('粉丝 Tab 的总数独立统计', page.data.followersTotal === 1)
  check('未回关时按钮状态为 false', page.data.list[0].followed === false)

  page.onToggle({ currentTarget: { dataset: { id: 13 } } })
  await sleep(80)
  check('回关后按钮变为已关注', page.data.list[0].followed === true)
  check('回关后粉丝数更新', page.data.list[0].fansText === 4, page.data.list[0].fansText)

  page.switchTab({ currentTarget: { dataset: { tab: 0 } } })
  await sleep(60)
  page.onToggle({ currentTarget: { dataset: { id: 12 } } })
  await sleep(80)
  check('关注列表取消关注后移除该用户', page.data.list.length === 0, page.data.list.length)
  check('取消关注后总数减一', page.data.followingTotal === 0, page.data.followingTotal)

  // 模块8 起卖家主页已实现：关注列表点头像直接跳主页
  page.goSeller({ currentTarget: { dataset: { id: 12 } } })
  check('点击头像跳转到卖家主页',
    navigations[navigations.length - 1] === '/pages/user/home?id=12',
    navigations.slice(-1))
}

async function testDetail() {
  console.log('\n== 商品详情页 ==')
  followStates[12] = false
  const page = instantiate('pages/product/detail.js')
  page.id = 41
  await page.load()
  check('详情加载成功', !!page.data.detail)
  check('收藏态与收藏数正确',
    page.data.detail.favorited === true && page.data.detail.favoriteCount === 2,
    [page.data.detail.favorited, page.data.detail.favoriteCount])
  check('收藏按钮文案带数量', page.data.detail.favLabel === '收藏 2', page.data.detail.favLabel)
  check('卖家粉丝数已填充', page.data.detail.seller.fansCount === 8)
  check('卖家头像已拼完整地址',
    page.data.detail.seller.avatarUrl.startsWith('http://localhost:8080'))

  await page.favorite()
  check('点击收藏后切换为未收藏', page.data.detail.favorited === false)
  check('点击收藏后数量更新', page.data.detail.favoriteCount === 1)
  check('取消收藏有 toast', toasts.includes('已取消收藏'))

  await page.follow()
  check('点击关注后状态为已关注', page.data.detail.seller.followed === true)
  check('关注后粉丝数更新', page.data.detail.seller.fansCount === 9, page.data.detail.seller.fansCount)

  const ownerPage = instantiate('pages/product/detail.js')
  ownerPage.id = 41
  await ownerPage.load()
  check('卖家本人不显示关注按钮（isOwner 为 true 由模板判断）', ownerPage.data.detail.isOwner === false)
}

;(async () => {
  await testFavorite()
  await testFollow()
  await testDetail()
  const failed = results.filter(r => !r[1])
  console.log('\n结果：%d/%d 通过', results.length - failed.length, results.length)
  if (failed.length) {
    failed.forEach(f => console.log('  FAIL ' + f[0]))
    process.exit(1)
  }
})()
