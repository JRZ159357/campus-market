// 文件路径: admin-web/src/api/sensitiveWord.js
import request from '@/utils/request'

/** 敏感词分页（按类型 Tab / 状态 / 关键词筛选） */
export const pageSensitiveWords = params => request.get('/sensitive-word/page', { params })

/** 新增敏感词 */
export const createSensitiveWord = data => request.post('/sensitive-word', data)

/** 编辑敏感词 */
export const updateSensitiveWord = (id, data) => request.put(`/sensitive-word/${id}`, data)

/** 删除敏感词 */
export const deleteSensitiveWord = id => request.delete(`/sensitive-word/${id}`)

/** 启用 / 禁用 */
export const updateSensitiveWordStatus = (id, status) =>
  request.put(`/sensitive-word/${id}/status`, null, { params: { status } })

/** 批量添加（文本框一行一个） */
export const batchCreateSensitiveWords = data => request.post('/sensitive-word/batch', data)

/** 当前生效的词表，用于「词典体检」 */
export const listEnabledWords = () => request.get('/sensitive-word/enabled')

/** 手动重新加载内存词典（多实例部署时用） */
export const reloadSensitiveWords = () => request.post('/sensitive-word/reload')

/**
 * 类型选项。value 与后端 sensitive_word.type 一一对应，
 * 颜色只在前端维护——配色是展示层的事，后端不该知道「违禁品用红色」。
 */
export const SENSITIVE_TYPES = [
  { value: 1, label: '违禁品', color: '#F56C6C' },
  { value: 2, label: '辱骂', color: '#E6A23C' },
  { value: 3, label: '广告', color: '#409EFF' },
  { value: 4, label: '其他', color: '#909399' }
]

/** 等级选项：警告橙、拦截红 */
export const SENSITIVE_LEVELS = [
  { value: 1, label: '警告', color: '#E6A23C', hint: '允许发布，标记待人工复核并通知管理员' },
  { value: 2, label: '拦截', color: '#F56C6C', hint: '直接拒绝，提示内容包含违规词' }
]

/** 按码值取类型配置，取不到时给一个中性色，避免页面因脏数据崩掉 */
export const typeMeta = type =>
  SENSITIVE_TYPES.find(t => t.value === type) || { value: type, label: '未知', color: '#909399' }

/** 按码值取等级配置 */
export const levelMeta = level =>
  SENSITIVE_LEVELS.find(l => l.value === level) || { value: level, label: '未知', color: '#909399' }
