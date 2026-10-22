<!-- 文件路径: admin-web/src/views/credit/index.vue -->
<template>
  <div class="page-container">
    <el-row :gutter="12">
      <!-- 左：用户搜索 -->
      <el-col :xs="24" :md="10">
        <div class="page-card credit-side">
          <div class="toolbar">
            <span class="toolbar__title">选择用户</span>
            <span class="toolbar__meta">共 {{ userTotal }} 人</span>
          </div>

          <el-input
            v-model.trim="keyword"
            placeholder="按昵称 / 账号 / 手机号 / 用户ID 搜索"
            clearable
            :prefix-icon="Search"
            class="credit-side__search"
            @keyup.enter="searchUsers"
            @clear="searchUsers"
          />

          <el-table
            v-loading="userLoading"
            :data="users"
            highlight-current-row
            height="calc(100vh - 240px)"
            @row-click="selectUser"
          >
            <el-table-column label="用户" min-width="150">
              <template #default="{ row }">
                <div class="user-cell">
                  <el-avatar :size="28" :src="row.avatar || undefined">
                    {{ (row.nickname || '?').slice(0, 1) }}
                  </el-avatar>
                  <div class="user-cell__info">
                    <span class="user-cell__name">{{ row.nickname }}</span>
                    <span class="user-cell__sub">ID {{ row.userId }}<template v-if="row.username"> · {{ row.username }}</template></span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="信誉分" width="82" align="center">
              <template #default="{ row }">
                <span class="score-num">{{ row.score }}</span>
              </template>
            </el-table-column>
            <el-table-column label="等级" width="86" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="dark" :color="row.levelColor" class="level-tag">
                  {{ row.levelLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="没有匹配的用户" :image-size="80" />
            </template>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="userPageNum"
              :page-size="userPageSize"
              :total="userTotal"
              layout="prev, pager, next"
              small
              @current-change="loadUsers"
            />
          </div>
        </div>
      </el-col>

      <!-- 右：信誉分详情 + 调整 + 历史 -->
      <el-col :xs="24" :md="14">
        <div v-if="!current" class="page-card empty-tip">
          <el-empty description="请先在左侧选择一个用户" :image-size="100" />
        </div>

        <template v-else>
          <!-- 信誉分卡片 -->
          <div class="page-card score-card" v-loading="detailLoading">
            <div class="score-card__head">
              <div class="score-card__user">
                <el-avatar :size="44" :src="detail.avatar || undefined">
                  {{ (detail.nickname || '?').slice(0, 1) }}
                </el-avatar>
                <div>
                  <div class="score-card__name">{{ detail.nickname }}</div>
                  <div class="score-card__id">用户 ID {{ detail.userId }}</div>
                </div>
              </div>
              <el-tag effect="dark" :color="detail.levelColor" class="level-tag level-tag--lg">
                {{ detail.levelLabel }}
              </el-tag>
            </div>

            <div class="score-card__body">
              <div class="score-card__num" :style="{ color: detail.levelColor }">
                {{ detail.score }}
                <span class="score-card__unit">分</span>
              </div>
              <div class="score-card__progress">
                <el-progress
                  :percentage="detail.progress"
                  :color="detail.levelColor"
                  :show-text="false"
                  :stroke-width="10"
                />
                <div class="score-card__hint">
                  <span v-if="detail.nextLevelLabel">
                    距「{{ detail.nextLevelLabel }}」还需 {{ detail.nextLevelScore - detail.score }} 分
                  </span>
                  <span v-else>已达最高等级，距满分 {{ detail.toMaxScore }} 分</span>
                </div>
              </div>
            </div>

            <div class="score-card__stat">
              <div class="stat-item">
                <span class="stat-item__num">{{ detail.stat.total }}</span>
                <span class="stat-item__label">收到评价</span>
              </div>
              <div class="stat-item">
                <span class="stat-item__num">{{ detail.stat.goodRate }}%</span>
                <span class="stat-item__label">好评率</span>
              </div>
              <div class="stat-item">
                <span class="stat-item__num">{{ detail.stat.avgStars }}</span>
                <span class="stat-item__label">平均星级</span>
              </div>
              <div class="stat-item">
                <span class="stat-item__num">{{ detail.stat.badCount }}</span>
                <span class="stat-item__label">差评数</span>
              </div>
            </div>
          </div>

          <!-- 调整表单 -->
          <div class="page-card">
            <div class="toolbar">
              <span class="toolbar__title">调整信誉分</span>
              <el-tag v-if="isReadOnly" type="warning" size="small" effect="plain">客服只读</el-tag>
            </div>

            <el-alert
              class="tip"
              type="info"
              :closable="false"
              show-icon
              title="调整会写入信誉分流水，用户可在小程序查看变动明细；单次最多 ±100 分，分数始终限制在 0-150。"
            />

            <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
              <el-form-item label="调整方向" prop="direction">
                <el-radio-group v-model="form.direction" :disabled="isReadOnly">
                  <el-radio-button value="add">加分</el-radio-button>
                  <el-radio-button value="minus">减分</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="调整分值" prop="points">
                <el-input-number
                  v-model="form.points"
                  :min="1"
                  :max="100"
                  :disabled="isReadOnly"
                  controls-position="right"
                />
                <span class="form-hint">调整后预计：{{ previewScore }} 分</span>
              </el-form-item>
              <el-form-item label="调整原因" prop="reason">
                <el-input
                  v-model.trim="form.reason"
                  type="textarea"
                  :rows="3"
                  maxlength="200"
                  show-word-limit
                  placeholder="请填写具体原因，例如：举报核实存在虚假描述，扣除 10 分"
                  :disabled="isReadOnly"
                />
              </el-form-item>
              <el-form-item>
                <el-button
                  v-permission="'admin'"
                  type="primary"
                  :loading="submitting"
                  @click="handleAdjust"
                >
                  确认调整
                </el-button>
                <el-button :disabled="isReadOnly" @click="resetForm">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 调整历史 -->
          <div class="page-card">
            <div class="toolbar">
              <span class="toolbar__title">调整历史</span>
              <el-button link type="primary" :icon="Refresh" @click="loadLogs">刷新</el-button>
            </div>
            <el-table v-loading="logLoading" :data="logs" size="small" border>
              <el-table-column label="时间" prop="createTime" width="160" align="center" />
              <el-table-column label="变动" width="86" align="center">
                <template #default="{ row }">
                  <span :class="row.changeScore >= 0 ? 'delta--up' : 'delta--down'">
                    {{ row.changeScore > 0 ? '+' : '' }}{{ row.changeScore }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="变动前后" width="120" align="center">
                <template #default="{ row }">{{ row.beforeScore }} → {{ row.afterScore }}</template>
              </el-table-column>
              <el-table-column label="原因" prop="reason" min-width="220" show-overflow-tooltip />
              <el-table-column label="操作人" width="110" align="center">
                <template #default="{ row }">{{ row.operatorName || '系统' }}</template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无调整记录" :image-size="70" />
              </template>
            </el-table>
            <div class="pagination">
              <el-pagination
                v-model:current-page="logPageNum"
                :page-size="logPageSize"
                :total="logTotal"
                layout="total, prev, pager, next"
                small
                @current-change="loadLogs"
              />
            </div>
          </div>
        </template>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { adjustCredit, getCreditDetail, pageCreditLogs, pageCreditUsers } from '@/api/credit'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isReadOnly = computed(() => userStore.isReadOnly)

const keyword = ref('')
const users = ref([])
const userTotal = ref(0)
const userPageNum = ref(1)
const userPageSize = 10
const userLoading = ref(false)

const current = ref(null)
const detail = ref({})
const detailLoading = ref(false)

const logs = ref([])
const logTotal = ref(0)
const logPageNum = ref(1)
const logPageSize = 10
const logLoading = ref(false)

const formRef = ref()
const submitting = ref(false)
const form = reactive({
  direction: 'add',
  points: 5,
  reason: ''
})

const rules = {
  points: [{ required: true, message: '请填写调整分值', trigger: 'blur' }],
  reason: [{ required: true, message: '调整原因不能为空', trigger: 'blur' }]
}

/**
 * 调整后预计分数：与后端 clamp(0, 150) 保持一致的算法，
 * 让管理员在提交前就能看到「149 分再 +5 只会到 150」而不是提交后才发现。
 */
const previewScore = computed(() => {
  const base = detail.value?.score ?? 0
  const delta = form.direction === 'add' ? form.points : -form.points
  return Math.min(150, Math.max(0, base + delta))
})

const loadUsers = async () => {
  userLoading.value = true
  try {
    const data = await pageCreditUsers({
      keyword: keyword.value || undefined,
      pageNum: userPageNum.value,
      pageSize: userPageSize
    })
    users.value = data.records || []
    userTotal.value = data.total || 0
  } finally {
    userLoading.value = false
  }
}

const searchUsers = () => {
  userPageNum.value = 1
  loadUsers()
}

const selectUser = async row => {
  current.value = row
  logPageNum.value = 1
  await Promise.all([loadDetail(row.userId), loadLogs()])
}

const loadDetail = async userId => {
  detailLoading.value = true
  try {
    detail.value = await getCreditDetail(userId)
  } finally {
    detailLoading.value = false
  }
}

const loadLogs = async () => {
  if (!current.value) {
    return
  }
  logLoading.value = true
  try {
    // 只看管理员调整，评价加减分属于业务流水，不混进人工干预记录
    const data = await pageCreditLogs(current.value.userId, {
      type: 3,
      pageNum: logPageNum.value,
      pageSize: logPageSize
    })
    logs.value = data.records || []
    logTotal.value = data.total || 0
  } finally {
    logLoading.value = false
  }
}

const resetForm = () => {
  form.direction = 'add'
  form.points = 5
  form.reason = ''
  formRef.value?.clearValidate()
}

const handleAdjust = async () => {
  await formRef.value.validate()
  const changeScore = form.direction === 'add' ? form.points : -form.points
  submitting.value = true
  try {
    await adjustCredit(current.value.userId, { changeScore, reason: form.reason })
    ElMessage.success(`已${changeScore > 0 ? '加' : '扣'} ${Math.abs(changeScore)} 分`)
    resetForm()
    await Promise.all([loadDetail(current.value.userId), loadLogs(), loadUsers()])
    const latest = users.value.find(item => item.userId === current.value.userId)
    if (latest) {
      current.value = latest
    }
  } finally {
    submitting.value = false
  }
}

onMounted(loadUsers)
</script>

<style scoped>
.credit-side__search {
  margin-bottom: 10px;
}

.toolbar__meta {
  font-size: 13px;
  color: #909399;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.user-cell__info {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.user-cell__name {
  font-size: 13px;
  color: #303133;
}

.user-cell__sub {
  font-size: 11px;
  color: #a8abb2;
}

.score-num {
  font-weight: 600;
  color: #303133;
}

.level-tag {
  border: none;
  color: #fff;
}

.level-tag--lg {
  padding: 0 14px;
  height: 28px;
  font-size: 13px;
}

.empty-tip {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.score-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.score-card__user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.score-card__name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.score-card__id {
  font-size: 12px;
  color: #909399;
}

.score-card__body {
  display: flex;
  align-items: center;
  gap: 24px;
  margin: 16px 0 12px;
}

.score-card__num {
  font-size: 44px;
  font-weight: 700;
  line-height: 1;
}

.score-card__unit {
  font-size: 14px;
  font-weight: 400;
  color: #909399;
}

.score-card__progress {
  flex: 1;
}

.score-card__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.score-card__stat {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-item__num {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.stat-item__label {
  font-size: 12px;
  color: #909399;
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

/* 信誉分不是股票行情：加分用绿色、扣分用红色，符合"好/坏"的通用语义 */
.delta--up {
  color: #67c23a;
  font-weight: 600;
}

.delta--down {
  color: #f56c6c;
  font-weight: 600;
}
</style>
