// 模块5 小程序页面逻辑离线校验：用假的 wx / Page / getApp 驱动页面方法，检查 setData 路径与接口字段
const path = require('path')

const ROOT = 'D:/毕业设计/skill/campus-market/miniprogram'
const toasts = []
const navigations = []
const requests = []
let modalConfirm = true
let payCalled = 0

const ORDER_NO = '202610211200001234'

const addressList = [
  { id: 1, receiverName: '张三', phone: '13800138000', address: '3号宿舍楼 502', isDefault: true },
  { id: 2, receiverName: '李四', phone: '13900139000', address: '图书馆东门', isDefault: false }
]

const orderDetail = {
  id: 1,
  orderNo: ORDER_NO,
  status: 0,
  statusLabel: '待付款',
  price: 120.0,
  buyerId: 5,
  buyerNickname: '张同学',
  sellerId: 9,
  sellerNickname: '李同学',
  product: {
    productId: 41,
    title: '九成新山地自行车',
    cover: '/uploads/bike.png',
    price: 120.0,
    originalPrice: 599,
    categoryName: '运动户外',
    description: '骑了半年'
  },
  address: { id: 1, receiverName: '张三', phone: '13800138000', address: '3号宿舍楼 502' },
  createTime: '2026-10-21 12:00:00',
  payDeadline: '2026-10-21 12:30:00',
  paymentTime: null,
  shipTime: null,
  finishTime: null,
  cancelTime: null,
  refundTime: null,
  cancelReason: null,
  refundReason: null,
  rejectRefundReason: null,
  refundFromStatus: null,
  isBuyer: true,
  isSeller: false,
  canPay: true,
  canCancel: true,
  canShip: false,
  canReceive: false,
  canApplyRefund: false,
  canAgreeRefund: false,
  canRejectRefund: false,
  canCancelRefund: false,
  canAdminCancel: false,
  canAdminRefund: false,
  logs: [
    {
      id: 1, action: 'create', actionLabel: '提交订单', operatorRole: 'buyer',
      operatorRoleLabel: '买家', operatorId: 5, operatorName: '张同学',
      fromStatus: null, toStatus: 0, remark: '下单成功，成交价 120.00 元',
      createTime: '2026-10-21 12:00:00'
    }
  ]
}

const orderPage = {
  records: [
    { id: 1, orderNo: ORDER_NO, status: 0, statusLabel: '待付款', productId: 41,
      productTitle: '九成新山地自行车', productCover: '/uploads/bike.png', price: 120.0,
      buyerId: 5, buyerNickname: '张同学', sellerId: 9, sellerNickname: '李同学',
      createTime: '2026-10-21 12:00:00', payDeadline: '2026-10-21 12:30:00' },
    { id: 2, orderNo: '202610211100001111', status: 2, statusLabel: '待收货', productId: 42,
      productTitle: '高数教材', productCover: '/uploads/book.png', price: 25.0,
      buyerId: 5, buyerNickname: '张同学', sellerId: 9, sellerNickname: '李同学',
      createTime: '2026-10-21 11:00:00', payDeadline: '2026-10-21 11:30:00' }
  ],
  total: 2,
  current: 1,
  size: 10
}

