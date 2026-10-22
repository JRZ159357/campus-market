// 文件路径: admin-web/src/stores/user.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { adminLogin, getMe, logout as logoutApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  const isLogin = computed(() => !!token.value)
  const role = computed(() => userInfo.value?.role || '')
  const roleLabel = computed(() => userInfo.value?.roleLabel || '')
  const isAdmin = computed(() => role.value === 'admin')
  const isCustomerService = computed(() => role.value === 'customer_service')
  /** 客服只读模式：页面统一据此隐藏写操作入口 */
  const isReadOnly = computed(() => isCustomerService.value)

  /**
   * 登录并持久化 token
   */
  const login = async form => {
    const data = await adminLogin(form)
    token.value = data.token
    userInfo.value = data
    localStorage.setItem('token', data.token)
    localStorage.setItem('userInfo', JSON.stringify(data))
    return data
  }

  /**
   * 刷新用户信息（页面刷新后校验 token 是否仍有效）
   */
  const fetchMe = async () => {
    const data = await getMe()
    userInfo.value = { ...(userInfo.value || {}), ...data }
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    return data
  }

  const reset = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  const logout = async () => {
    try {
      await logoutApi()
    } catch (e) {
      // 退出接口失败不阻断前端清理
    } finally {
      reset()
    }
  }

  return {
    token,
    userInfo,
    isLogin,
    role,
    roleLabel,
    isAdmin,
    isCustomerService,
    isReadOnly,
    login,
    fetchMe,
    logout,
    reset
  }
})
