// 文件路径: admin-web/src/api/home.js
import request from '@/utils/request'

// ==================== 轮播图（Tab1） ====================
// 走 /home/banner/* 新前缀；后端同时兼容老的 /banner/*，这里统一用新路径

export const pageBanners = params => request.get('/home/banner/page', { params })

export const createBanner = data => request.post('/home/banner', data)

export const updateBanner = (id, data) => request.put(`/home/banner/${id}`, data)

export const deleteBanner = id => request.delete(`/home/banner/${id}`)

export const updateBannerStatus = (id, status) =>
  request.put(`/home/banner/${id}/status`, null, { params: { status } })

/** 批量排序：items = [{id, sort}]，拖拽结束后一次提交 */
export const sortBanners = items => request.put('/home/banner/sort', { items })

// ==================== 首页配置（Tab2 公告 / Tab4 热门 / Tab5 新品） ====================

export const getHomeConfig = () => request.get('/home/config')

export const saveHomeConfig = data => request.put('/home/config', data)

// ==================== 金刚区（Tab3） ====================

export const getCategorySort = () => request.get('/home/category/sort')

/** items = [{id, isHomeShow, sort}]，数组顺序即金刚区顺序 */
export const saveCategorySort = items => request.put('/home/category/sort', { items })

// ==================== 选品与预览用的商品数据 ====================

/** 按ID取已上架商品（保持传入顺序），用于手动选品的卡片回显 */
export const listPickedProducts = ids =>
  request.get('/home/product/picked', { params: { ids: (ids || []).join(',') } })

/** 商品搜索（只含已上架），用于选品弹窗 */
export const searchProducts = params => request.get('/product/search', { params })

/** 按浏览量+收藏量排序的热门商品，用于「自动模式」预览 */
export const hotProducts = () => request.get('/product/hot')

/** 最新上架商品（游标分页取第一页），用于「新品推荐」预览 */
export const latestProducts = size => request.get('/product/feed', { params: { size } })
