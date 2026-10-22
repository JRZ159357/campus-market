// 文件路径: admin-web/src/api/upload.js
import request from '@/utils/request'

/**
 * 上传图片，返回服务端相对地址 /uploads/yyyy/MM/xxx.png
 * 分类图标、商品图片共用同一接口
 */
export const uploadImage = file => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
