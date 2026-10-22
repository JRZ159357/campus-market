// 文件路径: admin-web/src/api/category.js
import request from '@/utils/request'

/** 分类列表。all=true 时管理员可拿到含禁用项的全部分类 */
export const listCategories = params => request.get('/category/list', { params })

/** 新增分类 */
export const createCategory = data => request.post('/category', data)

/** 编辑分类 */
export const updateCategory = (id, data) => request.put(`/category/${id}`, data)

/** 删除分类（分类下有商品会被拒绝） */
export const deleteCategory = id => request.delete(`/category/${id}`)

/** 启用 / 禁用分类 */
export const updateCategoryStatus = (id, status) =>
  request.put(`/category/${id}/status`, null, { params: { status } })
