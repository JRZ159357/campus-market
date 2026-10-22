// 文件路径: admin-web/src/utils/request.js
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：注入 token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截：解包 Result，页面直接拿到业务数据
request.interceptors.response.use(
  response => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    // 401 / 403 统一处理：清 token 回登录页
    if (res.code === 401 || res.code === 403) {
      if (res.code === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        if (!location.hash.includes('/login')) {
          location.href = '/#/login'
        }
      }
      ElMessage.error(res.msg || '登录状态异常')
      return Promise.reject(new Error(res.msg || 'Error'))
    }
    ElMessage.error(res.msg || '操作失败')
    return Promise.reject(new Error(res.msg || 'Error'))
  },
  error => {
    const status = error.response?.status
    const msg = error.response?.data?.msg
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      location.href = '/#/login'
      ElMessage.error('登录已过期，请重新登录')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络或后端服务')
    } else {
      ElMessage.error(msg || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
