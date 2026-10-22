// 文件路径: miniprogram/api/product.js
const { request } = require('../utils/request')

/** 拼接查询串，自动忽略空值 */
function qs(params = {}) {
  const parts = Object.keys(params)
    .filter(key => params[key] !== undefined && params[key] !== null && params[key] !== '')
    .map(key => key + '=' + encodeURIComponent(params[key]))
  return parts.length ? '?' + parts.join('&') : ''
}

/** 分类列表（免登录） */
const listCategories = () => request({ url: '/api/category/list', auth: false })

/** 商品列表（免登录） */
const listProducts = params => request({ url: '/api/product/page' + qs(params), auth: false })

/** 关键词搜索（标题+描述模糊，支持分类/价格区间/排序） */
const searchProducts = params => request({ url: '/api/product/search' + qs(params), auth: false })

/** 热门商品（浏览量+收藏量加权前10，服务端缓存5分钟） */
const listHotProducts = () => request({ url: '/api/product/hot', auth: false })

/**
 * 瀑布流游标分页
 * @param {{cursor?:number, size?:number, categoryId?:number}} params
 */
const feedProducts = params => request({ url: '/api/product/feed' + qs(params), auth: false })

/** 商品详情（免登录，携带 token 时会返回 isOwner） */
const getProduct = id => request({ url: '/api/product/' + id })

/** 发布商品 */
const createProduct = data => request({ url: '/api/product', method: 'POST', data })

/** 编辑商品 */
const updateProduct = (id, data) => request({ url: '/api/product/' + id, method: 'PUT', data })

/** 我的发布 */
const pageMyProducts = params => request({ url: '/api/product/my' + qs(params) })

/** 下架商品 */
const offlineProduct = id => request({ url: '/api/product/' + id + '/offline', method: 'PUT' })

/** 删除商品 */
const deleteProduct = id => request({ url: '/api/product/' + id, method: 'DELETE' })

module.exports = {
  listCategories,
  listProducts,
  searchProducts,
  listHotProducts,
  feedProducts,
  getProduct,
  createProduct,
  updateProduct,
  pageMyProducts,
  offlineProduct,
  deleteProduct
}
