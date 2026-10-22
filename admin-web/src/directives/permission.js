// 文件路径: admin-web/src/directives/permission.js
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令。
 * 用法：v-permission="'admin'" 或 v-permission="['admin','customer_service']"
 * 语义：仅列出的角色可见；无权限时元素直接从 DOM 移除，避免“隐藏但仍可点击”。
 * 说明：前端隐藏只是体验层，真正的权限判定在服务端拦截器（客服写操作返回 403）。
 */
const normalize = value => {
  if (Array.isArray(value)) return value.filter(Boolean)
  return String(value || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}

export const hasPermission = value => {
  const roles = normalize(value)
  if (!roles.length) return true
  const userStore = useUserStore()
  return roles.includes(userStore.role)
}

const permission = {
  mounted(el, binding) {
    if (!hasPermission(binding.value)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}

export default permission
