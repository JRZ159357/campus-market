<!-- 文件路径: admin-web/src/views/report/index.vue -->
<template>
  <div class="page-container">
    <!-- 统计卡片：待处理数放最前且用红色，因为它是客服唯一需要主动盯的指标 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__label">待处理举报</div>
          <div class="stat-card__value stat-card__value--danger">{{ stats.pendingCount || 0 }}</div>
          <div class="stat-card__hint">处理中 {{ stats.processingCount || 0 }} 条</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__label">今日新增</div>
          <div class="stat-card__value">{{ stats.todayCount || 0 }}</div>
          <div class="stat-card__hint">今日已处理 {{ stats.todayHandledCount || 0 }} 条</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__label">处理率</div>
          <div class="stat-card__value stat-card__value--success">{{ rateText }}</div>
          <div class="stat-card__hint">
            已处理 {{ stats.handledCount || 0 }} · 已驳回 {{ stats.rejectedCount || 0 }}
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__label">平均处理时长</div>
          <div class="stat-card__value">{{ hoursText }}</div>
          <div class="stat-card__hint">从接单算到处理完成</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane name="all">全部</el-tab-pane>
        <el-tab-pane name="0">
          待处理
          <el-badge v-if="stats.pendingCount" :value="stats.pendingCount" class="tab-badge" />
        </el-tab-pane>
        <el-tab-pane name="1">处理中</el-tab-pane>
        <el-tab-pane name="2">已处理</el-tab-pane>
        <el-tab-pane name="3">已驳回</el-tab-pane>
      </el-tabs>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="举报类型">
          <el-select v-model="query.reportType" placeholder="全部" clearable style="width: 120px" @change="handleSearch">
            <el-option v-for="t in REPORT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            placeholder="举报人 / 被举报人 / 举报内容"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="提交时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="records" style="width: 100%" border>
        <el-table-column prop="id" label="ID" width="64" />

        <el-table-column label="类型" width="88">
          <template #default="{ row }">
            <el-tag :type="reportTypeTag(row.reportType)" effect="dark" size="small">
              {{ reportTypeLabel(row.reportType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="被举报对象" min-width="180">
          <template #default="{ row }">
            <div class="cell-target">
              <el-image
                v-if="row.targetCover"
                :src="row.targetCover"
                fit="cover"
                class="cell-target__img"
                :preview-src-list="[row.targetCover]"
                preview-teleported
              />
              <el-avatar v-else :size="34" class="cell-target__avatar">
                {{ (row.targetTitle || '?').slice(0, 1) }}
              </el-avatar>
              <div class="cell-target__body">
                <div class="cell-target__title">{{ row.targetTitle || '（对象已不存在）' }}</div>
                <div class="cell-target__sub">
                  被举报人：{{ row.targetUserName || '—' }}
                  <el-tag v-if="row.targetUserStatus === '已封禁'" size="small" type="danger" effect="plain">
                    已封禁
                  </el-tag>
                  <el-tag v-else-if="row.targetUserStatus === '账号已注销'" size="small" type="info" effect="plain">
                    已注销
                  </el-tag>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="举报人" width="104">
          <template #default="{ row }">
            <span>{{ row.reporterName || '已注销用户' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="原因" width="92">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.reasonLabel }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="content" label="举报描述" min-width="170" show-overflow-tooltip />

        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="reportStatusTag(row.status)" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="提交时间" width="132">
          <template #default="{ row }">
            <div class="cell-time">{{ shortTime(row.createTime) }}</div>
            <div class="cell-time cell-time--sub">{{ agoText(row.hoursAgo) }}</div>
          </template>
        </el-table-column>

        <el-table-column label="处理人" width="116">
          <template #default="{ row }">
            <template v-if="row.handlerName">
              <span>{{ row.handlerName }}</span>
              <el-tag v-if="row.acceptedByMe" size="small" type="primary" effect="plain" class="cell-mine">
                我接的
              </el-tag>
            </template>
            <span v-else class="cell-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <!-- 接单只在待处理时出现：状态由服务端推进，
                 这里再按状态控制按钮，是为了让客服一眼看出哪些单能接 -->
            <el-button v-if="row.status === 0" link type="warning" size="small" @click="onAccept(row)">
              接单
            </el-button>
            <el-button v-if="row.status === 1" link type="danger" size="small" @click="openDetail(row)">
              处理
            </el-button>
            <!-- 管理员可对任意未终结的举报直接改判（客服不行：
                 已处理/已驳回是终态，只能由管理员在必要时走申诉流程） -->
            <el-button
              v-if="row.status === 0 && isAdmin"
              link
              type="primary"
              size="small"
              @click="openDetail(row)"
            >
              直接处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pager"
        @current-change="loadData"
        @size-change="handleSearch"
      />
    </el-card>

    <!-- ============ 举报详情 + 处理弹窗 ============ -->
    <el-dialog v-model="detailVisible" title="举报详情" width="900px" top="5vh" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detail">
          <!-- 处理记录时间线：让客服一眼看到这条单经过了哪些人 -->
          <el-timeline class="timeline">
            <el-timeline-item :timestamp="detail.createTime" type="primary" placement="top">
              <div class="timeline__title">用户提交举报</div>
              <div class="timeline__sub">
                举报人：{{ detail.reporterName || '已注销用户' }} ·
                原因：{{ detail.reasonLabel }}
              </div>
            </el-timeline-item>
            <el-timeline-item
              v-if="detail.acceptTime"
              :timestamp="detail.acceptTime"
              type="warning"
              placement="top"
            >
              <div class="timeline__title">客服接单</div>
              <div class="timeline__sub">处理人：{{ detail.handlerName || '—' }}</div>
            </el-timeline-item>
            <el-timeline-item
              v-if="detail.handleTime"
              :timestamp="detail.handleTime"
              :type="detail.status === 2 ? 'danger' : 'info'"
              placement="top"
            >
              <div class="timeline__title">
                {{ detail.status === 2 ? '处理完成（举报成立）' : '处理完成（举报不成立）' }}
              </div>
              <div class="timeline__sub">
                执行动作：{{ detail.handleActionLabel }} · 耗时 {{ hoursTextOf(detail.handleHours) }}
              </div>
              <div v-if="detail.handleResult" class="timeline__result">{{ detail.handleResult }}</div>
            </el-timeline-item>
            <el-timeline-item v-if="!detail.handleTime" type="info" hollow placement="top">
              <div class="timeline__title timeline__title--muted">等待处理</div>
            </el-timeline-item>
          </el-timeline>

          <!-- 被举报对象快照：灰底框突出显示。
               快照是「举报当时的证据」，即使原对象已被下架/删除/改名，这里仍可查 -->
          <el-divider content-position="left">
            被举报对象快照
            <el-tag v-if="detail.targetMissing" size="small" type="warning" effect="plain" class="divider-tag">
              快照缺失，仅能参考当前信息
            </el-tag>
          </el-divider>
          <div class="snapshot">
            <el-row :gutter="16">
              <el-col :span="6">
                <el-image
                  v-if="detail.targetCover"
                  :src="detail.targetCover"
                  fit="cover"
                  class="snapshot__cover"
                  :preview-src-list="[detail.targetCover]"
                  preview-teleported
                />
                <div v-else class="snapshot__cover snapshot__cover--empty">无图</div>
              </el-col>
              <el-col :span="18">
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="举报类型">{{ detail.reportTypeLabel }}</el-descriptions-item>
                  <el-descriptions-item label="对象标题">{{ detail.targetTitle || '—' }}</el-descriptions-item>
                  <template v-if="detail.reportType === 1">
                    <el-descriptions-item label="商品价格">
                      ¥{{ priceOf(detail.snapshot.price) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="举报时状态">{{ detail.snapshot.statusLabel }}</el-descriptions-item>
                    <el-descriptions-item label="商品描述" :span="2">
                      {{ detail.snapshot.description || '—' }}
                    </el-descriptions-item>
                  </template>
                  <template v-else-if="detail.reportType === 2">
                    <el-descriptions-item label="身份角色">{{ detail.snapshot.roleLabel }}</el-descriptions-item>
                    <el-descriptions-item label="学生认证">
                      {{ detail.snapshot.studentVerified === 1 ? '已认证' : '未认证' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="举报时信誉分">
                      {{ detail.snapshot.creditScore }}
                    </el-descriptions-item>
                    <el-descriptions-item label="当前状态">{{ detail.targetUserStatus || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="个人简介" :span="2">
                      {{ detail.snapshot.intro || '—' }}
                    </el-descriptions-item>
                  </template>
                  <template v-else>
                    <el-descriptions-item label="星级">
                      <el-rate :model-value="detail.snapshot.stars" disabled allow-half />
                    </el-descriptions-item>
                    <el-descriptions-item label="关联订单">{{ detail.snapshot.orderNo || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="评价内容" :span="2">
                      {{ detail.snapshot.content || '—' }}
                    </el-descriptions-item>
                  </template>
                </el-descriptions>
              </el-col>
            </el-row>
            <div v-if="detail.reportType === 3 && reviewImages.length" class="snapshot__review-imgs">
              <el-image
                v-for="(img, i) in reviewImages"
                :key="i"
                :src="img"
                fit="cover"
                class="snapshot__review-img"
                :preview-src-list="[img]"
                preview-teleported
              />
            </div>
          </div>

          <el-divider content-position="left">举报信息</el-divider>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="举报人">{{ detail.reporterName || '已注销用户' }}</el-descriptions-item>
            <el-descriptions-item label="举报时间">{{ detail.createTime }}</el-descriptions-item>
            <el-descriptions-item label="举报原因">{{ detail.reasonLabel }}</el-descriptions-item>
            <el-descriptions-item label="被举报人">
              {{ detail.targetUserName || '—' }}
              <span v-if="detail.targetUserStatus" class="cell-muted">（{{ detail.targetUserStatus }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="详细描述" :span="2">
              <div class="content-block">{{ detail.content }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="证据图片" :span="2">
              <div v-if="detail.images && detail.images.length" class="evidence">
                <el-image
                  v-for="(img, i) in detail.images"
                  :key="i"
                  :src="img"
                  fit="cover"
                  class="evidence__img"
                  :preview-src-list="detail.images"
                  :initial-index="i"
                  preview-teleported
                />
              </div>
              <span v-else class="cell-muted">未上传</span>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 处理区：只在未终结时显示 -->
          <template v-if="detail.status === 0 || detail.status === 1">
            <el-divider content-position="left">
              处理区
              <el-tag v-if="detail.status === 0" size="small" type="danger" effect="plain" class="divider-tag">
                待处理：请先接单或由管理员直接处理
              </el-tag>
              <el-tag v-else size="small" type="warning" effect="plain" class="divider-tag">
                处理中
              </el-tag>
            </el-divider>

            <el-form ref="handleRef" :model="handleForm" :rules="handleRules" label-width="96px">
              <el-form-item label="处理结论" prop="status">
                <el-radio-group v-model="handleForm.status">
                  <el-radio :value="2">举报成立（已处理）</el-radio>
                  <el-radio :value="3">举报不成立（已驳回）</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="handleForm.status === 2" label="处理动作" prop="handleAction">
                <el-select v-model="handleForm.handleAction" style="width: 100%" :disabled="actionOptions.length === 1">
                  <el-option v-for="a in actionOptions" :key="a.value" :label="a.label" :value="a.value">
                    <div class="action-option">
                      <span class="action-option__label">{{ a.label }}</span>
                      <span class="action-option__desc">{{ a.desc }}</span>
                    </div>
                  </el-option>
                </el-select>
                <div v-if="actionOptions.length === 1" class="form-tip">
                  该举报类型只能选择「不处理」，若需处罚请在「我的举报」之外走其他管理入口。
                </div>
              </el-form-item>
              <el-form-item label="处理结果" prop="handleResult">
                <el-input
                  v-model.trim="handleForm.handleResult"
                  type="textarea"
                  :rows="4"
                  maxlength="500"
                  show-word-limit
                  placeholder="请说明核实过程与结论，这段文字会同时展示给举报人与被举报人"
                />
              </el-form-item>
            </el-form>
          </template>

          <el-alert
            v-else-if="detail.status === 2 || detail.status === 3"
            :title="detail.status === 2 ? '举报成立，已完成处置' : '举报不成立，已驳回'"
            :type="detail.status === 2 ? 'error' : 'info'"
            :description="detail.handleResult"
            show-icon
            :closable="false"
          />
        </template>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="detail && (detail.status === 0 || detail.status === 1)">
          <el-button v-if="detail.status === 0" type="warning" @click="onAccept(detail)">
            接单
          </el-button>
          <el-button type="danger" :loading="handling" @click="onHandle">提交处理</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageReports, getReportDetail, acceptReport, handleReport, reportStats,
  REPORT_TYPES, reportTypeLabel, reportTypeTag, reportStatusTag, actionsOfType, defaultActionOfType
} from '@/api/report'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

const loading = ref(false)
const records = ref([])
const total = ref(0)
const stats = ref({})
const activeTab = ref('all')
const dateRange = ref([])

const query = reactive({ pageNum: 1, pageSize: 10, reportType: null, keyword: '', status: null })

const loadStats = async () => {
  try {
    stats.value = (await reportStats()) || {}
  } catch (e) {
    // 统计失败不影响列表主流程
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const params = { ...query }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }
    const data = await pageReports(params)
    records.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) {
    ElMessage.error(e.message || '举报列表加载失败')
  } finally {
    loading.value = false
  }
}

const onTabChange = name => {
  query.status = name === 'all' ? null : Number(name)
  query.pageNum = 1
  loadData()
}

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.pageNum = 1
  query.reportType = null
  query.keyword = ''
  query.status = null
  dateRange.value = []
  activeTab.value = 'all'
  loadData()
}

const onAccept = async row => {
  try {
    await ElMessageBox.confirm(
      `接单后该举报将标记为「处理中」，其他人将不能再接。确认接下举报 #${row.id}？`,
      '接单确认',
      { type: 'warning', confirmButtonText: '确认接单' }
    )
  } catch (e) {
    return
  }
  try {
    await acceptReport(row.id)
    ElMessage.success('接单成功')
    detailVisible.value && (await openDetail(row))
    loadData()
    loadStats()
  } catch (e) {
    ElMessage.error(e.message || '接单失败')
  }
}

// ---------------- 详情与处理 ----------------
const detailVisible = ref(false)
const detailLoading = ref(false)
const handling = ref(false)
const detail = ref(null)
const handleRef = ref(null)

const handleForm = reactive({ status: 2, handleAction: 0, handleResult: '' })
const handleRules = {
  status: [{ required: true, message: '请选择处理结论', trigger: 'change' }],
  handleAction: [{ required: true, message: '请选择处理动作', trigger: 'change' }],
  handleResult: [
    { required: true, message: '请填写处理结果', trigger: 'blur' },
    { min: 5, message: '处理结果至少5字，让举报人和被举报人都能看懂结论', trigger: 'blur' }
  ]
}

/**
 * 可选处理动作：按举报类型收窄 + 按「结论是否为驳回」过滤。
 * 驳回时只留「不处理」——服务端也会拦，这里先拦一层是为了让用户
 * 在提交前就看到「驳回不能带处罚」，而不是提交后才报错。
 */
const actionOptions = computed(() => {
  if (!detail.value) return []
  const all = actionsOfType(detail.value.reportType)
  if (handleForm.status === 3) return all.filter(a => a.value === 0)
  return all
})

/**
 * 评价配图。
 * 服务端已经把快照里的 images 从 JSON 数组字符串解析成了数组，
 * 这里只做一层兜底：老数据可能还是字符串，split 一下也能用。
 */
const reviewImages = computed(() => {
  const imgs = detail.value?.snapshot?.images
  if (!imgs) return []
  if (Array.isArray(imgs)) return imgs.filter(Boolean)
  return String(imgs).split(',').map(s => s.trim()).filter(Boolean)
})

// 切换举报类型或结论时，纠正出一个合法的默认动作
watch([() => detail.value?.reportType, () => handleForm.status], () => {
  const options = actionOptions.value
  if (!options.length) return
  if (!options.some(o => o.value === handleForm.handleAction)) {
    // 切换到「举报不成立」时只剩「不处理」一个选项；
    // 切回「成立」时恢复该类型的典型处罚，而不是笼统的 options[0]
    handleForm.handleAction = handleForm.status === 3
      ? 0
      : defaultActionOfType(detail.value.reportType)
  }
})

const openDetail = async row => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getReportDetail(row.id)
    // 默认结论：举报成立。多数举报确实是成立的，
    // 让客服主动去点「不成立」比默认不成立更符合真实分布。
    handleForm.status = 2
    // 默认动作要与「举报成立」自洽：结论选了成立，动作却停在「不处理」，
    // 提交出去就是一条「已处理但没做任何处置」的举报
    handleForm.handleAction = defaultActionOfType(detail.value.reportType)
    handleForm.handleResult = ''
    handleRef.value?.clearValidate()
  } catch (e) {
    ElMessage.error(e.message || '举报详情加载失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const onHandle = async () => {
  try {
    await handleRef.value.validate()
  } catch (e) {
    return
  }
  try {
    await ElMessageBox.confirm(
      handleForm.status === 2
        ? '将执行处置动作并给举报人与被举报人同时发送通知，确认提交？'
        : '将把该举报标记为「已驳回」并通知举报人与被举报人，确认提交？',
      '处理确认',
      { type: 'warning', confirmButtonText: '确认提交' }
    )
  } catch (e) {
    return
  }
  handling.value = true
  try {
    await handleReport(detail.value.id, {
      status: handleForm.status,
      handleAction: handleForm.status === 3 ? 0 : handleForm.handleAction,
      handleResult: handleForm.handleResult
    })
    ElMessage.success('处理完成，通知已发送')
    detailVisible.value = false
    loadData()
    loadStats()
  } catch (e) {
    ElMessage.error(e.message || '处理失败')
  } finally {
    handling.value = false
  }
}

// ---------------- 展示辅助 ----------------
const shortTime = value => (value ? String(value).slice(5, 16) : '')
const priceOf = value => Number(value || 0).toFixed(2)

const agoText = hours => {
  const h = Number(hours || 0)
  if (h < 1) return '刚刚提交'
  if (h < 24) return `${h} 小时前`
  return `${Math.floor(h / 24)} 天前`
}

const hoursTextOf = value => {
  const h = Number(value || 0)
  if (h < 1) return `${Math.round(h * 60)} 分钟`
  if (h < 24) return `${h.toFixed(1)} 小时`
  return `${(h / 24).toFixed(1)} 天`
}

const rateText = computed(() => `${Math.round((stats.value.handleRate || 0) * 100)}%`)
const hoursText = computed(() => hoursTextOf(stats.value.avgHandleHours))

onMounted(() => {
  loadData()
  loadStats()
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 12px;
}

.stat-card :deep(.el-card__body) {
  padding: 14px 18px;
}

.stat-card__label {
  font-size: 13px;
  color: #909399;
}

.stat-card__value {
  margin: 4px 0 2px;
  font-size: 26px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-card__value--danger {
  color: #f56c6c;
}

.stat-card__value--success {
  color: #67c23a;
}

.stat-card__hint {
  font-size: 12px;
  color: #a8abb2;
}

.tab-badge {
  margin-left: 4px;
  vertical-align: top;
}

.filter-form {
  margin-bottom: 4px;
}

/* 被举报对象单元格 */
.cell-target {
  display: flex;
  gap: 8px;
  align-items: center;
}

.cell-target__img,
.cell-target__avatar {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 6px;
}

.cell-target__avatar {
  background: linear-gradient(135deg, #909399, #c0c4cc);
  color: #fff;
}

.cell-target__img {
  /* 缩略图可能是透明图、也可能加载失败。垫一层浅灰底，
     否则这一格在表格里看起来像「压根没渲染」——排查成本远高于一行背景色 */
  background: #f2f4f7;
}

.cell-target__body {
  min-width: 0;
}

.cell-target__title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-target__sub {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

.cell-time {
  font-size: 13px;
  color: #606266;
}

.cell-time--sub {
  margin-top: 2px;
  font-size: 11px;
  color: #a8abb2;
}

.cell-muted {
  color: #c0c4cc;
}

/* 「我接的」标记：与处理人名字之间留一点间距，否则会挤在一起 */
.cell-mine {
  margin-left: 6px;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

/* 详情弹窗 */
.timeline {
  margin-bottom: 4px;
  padding-left: 4px;
}

.timeline__title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.timeline__title--muted {
  color: #a8abb2;
  font-weight: 400;
}

.timeline__sub {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

.timeline__result {
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f4f4f5;
  font-size: 12px;
  color: #606266;
  white-space: pre-wrap;
}

.divider-tag {
  margin-left: 8px;
}

/* 快照区：灰底突出，这是客服判罚的核心证据 */
.snapshot {
  padding: 14px;
  border-radius: 8px;
  background: #f5f6f7;
}

.snapshot__cover {
  width: 100%;
  height: 110px;
  border-radius: 8px;
  background: #fff;
}

.snapshot__cover--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #c0c4cc;
}

.snapshot__review-imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.snapshot__review-img {
  width: 90px;
  height: 90px;
  border-radius: 6px;
  cursor: zoom-in;
}

.content-block {
  max-height: 160px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.evidence__img {
  width: 80px;
  height: 80px;
  border-radius: 6px;
  cursor: zoom-in;
}

/* 处理动作下拉：把后果直接写在选项旁边 */
.action-option {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
  padding: 2px 0;
}

.action-option__label {
  font-size: 13px;
}

.action-option__desc {
  font-size: 11px;
  color: #a8abb2;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #e6a23c;
  line-height: 1.5;
}
</style>