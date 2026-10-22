<!-- 文件路径: admin-web/src/views/system/account.vue -->
<template>
  <div class="page-container">
    <div class="page-card">
      <div class="head">
        <div class="head__text">
          <h2 class="head__title">账号管理</h2>
          <p class="head__desc">
            管理可以登录本后台的账号。管理员拥有全部权限，客服只能查看数据、处理举报与咨询。
            系统始终保留至少一个可用的管理员账号，避免把自己锁在门外。
          </p>
        </div>
        <div class="head__actions">
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          <el-button v-permission="'admin'" type="primary" :icon="Plus" @click="openCreate">
            新增账号
          </el-button>
        </div>
      </div>

      <!-- 筛选 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            placeholder="账号 / 昵称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="query.role"
            placeholder="全部"
            clearable
            style="width: 130px"
            @change="handleSearch"
          >
            <el-option v-for="r in ACCOUNT_ROLES" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option label="正常" :value="0" />
            <el-option label="禁用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-result
        v-if="error"
        icon="error"
        title="账号数据加载失败"
        sub-title="请检查后端服务是否启动、网络是否正常"
      >
        <template #extra>
          <el-button type="primary" @click="loadData">重新加载</el-button>
        </template>
      </el-result>

      <template v-else>
        <el-table v-loading="loading" :data="records" border style="width: 100%">
          <el-table-column label="账号" min-width="170">
            <template #default="{ row }">
              <div class="account-cell">
                <el-avatar :size="30" class="account-cell__avatar">
                  {{ (row.nickname || row.username || 'A').slice(0, 1) }}
                </el-avatar>
                <div class="account-cell__main">
                  <div class="account-cell__name">
                    {{ row.username }}
                    <el-tag v-if="row.self" size="small" type="info" effect="plain" class="self-tag">
                      本人
                    </el-tag>
                  </div>
                  <div class="account-cell__id">ID {{ row.id }}</div>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="nickname" label="昵称" min-width="130" show-overflow-tooltip />

          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-tag
                size="small"
                effect="light"
                :style="{
                  color: roleMeta(row.role).color,
                  borderColor: roleMeta(row.role).color + '66',
                  background: roleMeta(row.role).color + '14'
                }"
              >
                {{ row.roleLabel }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 0 ? 'success' : 'danger'" effect="plain">
                {{ row.statusLabel }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="最后登录" width="170">
            <template #default="{ row }">
              <span v-if="row.lastLoginTime" class="cell-time">
                {{ String(row.lastLoginTime).slice(0, 16) }}
              </span>
              <span v-else class="cell-muted">从未登录</span>
            </template>
          </el-table-column>

          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">
              <span class="cell-time">{{ String(row.createTime || '').slice(0, 16) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'admin'" link type="primary" @click="openEdit(row)">
                编辑
              </el-button>
              <el-button
                v-permission="'admin'"
                link
                type="primary"
                :disabled="row.self"
                :title="row.self ? '请用右上角「修改密码」重置自己的密码' : ''"
                @click="openReset(row)"
              >
                重置密码
              </el-button>
              <el-button
                v-permission="'admin'"
                link
                :type="row.status === 0 ? 'warning' : 'success'"
                :disabled="row.self"
                :title="row.self ? '不能禁用自己的账号' : ''"
                @click="toggleStatus(row)"
              >
                {{ row.status === 0 ? '禁用' : '启用' }}
              </el-button>
              <el-button
                v-permission="'admin'"
                link
                type="danger"
                :disabled="row.self"
                :title="row.self ? '不能删除自己的账号' : ''"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty
          v-if="!loading && !records.length"
          description="没有符合条件的账号"
          :image-size="90"
          class="empty"
        />

        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          class="pager"
          @current-change="loadData"
          @size-change="handleSearch"
        />
      </template>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="form.id ? '编辑账号' : '新增账号'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="账号" prop="username">
          <el-input
            v-model.trim="form.username"
            :disabled="!!form.id"
            maxlength="20"
            placeholder="4-20位字母或数字"
          />
          <div v-if="form.id" class="form-hint">账号是登录凭证与审计依据，创建后不可修改</div>
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="32"
            placeholder="6-32位"
          />
        </el-form-item>
        <el-form-item v-if="!form.id" label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            maxlength="32"
            placeholder="再输入一次密码"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model.trim="form.nickname" maxlength="50" placeholder="显示在操作日志里" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select
            v-model="form.role"
            style="width: 100%"
            :disabled="form.self && !!form.id"
            placeholder="请选择角色"
          >
            <el-option
              v-for="r in ACCOUNT_ROLES"
              :key="r.value"
              :label="r.label"
              :value="r.value"
            />
          </el-select>
          <div v-if="form.self && form.id" class="form-hint">不能修改自己的角色</div>
          <div v-else class="form-hint">
            管理员拥有全部权限；客服只能查看数据、处理举报与咨询，不能改动配置
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="0"
            :inactive-value="1"
            active-text="正常"
            inactive-text="禁用"
            :disabled="form.self && !!form.id"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetVisible" title="重置密码" width="460px" destroy-on-close>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="reset-alert"
        :title="`正在重置「${resetTarget.username}」的密码`"
        description="重置后请通过安全渠道把新密码告知本人。对方的下一次请求会使用新密码校验，旧登录态仍会过期。"
      />
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-width="90px">
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="resetForm.password"
            type="password"
            show-password
            maxlength="32"
            placeholder="6-32位"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="resetForm.confirmPassword"
            type="password"
            show-password
            maxlength="32"
            placeholder="再输入一次新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReset">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import {
  ACCOUNT_ROLES,
  createAccount,
  deleteAccount,
  pageAccounts,
  resetAccountPassword,
  roleMeta,
  updateAccount,
  updateAccountStatus
} from '@/api/account'

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', role: '', status: null })
const records = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref(false)

const loadData = async () => {
  loading.value = true
  error.value = false
  try {
    const data = await pageAccounts({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      role: query.role || undefined,
      status: query.status ?? undefined
    })
    records.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) {
    error.value = true
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.keyword = ''
  query.role = ''
  query.status = null
  query.pageNum = 1
  loadData()
}

// ---------- 新增 / 编辑 ----------
const formVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  role: 'customer_service',
  status: 0,
  self: false
})

// 两次密码一致性在表单层校验：放到提交后再由后端报错，
// 用户要重新填一遍整个表单，体验很差
const validateConfirm = (rule, value, callback) => {
  if (form.password !== value) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const formRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{4,20}$/, message: '账号需为4-20位字母或数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需为6-32位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const openCreate = () => {
  form.id = null
  form.username = ''
  form.password = ''
  form.confirmPassword = ''
  form.nickname = ''
  form.role = 'customer_service'
  form.status = 0
  form.self = false
  formVisible.value = true
}

const openEdit = row => {
  form.id = row.id
  form.username = row.username
  form.password = ''
  form.confirmPassword = ''
  form.nickname = row.nickname || ''
  form.role = row.role
  form.status = row.status
  form.self = !!row.self
  formVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return
    submitting.value = true
    try {
      if (form.id) {
        await updateAccount(form.id, {
          nickname: form.nickname,
          role: form.role,
          status: form.status
        })
        ElMessage.success('账号已更新')
      } else {
        await createAccount({
          username: form.username,
          password: form.password,
          nickname: form.nickname,
          role: form.role,
          status: form.status
        })
        ElMessage.success('账号已创建，请把账号与密码告知本人')
      }
      formVisible.value = false
      loadData()
    } catch (e) {
      /* 错误提示由 request 拦截器统一弹出 */
    } finally {
      submitting.value = false
    }
  })
}

const toggleStatus = async row => {
  const next = row.status === 0 ? 1 : 0
  const action = next === 1 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      next === 1
        ? `禁用后「${row.username}」将无法登录本后台，已登录的会话会在下次请求时被拒绝。确认继续？`
        : `确认启用「${row.username}」吗？`,
      `${action}确认`,
      { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await updateAccountStatus(row.id, next)
    ElMessage.success(`已${action}`)
    loadData()
  } catch (e) {
    /* 已由拦截器提示 */
  }
}

const handleDelete = async row => {
  try {
    await ElMessageBox.confirm(
      `确认删除账号「${row.username}」吗？删除后该账号无法登录，历史操作日志仍会保留。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteAccount(row.id)
    ElMessage.success('账号已删除')
    if (records.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    loadData()
  } catch (e) {
    /* 已由拦截器提示 */
  }
}

// ---------- 重置密码 ----------
const resetVisible = ref(false)
const resetFormRef = ref()
const resetTarget = ref({})
const resetForm = reactive({ password: '', confirmPassword: '' })

const resetRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需为6-32位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (resetForm.password !== value) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const openReset = row => {
  resetTarget.value = row
  resetForm.password = ''
  resetForm.confirmPassword = ''
  resetVisible.value = true
}

const submitReset = async () => {
  if (!resetFormRef.value) return
  await resetFormRef.value.validate(async valid => {
    if (!valid) return
    submitting.value = true
    try {
      await resetAccountPassword(resetTarget.value.id, { password: resetForm.password })
      resetVisible.value = false
      // 新密码由管理员自己输入，这里回显是为了让他确认「刚才填的是什么」，
      // 避免记错后又要再重置一次
      ElMessage({
        type: 'success',
        duration: 6000,
        showClose: true,
        message: `「${resetTarget.value.username}」的密码已重置为：${resetForm.password}`
      })
    } catch (e) {
      /* 已由拦截器提示 */
    } finally {
      submitting.value = false
    }
  })
}

onMounted(loadData)
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.head__title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.head__desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
  max-width: 720px;
}

.head__actions {
  flex: none;
  display: flex;
  gap: 8px;
}

.filter-form {
  margin-bottom: 4px;
}

.account-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.account-cell__avatar {
  flex: none;
  background: linear-gradient(135deg, #409eff, #8b5cf6);
  color: #fff;
  font-size: 13px;
}

.account-cell__name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.account-cell__id {
  font-size: 12px;
  color: #c0c4cc;
  font-family: Consolas, Monaco, monospace;
}

.self-tag {
  transform: scale(0.9);
}

.cell-time {
  font-size: 13px;
  color: #606266;
}

.cell-muted {
  font-size: 13px;
  color: #c0c4cc;
}

.empty {
  padding: 30px 0;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.form-hint {
  width: 100%;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.reset-alert {
  margin-bottom: 14px;
  border-radius: 8px;
}
</style>
