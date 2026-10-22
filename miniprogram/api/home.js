// 文件路径: miniprogram/api/home.js
const { request } = require('../utils/request')

/**
 * 首页聚合：公告 + 轮播图 + 金刚区 + 热门推荐 + 新品推荐 + 热门搜索词
 * 一次请求拿全首屏，避免弱网下多个串行请求叠加白屏。
 * 后端同时保留了 /api/home/index 这个老地址，老版本小程序不会因为升级而白屏。
 */
const getHomeIndex = () => request({ url: '/api/home', auth: false })

/** 轮播图列表（启用中） */
const listBanners = () => request({ url: '/api/home/banner/list', auth: false })

/** 热门搜索词 */
const getHotKeywords = () => request({ url: '/api/home/hot-keywords', auth: false })

module.exports = { getHomeIndex, listBanners, getHotKeywords }
