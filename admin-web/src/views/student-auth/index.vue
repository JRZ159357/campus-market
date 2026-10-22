<!-- 文件路径: admin-web/src/views/student-auth/index.vue -->
<template>
  <div class="page-container">
    <div class="page-card">
      <!-- 1. 搜索筛选栏 -->
      <el-form :model="query" inline class="search-bar">
        <el-form-item label="审核状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            placeholder="学号 / 姓名"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 2. 工具栏 -->
      <div class="toolbar">
        <span class="toolbar__title">
          认证申请
          <el-tag v-if="pendingCount > 0" type="warning" effect="dark" size="small" round>
            待审核 {{ pendingCount }}
          </el-tag>
        </span>
        <el-button :icon="Refresh" circle @click="loadData" />
      </div>

      <!-- 3. 错误态 -->
      <el-result
        v-if="error"
        icon="error"
        title="数据加载失败"
        sub-title="请检查后端服务是否启动、网络是否正常"
      >
        <template #extra>
          <el-button type="primary" @click="loadData">重新加载</el-button>
        </template>
      </el-result>

      <!-- 4. 表格 -->
      <template v-else>
        <el-table
          v-loading="loading"
          :data="list"
          border
          stripe
          highlight-current-row
          :header-cell-style="{ background: '#fafafa', color: '#606266', fontWeight: 600 }"
          style="width: 100%"
        >
          <el-table-column type="index" label="#" width="55" align="center" />
          <el-table-column label="申请人" min-width="180">
            <template #default="{ row }">
              <div class="applicant">
                <el-avatar :size="32" :src="row.avatar">
                  {{ (row.nickname || 'U').slice(0, 1) }}
                </el-avatar>
                <div class="applicant__info">
                  <div class="applicant__name">{{ row.nickname || '未设置昵称' }}</div>
                  <div class="applicant__phone">{{ row.phone || '未绑定手机号' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="studentNo" label="学号" width="130" />
          <el-table-column prop="realName" label="姓名" width="100" />
          <el-table-column prop="school" label="学校" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.school || '—' }}</template>
          </el-table-column>
          <el-table-column label="学生证" width="90" align="center">
            <template #default="{ row }">
              <el-image
                v-if="row.certImg"
                :src="row.certImg"
                :preview-src-list="[row.certImg]"
                :preview-teleported="true"
                fit="cover"
                class="cert-thumb"
              >
                <template #error>
                  <div class="cert-thumb__error">加载失败</div>
                </template>
              </el-image>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="提交时间" width="170" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.status).type" effect="light">
                {{ statusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="row.status === 0"
                v-permission="'admin'"
                link
                type="success"
                @click="openAudit(row)"
              >
                审核
              </el-button>
              <span v-if="row.status !== 0" class="text-muted">已处理</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="query.keyword || query.status !== null ? '没有符合条件的申请' : '暂无认证申请'" />
          </template>
        </el-table>

        <!-- 5. 分页 -->
        <div class="pagination">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handleSizeChange"
            @current-change="loadData"
          />
        </div>
      </template>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="认证申请详情" width="560px" destroy-on-close>
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="申请人">
          {{ current.nickname || '—' }}（ID: {{ current.userId }}）
        </el-descriptions-item>
        <el-descriptions-item label="手机号">{{ current.phone || '未绑定' }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ current.studentNo }}</el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ current.realName }}</el-descriptions-item>
        <el-descriptions-item label="学校">{{ current.school || '—' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ current.createTime }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">
          <el-tag :type="statusMeta(current.status).type">{{ statusMeta(current.status).label }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="current.auditTime" label="审核时间">
          {{ current.auditTime }}
        </el-descriptions-item>
        <el-descriptions-item v-if="current.rejectReason" label="驳回原因">
          <span class="danger-text">{{ current.rejectReason }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="学生证照片">
          <el-image
            v-if="current.certImg"
            :src="current.certImg"
            :preview-src-list="[current.certImg]"
            :preview-teleported="true"
            fit="contain"
            class="cert-large"
          />
          <span v-else>—</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="current?.status === 0"
          v-permission="'admin'"
          type="primary"
          @click="openAudit(current)"
        >
          去审核
        </el-button>
      </template>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog v-model="auditVisible" title="审核学生认证" width="520px" destroy-on-close>
      <el-alert
        v-if="auditTarget"
        type="info"
        :closable="false"
        show-icon
        class="audit-alert"
        :title="`${auditTarget.realName} · ${auditTarget.studentNo}`"
        description="审核通过后，该用户角色将升级为「认证学生」，并获得发布商品权限。"
      />
      <el-form ref="auditFormRef" :model="auditForm" :rules="auditRules" label-width="90px">
        <el-form-item label="审核结果" prop="status">
          <el-radio-group v-model="auditForm.status">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="2">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditForm.status === 2" label="驳回原因" prop="rejectReason">
          <el-input
            v-model.trim="auditForm.rejectReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写驳回原因，将展示给申请人"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAuditSubmit">提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { pageStudentAuth, auditStudentAuth } from '@/api/studentAuth'

// ---------- 字典 ----------
const statusOptions = [
  { label: '待审核', value: 0 },
  { label: '已通过', value: 1 },
  { label: '已驳回', value: 2 }
]

const statusMeta = status => {
  switch (status) {
    case 0:
      return { label: '待审核', type: 'warning' }
    case 1:
      return { label: '已通过', type: 'success' }
    case 2:
      return { label: '已驳回', type: 'danger' }
    default:
      return { label: '未知', type: 'info' }
  }
}

// ---------- 列表 ----------
const query = reactive({ pageNum: 1, pageSize: 10, status: null, keyword: '' })
const list = ref([])
const total = ref(0)
const pendingCount = ref(0)
const loading = ref(false)
const error = ref(false)

const loadData = async () => {
  loading.value = true
  error.value = false
  try {
    const data = await pageStudentAuth(query)
    list.value = data.records || []
    total.value = Number(data.total || 0)
    pendingCount.value = query.status === 0 ? Number(data.total || 0) : countPending()
  } catch (e) {
    error.value = true
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 未筛选待审核时，用当前页数据估算待审核数量提示（完整统计将在统计接口中提供）
const countPending = () => list.value.filter(item => item.status === 0).length

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.status = null
  query.keyword = ''
  query.pageNum = 1
  loadData()
}

const handleSizeChange = () => {
  query.pageNum = 1
  loadData()
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const current = ref(null)

const openDetail = row => {
  current.value = row
  detailVisible.value = true
}

// ---------- 审核 ----------
const auditVisible = ref(false)
const submitting = ref(false)
const auditFormRef = ref()
const auditTarget = ref(null)
const auditForm = reactive({ status: 1, rejectReason: '' })

const auditRules = {
  status: [{ required: true, message: '请选择审核结果', trigger: 'change' }],
  rejectReason: [
    {
      validator: (rule, value, callback) => {
        if (auditForm.status === 2 && !value) {
          callback(new Error('驳回时必须填写驳回原因'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const openAudit = row => {
  auditTarget.value = row
  auditForm.status = 1
  auditForm.rejectReason = ''
  detailVisible.value = false
  auditVisible.value = true
}

const handleAuditSubmit = async () => {
  const valid = await auditFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await auditStudentAuth(auditTarget.value.id, {
      status: auditForm.status,
      rejectReason: auditForm.status === 2 ? auditForm.rejectReason : undefined
    })
    ElMessage.success(auditForm.status === 1 ? '已通过认证' : '已驳回申请')
    auditVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.applicant {
  display: flex;
  align-items: center;
  gap: 10px;
}

.applicant__name {
  font-size: 14px;
  color: #303133;
  line-height: 1.4;
}

.applicant__phone {
  font-size: 12px;
  color: #909399;
}

.cert-thumb {
  width: 46px;
  height: 34px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  cursor: zoom-in;
}

.cert-thumb__error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #c0c4cc;
  background: #f5f7fa;
}

.cert-large {
  width: 100%;
  max-height: 260px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  cursor: zoom-in;
}

.text-muted {
  color: #c0c4cc;
  font-size: 13px;
}

.danger-text {
  color: #f56c6c;
}

.audit-alert {
  margin-bottom: 18px;
}
</style>
