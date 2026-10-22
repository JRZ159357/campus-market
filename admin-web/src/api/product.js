// 文件路径: admin-web/src/api/product.js
import request from '@/utils/request'

/**
 * 管理端商品列表
 * @param {{pageNum:number,pageSize:number,status?:number,keyword?:string,categoryId?:number,
 *          minPrice?:number,maxPrice?:number,startTime?:string,endTime?:string}} params
 */
export const pageAuditProducts = params => request.get('/product/audit/page', { params })

/** 商品详情 */
export const getProduct = id => request.get(`/product/${id}`)

/**
 * 审核商品
 * @param {number} id
 * @param {{status:number, auditReason?:string}} data status: 1通过 2驳回
 */
export const auditProduct = (id, data) => request.put(`/product/${id}/audit`, data)

/** 强制下架 */
export const forceOfflineProduct = (id, reason) =>
  request.put(`/product/${id}/force-offline`, { reason })
