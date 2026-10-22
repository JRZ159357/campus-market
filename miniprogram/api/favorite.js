// 文件路径: miniprogram/api/favorite.js
const { request } = require('../utils/request')

/**
 * 收藏 / 取消收藏（同一接口切换）
 * @param {number} productId
 * @returns {Promise<{favorited: boolean, favoriteCount: number}>}
 */
const toggleFavorite = productId =>
  request({ url: '/api/favorite/' + productId, method: 'POST' })

/**
 * 我的收藏（游标分页）
 * @param {{cursor?: number, size?: number}} params
 * @returns {Promise<{records: Array, nextCursor: number|null, hasMore: boolean}>}
 */
const pageMyFavorites = params => {
  const query = []
  if (params && params.cursor) query.push('cursor=' + params.cursor)
  if (params && params.size) query.push('size=' + params.size)
  return request({ url: '/api/favorite/my' + (query.length ? '?' + query.join('&') : '') })
}

/** 是否已收藏某商品（Redis O(1)） */
const checkFavorite = productId => request({ url: '/api/favorite/check/' + productId })

module.exports = { toggleFavorite, pageMyFavorites, checkFavorite }