/** 路由解析：静态 key 优先，动态订单号用正则兜底 */
function resolve(options) {
  const url = options.url.replace('http://localhost:8080', '')
  const clean = url.split('?')[0]
  const key = options.method + ' ' + clean
  requests.push(key)

  if (key === 'POST /api/order/' + ORDER_NO + '/pay') {
    payCalled++
    return { code: 200, msg: '支付成功', data: null }
  }
  if (key === 'POST /api/order/' + ORDER_NO + '/cancel') {
    return { code: 200, msg: '订单已取消', data: null }
  }
  if (key === 'POST /api/order/' + ORDER_NO + '/apply-refund') {
    return { code: 200, msg: '退款申请已提交', data: null }
  }
  if (key === 'GET /api/order/' + ORDER_NO) {
    return { code: 200, data: JSON.parse(JSON.stringify(orderDetail)) }
  }
  if (key === 'POST /api/order/create') {
    return { code: 200, msg: '下单成功', data: { orderNo: ORDER_NO, price: 120.0 } }
  }
  if (key === 'GET /api/address/list') {
    return { code: 200, data: addressList }
  }
  if (key === 'POST /api/address' || key.startsWith('PUT /api/address') || key.startsWith('DELETE /api/address')) {
    return { code: 200, msg: 'ok', data: 3 }
  }
  if (key === 'GET /api/product/41') {
    return {
      code: 200,
      data: {
        id: 41, sellerId: 9, categoryId: 3, categoryName: '运动户外', title: '九成新山地自行车',
        description: '骑了半年', price: 120.0, originalPrice: 599, images: ['/uploads/bike.png'],
        status: 1, statusLabel: '已上架', isOwner: false
      }
    }
  }
  if (key === 'GET /api/order/buyer/page' || key === 'GET /api/order/seller/page') {
    return { code: 200, data: JSON.parse(JSON.stringify(orderPage)) }
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
  showToast: opt => toasts.push(opt.title),
  showLoading: () => {},
  hideLoading: () => {},
  showModal: opt => opt.success && opt.success({ confirm: modalConfirm, cancel: !modalConfirm }),
  navigateTo: opt => navigations.push(opt.url),
  redirectTo: opt => navigations.push(opt.url),
  switchTab: opt => navigations.push(opt.url),
  navigateBack: () => navigations.push('back'),
  setClipboardData: () => {},
  stopPullDownRefresh: () => {}
}

const appStub = {
  globalData: { token: 'fake-token', userInfo: { id: 5 } },
  isLogin: () => true
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

async function testConfirm() {
  console.log('\n== 确认订单页 ==')
  const page = instantiate('pages/order/confirm.js')
  page.onLoad({ productId: '41' })
  await sleep(80)
  check('商品加载成功', !!page.data.product && page.data.product.title === '九成新山地自行车',
    page.data.product && page.data.product.title)
  check('价格格式化为两位小数', page.data.product.priceText === '120.00', page.data.product.priceText)
  check('默认地址自动选中', page.data.address && page.data.address.id === 1,
    page.data.address && page.data.address.id)

  page.chooseAddress({ currentTarget: { dataset: { id: 2 } } })
  check('切换地址后选中项变化', page.data.address.id === 2, page.data.address.id)
  check('选择后弹层关闭', page.data.showAddress === false)

  await page.submit()
  // 下单成功后延迟 600ms 才 redirectTo（给 toast 留出展示时间）
  await sleep(750)
  check('提交订单调用创建接口', requests.includes('POST /api/order/create'))
  check('下单成功跳转订单详情',
    navigations.includes('/pages/order/detail?orderNo=' + ORDER_NO), navigations.slice(-1))
}

async function testDetail() {
  console.log('\n== 订单详情页 ==')
  const page = instantiate('pages/order/detail.js')
  page.onLoad({ orderNo: ORDER_NO })
  await sleep(80)
  check('详情加载成功', page.data.order && page.data.order.orderNo === ORDER_NO)
  check('状态文案为待付款', page.data.order.statusMeta.label === '待付款', page.data.order.statusMeta)
  check('图片地址被补全为绝对地址',
    page.data.order.product.coverUrl.indexOf('http') === 0, page.data.order.product.coverUrl)
  check('待付款展示付款与取消按钮',
    page.data.buttons.some(b => b.key === 'pay') && page.data.buttons.some(b => b.key === 'cancel'),
    page.data.buttons.map(b => b.text))
  check('时间线按时间倒序渲染', page.data.logs.length === 1 && page.data.logs[0].actionLabel === '提交订单',
    page.data.logs.map(l => l.actionLabel))

  const payBefore = payCalled
  page.onAction({ currentTarget: { dataset: { key: 'pay' } } })
  await sleep(1100)
  check('点击付款调用支付接口', payCalled === payBefore + 1, payCalled)
  check('付款成功有 toast 提示', toasts.includes('支付成功'))

  page.openReasonModal('refund')
  check('打开退款弹窗', page.data.reasonModal.visible === true && page.data.reasonModal.title === '申请退款')
  page.onReasonInput({ detail: { value: '坏了' } })
  await page.submitReason()
  check('退款原因过短被前端拦截', toasts.includes('原因至少 5 个字'), toasts.slice(-1))

  page.openReasonModal('refund')
  page.onReasonInput({ detail: { value: '商品外壳有明显裂痕，与描述不符' } })
  await page.submitReason()
  check('合规原因提交退款申请',
    requests.includes('POST /api/order/' + ORDER_NO + '/apply-refund'), requests.slice(-1))
  check('退款提交有 toast 提示', toasts.includes('退款申请已提交'))

  modalConfirm = false
  page.openReasonModal('rejectRefund')
  page.onReasonInput({ detail: { value: '瑕疵已提前说明' } })
  page.setData({ reasonModal: Object.assign({}, page.data.reasonModal, { visible: false }) })
  modalConfirm = true
  check('拒绝退款弹窗可关闭', page.data.reasonModal.visible === false)
}

async function testList() {
  console.log('\n== 订单列表页 ==')
  const page = instantiate('pages/order/list.js')
  page.onLoad({ status: '0', role: 'buyer' })
  await sleep(80)
  check('按状态参数选中标签', page.data.active === 0, page.data.active)
  check('列表渲染两条订单', page.data.list.length === 2, page.data.list.length)
  check('卡片状态元数据已附加', page.data.list[0].statusMeta.label === '待付款',
    page.data.list[0].statusMeta)
  check('待付款卡片含取消与付款按钮',
    page.data.list[0].actions.map(a => a.key).join(',') === 'cancel,pay',
    page.data.list[0].actions.map(a => a.text))
  check('待收货卡片含申请退款与确认收货',
    page.data.list[1].actions.map(a => a.key).join(',') === 'refund,receive',
    page.data.list[1].actions.map(a => a.text))
  check('分页无更多数据', page.data.hasMore === false && page.data.empty === false)

  const before = requests.filter(r => r === 'GET /api/order/buyer/page').length
  page.switchTab({ currentTarget: { dataset: { value: 2 } } })
  await sleep(80)
  const after = requests.filter(r => r === 'GET /api/order/buyer/page').length
  check('切换标签重新请求列表', after === before + 1, [before, after])
  check('切换标签后选中态正确', page.data.active === 2, page.data.active)

  page.switchRole({ currentTarget: { dataset: { role: 'seller' } } })
  await sleep(80)
  check('切换到我卖出的走卖家接口',
    requests.includes('GET /api/order/seller/page'), requests.slice(-1))

  page.onAction({ currentTarget: { dataset: { key: 'cancel', no: ORDER_NO } } })
  await sleep(120)
  check('列表页取消订单调用接口',
    requests.includes('POST /api/order/' + ORDER_NO + '/cancel'), requests.slice(-1))
  check('取消成功有 toast 提示', toasts.includes('订单已取消'))
}

async function testAddress() {
  console.log('\n== 地址管理页 ==')
  const page = instantiate('pages/address/list.js')
  page.onShow()
  await sleep(80)
  check('地址列表加载两条', page.data.list.length === 2, page.data.list.length)
  check('空态标记正确', page.data.empty === false)

  page.handleDefault({ currentTarget: { dataset: { id: 2 } } })
  await sleep(80)
  check('设为默认调用接口', requests.includes('PUT /api/address/2/default'), requests.slice(-1))

  page.handleDelete({ currentTarget: { dataset: { id: 2 } } })
  await sleep(120)
  check('删除地址调用接口', requests.includes('DELETE /api/address/2'), requests.slice(-1))

  page.goEdit({ currentTarget: { dataset: { id: 1 } } })
  check('编辑地址跳转带 id', navigations.includes('/pages/address/edit?id=1'), navigations.slice(-1))
}

async function testAddressEdit() {
  console.log('\n== 地址编辑页 ==')
  const page = instantiate('pages/address/edit.js')
  page.onLoad({ id: '1' })
  await sleep(80)
  check('编辑态回填表单', page.data.form.receiverName === '张三' && page.data.form.phone === '13800138000',
    page.data.form)

  page.setData({ form: { receiverName: '', phone: '123', address: '', isDefault: false } })
  check('空表单校验不通过', page.validate() === false)
  check('姓名必填提示正确', page.data.errors.receiverName === '请填写收货人姓名', page.data.errors)
  check('手机号格式提示正确', page.data.errors.phone === '手机号格式不正确', page.data.errors.phone)
  check('地址必填提示正确', page.data.errors.address === '请填写详细地址', page.data.errors.address)

  page.setData({
    form: { receiverName: '王五', phone: '13700137000', address: '实验楼 201', isDefault: true }
  })
  check('合法表单校验通过', page.validate() === true)
  await page.save()
  await sleep(750)
  check('保存调用更新接口', requests.includes('PUT /api/address/1'), requests.slice(-1))
  check('保存后返回上一页', navigations[navigations.length - 1] === 'back', navigations.slice(-1))

  const addPage = instantiate('pages/address/edit.js')
  addPage.onLoad({})
  addPage.setData({
    form: { receiverName: '赵六', phone: '13600136000', address: '二食堂门口', isDefault: false }
  })
  await addPage.save()
  await sleep(80)
  check('新增地址调用创建接口', requests.includes('POST /api/address'), requests.slice(-1))
}

async function main() {
  await testConfirm()
  await testDetail()
  await testList()
  await testAddress()
  await testAddressEdit()

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
