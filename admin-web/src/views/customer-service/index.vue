<!-- 文件路径: admin-web/src/views/customer-service/index.vue -->
<template>
  <div class="page-container">
    <!-- 客服只读提示：聊天监控部分确实只读，但举报处理是客服的本职工作，
         所以提示语必须说清楚「只读」指的是哪一部分，否则客服会以为自己什么都做不了 -->
    <el-alert
      v-if="isReadOnly"
      class="readonly-tip"
      type="warning"
      show-icon
      :closable="false"
      title="聊天记录为只读监控"
      description="本页面左侧的聊天记录只能查看，客服不能主动向用户发送消息；需要介入纠纷请使用「订单管理」中的强制取消 / 强制退款。右侧「待处理举报」可以正常接单与处理。"
    />

    <!-- 举报统计卡片：待处理数用红色，它是客服唯一需要主动盯的指标 -->
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

    <el-tabs v-model="activeTab" class="work-tabs" @tab-change="onTabChange">
      <!-- ============ 举报处理（客服可写） ============ -->
      <el-tab-pane name="report">
        <template #label>
          <span class="tab-label">
            举报处理
            <el-badge v-if="stats.pendingCount" :value="stats.pendingCount" class="tab-badge" />
          </span>
        </template>

        <div class="rp-layout">
          <!-- 左：待处理举报列表 -->
          <aside class="rp-side">
            <div class="rp-side__head">
              <el-select v-model="query.reportType" placeholder="全部类型" clearable size="small" @change="loadReports">
                <el-option v-for="t in REPORT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
              <el-select v-model="query.status" placeholder="全部状态" clearable size="small" @change="loadReports">
                <el-option label="待处理" :value="0" />
                <el-option label="处理中" :value="1" />
                <el-option label="已处理" :value="2" />
                <el-option label="已驳回" :value="3" />
              </el-select>
            </div>

            <div v-loading="loading" class="rp-side__list">
              <div
                v-for="item in reports"
                :key="item.id"
                class="rp-item"
                :class="{
                  'rp-item--active': currentReport && currentReport.id === item.id,
                  'rp-item--pending': item.status === 0
                }"
                @click="openReport(item)"
              >
                <div class="rp-item__row">
                  <el-tag :type="reportTypeTag(item.reportType)" size="small" effect="dark">
                    {{ item.targetTitle || reportTypeLabel(item.reportType) }}
                  </el-tag>
                  <el-tag :type="reportStatusTag(item.status)" size="small" effect="plain">
                    {{ item.statusLabel }}
                  </el-tag>
                </div>
                <div class="rp-item__content">{{ item.content }}</div>
                <div class="rp-item__meta">
                  #{{ item.id }} · {{ item.reporterName }} · {{ shortTime(item.createTime) }}
                </div>
                <el-button
                  v-if="item.status === 0"
                  size="mini"
                  type="warning"
                  class="rp-item__accept"
                  :loading="accepting === item.id"
                  @click.stop="onAccept(item)"
                >
                  接单
                </el-button>
              </div>

              <el-empty v-if="!loading && reports.length === 0" description="暂无举报" :image-size="70" />
            </div>

            <div v-if="total > query.pageSize" class="rp-side__page">
              <el-pagination
                v-model:current-page="query.pageNum"
                :page-size="query.pageSize"
                :total="total"
                layout="prev, pager, next"
                small
                background
                @current-change="loadReports"
              />
            </div>
          </aside>

          <!-- 右：选中举报的详情与处理表单 -->
          <main class="rp-main">
            <div v-if="!currentReport" class="rp-main__empty">
              <el-empty description="从左侧选择一条举报开始处理" :image-size="100" />
            </div>

            <template v-else>
              <header class="rp-head">
                <div class="rp-head__title">
                  举报 #{{ currentReport.id }}
                  <el-tag :type="reportTypeTag(currentReport.reportType)" size="small" effect="dark">
                    {{ currentReport.reportTypeLabel }}
                  </el-tag>
                  <el-tag :type="reportStatusTag(currentReport.status)" size="small">
                    {{ currentReport.statusLabel }}
                  </el-tag>
                </div>
                <div class="rp-head__meta">
                  举报人：{{ currentReport.reporterName }} ·
                  提交于 {{ currentReport.createTime }}
                  <template v-if="currentReport.acceptTime">
                    · 接单于 {{ currentReport.acceptTime }}
                  </template>
                </div>
              </header>

              <div class="rp-body">
                <!-- 快照：灰底突出，客服判罚的核心证据 -->
                <div class="snapshot">
                  <div class="snapshot__label">
                    被举报对象快照
                    <el-tag v-if="currentReport.targetMissing" size="small" type="warning" effect="plain">
                      快照缺失
                    </el-tag>
                  </div>
                  <div class="snapshot__main">
                    <el-image
                      v-if="currentReport.targetCover"
                      :src="currentReport.targetCover"
                      fit="cover"
                      class="snapshot__cover"
                      :preview-src-list="[currentReport.targetCover]"
                      preview-teleported
                    />
                    <div class="snapshot__body">
                      <div class="snapshot__title">{{ currentReport.targetTitle || '（对象已不存在）' }}</div>
                      <div class="snapshot__meta">
                        被举报人：{{ currentReport.targetUserName || '—' }}
                        <el-tag v-if="currentReport.targetUserStatus === '已封禁'" size="small" type="danger" effect="plain">
                          已封禁
                        </el-tag>
                      </div>
                      <div v-if="currentReport.reportType === 1" class="snapshot__desc">
                        ¥{{ priceOf(currentReport.snapshot.price) }} · {{ currentReport.snapshot.statusLabel }}
                      </div>
                      <div v-else-if="currentReport.reportType === 3" class="snapshot__desc">
                        {{ currentReport.snapshot.content }}
                      </div>
                      <div v-else class="snapshot__desc">
                        {{ currentReport.snapshot.roleLabel }} · 信誉分 {{ currentReport.snapshot.creditScore }}
                      </div>
                    </div>
                  </div>
                </div>

                <el-descriptions :column="1" border size="small" class="rp-info">
                  <el-descriptions-item label="举报原因">{{ currentReport.reasonLabel }}</el-descriptions-item>
                  <el-descriptions-item label="详细描述">{{ currentReport.content }}</el-descriptions-item>
                  <el-descriptions-item v-if="currentReport.images && currentReport.images.length" label="证据图片">
                    <el-image
                      v-for="(img, i) in currentReport.images"
                      :key="i"
                      :src="img"
                      fit="cover"
                      class="rp-evidence"
                      :preview-src-list="currentReport.images"
                      :initial-index="i"
                      preview-teleported
                    />
                  </el-descriptions-item>
                  <el-descriptions-item v-if="currentReport.handleResult" label="已处理">
                    {{ currentReport.handleResult }}
                  </el-descriptions-item>
                </el-descriptions>

                <!-- 处理表单 -->
                <template v-if="currentReport.status === 0 || currentReport.status === 1">
                  <el-form ref="handleRef" :model="handleForm" :rules="handleRules" label-width="88px" class="rp-form">
                    <el-form-item label="处理结论" prop="status">
                      <el-radio-group v-model="handleForm.status">
                        <el-radio :value="2">举报成立</el-radio>
                        <el-radio :value="3">举报不成立</el-radio>
                      </el-radio-group>
                    </el-form-item>
                    <el-form-item v-if="handleForm.status === 2" label="处理动作" prop="handleAction">
                      <el-select v-model="handleForm.handleAction" style="width: 100%">
                        <el-option v-for="a in actionOptions" :key="a.value" :label="a.label" :value="a.value">
                          <div class="action-option">
                            <span class="action-option__label">{{ a.label }}</span>
                            <span class="action-option__desc">{{ a.desc }}</span>
                          </div>
                        </el-option>
                      </el-select>
                    </el-form-item>
                    <el-form-item label="处理结果" prop="handleResult">
                      <el-input
                        v-model.trim="handleForm.handleResult"
                        type="textarea"
                        :rows="4"
                        maxlength="500"
                        show-word-limit
                        placeholder="说明核实过程与结论，这段文字会同时展示给举报人与被举报人"
                      />
                    </el-form-item>
                  </el-form>

                  <div class="rp-actions">
                    <el-button
                      v-if="currentReport.status === 0"
                      type="warning"
                      :loading="accepting === currentReport.id"
                      @click="onAccept(currentReport)"
                    >
                      接单
                    </el-button>
                    <el-button type="danger" :loading="handling" @click="onHandle">提交处理</el-button>
                  </div>
                </template>

                <el-alert
                  v-else
                  :title="currentReport.status === 2 ? '该举报已处理完成' : '该举报已驳回'"
                  :type="currentReport.status === 2 ? 'error' : 'info'"
                  :description="currentReport.handleResult"
                  show-icon
                  :closable="false"
                />
              </div>
            </template>
          </main>
        </div>
      </el-tab-pane>

      <!-- ============ 聊天记录监控（只读） ============ -->
      <el-tab-pane label="聊天监控" name="chat">
        <div class="cs-layout">
          <aside class="cs-side">
            <div class="cs-side__head">
              <el-input
                v-model.trim="query.keyword"
                placeholder="按买家 / 卖家昵称搜索"
                clearable
                :prefix-icon="Search"
                @keyup.enter="handleSearch"
                @clear="handleSearch"
              />
            </div>

            <div v-loading="loading" class="cs-side__list">
              <div
                v-for="item in sessions"
                :key="item.id"
                class="cs-item"
                :class="{ 'cs-item--active': current && current.id === item.id }"
                @click="openSession(item)"
              >
                <el-avatar :size="38" class="cs-item__avatar">
                  {{ (item.buyerNickname || '?').slice(0, 1) }}
                </el-avatar>
                <div class="cs-item__body">
                  <div class="cs-item__row">
                    <span class="cs-item__name">
                      {{ item.buyerNickname }} → {{ item.sellerNickname }}
                    </span>
                    <span class="cs-item__time">{{ shortTime(item.lastTime) }}</span>
                  </div>
                  <div class="cs-item__row">
                    <span class="cs-item__msg">{{ item.lastMessage || '暂无消息' }}</span>
                    <span v-if="unreadOf(item) > 0" class="cs-item__badge">{{ unreadOf(item) }}</span>
                  </div>
                  <div v-if="item.productTitle" class="cs-item__product">
                    关联商品：{{ item.productTitle }}
                  </div>
                </div>
              </div>

              <el-empty v-if="!loading && sessions.length === 0" description="暂无会话" :image-size="80" />
            </div>

            <div v-if="totalSessions > query.pageSize" class="cs-side__page">
              <el-pagination
                v-model:current-page="query.pageNum"
                :page-size="query.pageSize"
                :total="totalSessions"
                layout="prev, pager, next"
                small
                background
                @current-change="loadSessions"
              />
            </div>
          </aside>

          <main v-if="current" class="cs-main">
            <header class="cs-head">
              <div class="cs-head__peers">
                <span class="peer peer--buyer">买家 {{ current.buyerNickname }}</span>
                <el-icon class="peer__arrow"><Right /></el-icon>
                <span class="peer peer--seller">卖家 {{ current.sellerNickname }}</span>
              </div>
              <div class="cs-head__meta">
                <span v-if="current.productTitle" class="cs-head__product">
                  关联商品：{{ current.productTitle }}
                </span>
                <span class="cs-head__count">共 {{ messages.length }} 条消息</span>
              </div>
            </header>

            <div ref="msgBox" v-loading="msgLoading" class="cs-messages">
              <template v-for="msg in messages" :key="msg.id">
                <div v-if="msg.msgType === 3" class="sys-msg">{{ msg.content }}</div>

                <div v-else class="msg-row" :class="msg.fromBuyer ? 'msg-row--left' : 'msg-row--right'">
                  <el-avatar :size="32" class="msg-avatar">
                    {{ (msg.senderNickname || (msg.fromBuyer ? current.buyerNickname : current.sellerNickname) || '?').slice(0, 1) }}
                  </el-avatar>
                  <div class="msg-body">
                    <div class="msg-meta">
                      <span class="msg-who">
                        {{ msg.fromBuyer ? '买家' : '卖家' }}
                        · {{ msg.senderNickname || (msg.fromBuyer ? current.buyerNickname : current.sellerNickname) }}
                      </span>
                      <span class="msg-time">{{ shortTime(msg.createTime) }}</span>
                    </div>
                    <div v-if="msg.msgType === 1" class="bubble" :class="msg.fromBuyer ? 'bubble--buyer' : 'bubble--seller'">
                      {{ msg.content }}
                    </div>
                    <el-image
                      v-else-if="msg.msgType === 2"
                      :src="msg.content"
                      :preview-src-list="[msg.content]"
                      fit="cover"
                      class="bubble-img"
                      preview-teleported
                    />
                    <div v-else-if="msg.msgType === 4" class="card" :class="msg.fromBuyer ? 'card--buyer' : 'card--seller'">
                      <el-image v-if="msg.card && msg.card.cover" :src="msg.card.cover" fit="cover" class="card__img" />
                      <div v-else class="card__img card__img--empty">无图</div>
                      <div class="card__body">
                        <div class="card__title">{{ (msg.card && msg.card.title) || '商品' }}</div>
                        <div class="card__price">¥{{ priceText(msg.card && msg.card.price) }}</div>
                        <el-tag size="small" effect="plain">{{ (msg.card && msg.card.statusLabel) || '—' }}</el-tag>
                      </div>
                    </div>
                    <div v-else-if="msg.msgType === 5" class="card" :class="msg.fromBuyer ? 'card--buyer' : 'card--seller'">
                      <el-image v-if="msg.card && msg.card.cover" :src="msg.card.cover" fit="cover" class="card__img" />
                      <div v-else class="card__img card__img--empty">无图</div>
                      <div class="card__body">
                        <div class="card__title">{{ (msg.card && msg.card.title) || '订单' }}</div>
                        <div class="card__no">{{ (msg.card && msg.card.orderNo) || '' }}</div>
                        <div class="card__price">¥{{ priceText(msg.card && msg.card.price) }}</div>
                        <el-tag size="small" effect="plain">{{ (msg.card && msg.card.statusLabel) || '—' }}</el-tag>
                      </div>
                    </div>
                    <div v-else class="bubble bubble--plain">{{ msg.content }}</div>
                  </div>
                </div>
              </template>

              <el-empty v-if="!msgLoading && messages.length === 0" description="该会话还没有消息" :image-size="80" />
            </div>

            <footer class="cs-foot">
              <el-icon><Lock /></el-icon>
              <span>只读监控：客服不能主动发送消息，如需介入纠纷请前往「订单管理」</span>
            </footer>
          </main>

          <main v-else class="cs-main cs-main--empty">
            <el-empty description="请从左侧选择一个会话查看聊天记录" :image-size="110" />
          </main>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, nextTick, computed, watch } from 'vue'
import { Search, Right, Lock } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageAllSessions, getSessionMessages } from '@/api/chat'
import {
  pageReports, getReportDetail, acceptReport, handleReport, reportStats,
  REPORT_TYPES, reportTypeLabel, reportTypeTag, reportStatusTag, actionsOfType, defaultActionOfType
} from '@/api/report'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isReadOnly = computed(() => userStore.isReadOnly)

const activeTab = ref('report')

// 举报与聊天各有一套查询状态，混用一份 query 会让切 Tab 时互相带着对方的筛选条件
const query = reactive({ pageNum: 1, pageSize: 15, keyword: '', reportType: null, status: null })

const shortTime = value => (value ? String(value).slice(5, 16) : '')
const priceText = value => Number(value || 0).toFixed(2)
const priceOf = value => Number(value || 0).toFixed(2)

const hoursTextOf = value => {
  const h = Number(value || 0)
  if (h < 1) return `${Math.round(h * 60)} 分钟`
  if (h < 24) return `${h.toFixed(1)} 小时`
  return `${(h / 24).toFixed(1)} 天`
}

// ==================== 举报处理 ====================
const reports = ref([])
const total = ref(0)
const loading = ref(false)
const accepting = ref(0)
const handling = ref(false)
const currentReport = ref(null)
const stats = ref({})
const handleRef = ref(null)

const handleForm = reactive({ status: 2, handleAction: 0, handleResult: '' })
const handleRules = {
  status: [{ required: true, message: '请选择处理结论', trigger: 'change' }],
  handleAction: [{ required: true, message: '请选择处理动作', trigger: 'change' }],
  handleResult: [
    { required: true, message: '请填写处理结果', trigger: 'blur' },
    { min: 5, message: '处理结果至少5字', trigger: 'blur' }
  ]
}

const actionOptions = computed(() => {
  if (!currentReport.value) return []
  const all = actionsOfType(currentReport.value.reportType)
  return handleForm.status === 3 ? all.filter(a => a.value === 0) : all
})

watch([() => currentReport.value?.reportType, () => handleForm.status], () => {
  const options = actionOptions.value
  if (options.length && !options.some(o => o.value === handleForm.handleAction)) {
    // 切到「举报不成立」时只剩「不处理」；切回「成立」时恢复该类型的典型处罚
    handleForm.handleAction = handleForm.status === 3
      ? 0
      : defaultActionOfType(currentReport.value.reportType)
  }
})

const loadStats = async () => {
  try {
    stats.value = (await reportStats()) || {}
  } catch (e) {
    // 统计失败不影响主流程
  }
}

const loadReports = async () => {
  loading.value = true
  try {
    const data = await pageReports({ ...query })
    reports.value = data.records || []
    total.value = Number(data.total || 0)
    // 首次进入自动选中第一条，避免右侧一直空白
    if (!currentReport.value && reports.value.length) {
      openReport(reports.value[0])
    }
  } catch (e) {
    ElMessage.error(e.message || '举报列表加载失败')
  } finally {
    loading.value = false
  }
}

const openReport = async item => {
  try {
    currentReport.value = await getReportDetail(item.id)
    handleForm.status = 2
    // 默认动作要与「举报成立」自洽：结论选了成立，动作却停在「不处理」，
    // 提交出去就是一条「已处理但没做任何处置」的举报
    handleForm.handleAction = defaultActionOfType(currentReport.value.reportType)
    handleForm.handleResult = ''
    handleRef.value?.clearValidate()
  } catch (e) {
    ElMessage.error(e.message || '举报详情加载失败')
  }
}

const onAccept = async row => {
  accepting.value = row.id
  try {
    await acceptReport(row.id)
    ElMessage.success('接单成功')
    if (currentReport.value && currentReport.value.id === row.id) {
      currentReport.value = await getReportDetail(row.id)
    }
    loadReports()
    loadStats()
  } catch (e) {
    ElMessage.error(e.message || '接单失败')
  } finally {
    accepting.value = 0
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
        : '将把该举报标记为「已驳回」并通知双方，确认提交？',
      '处理确认',
      { type: 'warning', confirmButtonText: '确认提交' }
    )
  } catch (e) {
    return
  }
  handling.value = true
  try {
    await handleReport(currentReport.value.id, {
      status: handleForm.status,
      handleAction: handleForm.status === 3 ? 0 : handleForm.handleAction,
      handleResult: handleForm.handleResult
    })
    ElMessage.success('处理完成，通知已发送')
    currentReport.value = await getReportDetail(currentReport.value.id)
    loadReports()
    loadStats()
  } catch (e) {
    ElMessage.error(e.message || '处理失败')
  } finally {
    handling.value = false
  }
}

const rateText = computed(() => `${Math.round((stats.value.handleRate || 0) * 100)}%`)
const hoursText = computed(() => hoursTextOf(stats.value.avgHandleHours))

// ==================== 聊天监控（只读） ====================
const sessions = ref([])
const totalSessions = ref(0)
const current = ref(null)
const messages = ref([])
const msgLoading = ref(false)
const msgBox = ref(null)

/** 客服视角的未读 = 双方未读之和（服务端已按此口径返回），表示该会话还有多少条消息没人看 */
const unreadOf = item => Number(item.unread || 0)

const loadSessions = async () => {
  loading.value = true
  try {
    const data = await pageAllSessions({ pageNum: query.pageNum, pageSize: query.pageSize, keyword: query.keyword })
    sessions.value = data.records || []
    totalSessions.value = Number(data.total || 0)
    if (!current.value && sessions.value.length) {
      openSession(sessions.value[0])
    }
  } catch (e) {
    ElMessage.error(e.message || '会话列表加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNum = 1
  current.value = null
  messages.value = []
  loadSessions()
}

const openSession = async item => {
  current.value = item
  msgLoading.value = true
  try {
    const rows = await getSessionMessages(item.id, 200)
    messages.value = rows || []
    await scrollToBottom()
  } catch (e) {
    ElMessage.error(e.message || '聊天记录加载失败')
  } finally {
    msgLoading.value = false
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (msgBox.value) {
    msgBox.value.scrollTop = msgBox.value.scrollHeight
  }
}

const onTabChange = name => {
  if (name === 'report') {
    loadReports()
    loadStats()
  } else {
    loadSessions()
  }
}

onMounted(() => {
  loadReports()
  loadStats()
})
</script>

<style scoped>
.readonly-tip {
  margin-bottom: 12px;
}

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

.work-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-badge {
  vertical-align: top;
}

/* ---------------- 举报处理：左列表 + 右详情 ---------------- */
.rp-layout {
  display: flex;
  gap: 12px;
  height: calc(100vh - 280px);
  min-height: 480px;
}

.rp-side {
  display: flex;
  flex-direction: column;
  width: 300px;
  flex: none;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.rp-side__head {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.rp-side__list {
  flex: 1;
  overflow-y: auto;
}

.rp-item {
  position: relative;
  padding: 12px;
  border-bottom: 1px solid #f7f7f7;
  cursor: pointer;
  transition: background 0.15s;
}

.rp-item:hover {
  background: #f7f9fc;
}

.rp-item--active {
  background: #ecf5ff;
}

.rp-item__row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  /* 左栏本来就窄，加上对象标题动辄十几个字，两枚标签必然抢宽度。
     标题可以省略，状态绝不能省略 —— 客服扫列表判断优先级全靠它 */
  min-width: 0;
}

.rp-item__row > .el-tag:first-child {
  /* 0 1 auto：短标题保持自身宽度，长标题才收缩。
     写成 1 1 auto 会让「王老实」这种短标题也拉满整行，
     同屏几条一对比像是标签宽度失控 */
  flex: 0 1 auto;
  min-width: 0;
  max-width: 72%;
  overflow: hidden;
}

.rp-item__row > .el-tag:first-child :deep(.el-tag__content) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rp-item__row > .el-tag:last-child {
  flex: none;
}

.rp-item__content {
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  /* 限高2行，列表才不会被长描述撑得很长 */
  max-height: 38px;
  overflow: hidden;
}

.rp-item__meta {
  margin-top: 4px;
  font-size: 11px;
  color: #a8abb2;
}

.rp-item__accept {
  position: absolute;
  right: 12px;
  bottom: 10px;
}

/* 接单按钮是绝对定位在右下角的，会压住描述文字的第二行。
   待处理项给内容让出按钮宽度，别让客服读半截描述还要靠猜 */
.rp-item--pending .rp-item__content,
.rp-item--pending .rp-item__meta {
  padding-right: 62px;
}

.rp-side__page {
  padding: 8px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: center;
}

.rp-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.rp-main__empty {
  align-items: center;
  justify-content: center;
}

.rp-head {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafbfc;
}

.rp-head__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.rp-head__meta {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.rp-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

/* 快照：灰底突出，客服判罚的核心证据 */
.snapshot {
  padding: 12px;
  border-radius: 8px;
  background: #f5f6f7;
}

.snapshot__label {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: #606266;
}

.snapshot__main {
  display: flex;
  gap: 12px;
}

.snapshot__cover {
  width: 80px;
  height: 80px;
  flex: none;
  border-radius: 6px;
  background: #fff;
}

.snapshot__body {
  flex: 1;
  min-width: 0;
}

.snapshot__title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.snapshot__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.snapshot__desc {
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  max-height: 90px;
  overflow-y: auto;
}

.rp-info {
  margin-top: 14px;
}

.rp-evidence {
  width: 70px;
  height: 70px;
  margin-right: 8px;
  border-radius: 6px;
  cursor: zoom-in;
}

.rp-form {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f0f0f0;
}

.rp-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;
}

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

.cs-layout {
  display: flex;
  gap: 12px;
  height: calc(100vh - 150px);
  min-height: 520px;
}

/* ---------------- 左侧会话列表 ---------------- */
.cs-side {
  display: flex;
  flex-direction: column;
  width: 320px;
  flex: none;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.cs-side__head {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.cs-side__list {
  flex: 1;
  overflow-y: auto;
}

.cs-item {
  display: flex;
  gap: 10px;
  padding: 12px;
  cursor: pointer;
  border-bottom: 1px solid #f7f7f7;
  transition: background 0.15s;
}

.cs-item:hover {
  background: #f7f9fc;
}

.cs-item--active {
  background: #ecf5ff;
}

.cs-item__avatar {
  flex: none;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  font-size: 15px;
}

.cs-item__body {
  flex: 1;
  min-width: 0;
}

.cs-item__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.cs-item__name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-item__time {
  flex: none;
  font-size: 11px;
  color: #a8abb2;
}

.cs-item__msg {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-item__badge {
  flex: none;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.cs-item__product {
  margin-top: 4px;
  font-size: 11px;
  color: #b1b3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cs-side__page {
  padding: 8px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: center;
}

/* ---------------- 右侧聊天记录 ---------------- */
.cs-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.cs-main--empty {
  align-items: center;
  justify-content: center;
}

.cs-head {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafbfc;
}

.cs-head__peers {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}

.peer--buyer {
  color: #409eff;
}

.peer--seller {
  color: #ff6b35;
}

.peer__arrow {
  color: #c0c4cc;
}

.cs-head__meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.cs-head__product {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}

.cs-head__count {
  flex: none;
}

.cs-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f7f8fa;
}

/* 系统消息 */
.sys-msg {
  margin: 12px auto;
  padding: 4px 12px;
  max-width: 70%;
  width: fit-content;
  border-radius: 12px;
  background: #eef0f3;
  color: #909399;
  font-size: 12px;
  text-align: center;
}

/* 消息行：买家在左、卖家在右 */
.msg-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.msg-row--left {
  flex-direction: row;
}

.msg-row--right {
  flex-direction: row-reverse;
}

.msg-avatar {
  flex: none;
  background: #dcdfe6;
  color: #fff;
  font-size: 13px;
}

.msg-row--left .msg-avatar {
  background: linear-gradient(135deg, #409eff, #79bbff);
}

.msg-row--right .msg-avatar {
  background: linear-gradient(135deg, #ff6b35, #ff8c5a);
}

.msg-body {
  max-width: 62%;
  display: flex;
  flex-direction: column;
}

.msg-row--right .msg-body {
  align-items: flex-end;
}

.msg-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 11px;
  color: #a8abb2;
}

.bubble {
  padding: 9px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

/* 买家蓝色气泡 */
.bubble--buyer {
  background: #ecf5ff;
  color: #1f3b57;
  border: 1px solid #d9ecff;
  border-top-left-radius: 4px;
}

/* 卖家橙色气泡 */
.bubble--seller {
  background: #fff3ec;
  color: #6b3220;
  border: 1px solid #ffdfd0;
  border-top-right-radius: 4px;
}

.bubble--plain {
  background: #fff;
  border: 1px solid #ebeef5;
}

.bubble-img {
  width: 160px;
  max-height: 200px;
  border-radius: 8px;
  cursor: zoom-in;
}

/* 商品 / 订单卡片 */
.card {
  display: flex;
  gap: 10px;
  width: 260px;
  padding: 8px;
  border-radius: 10px;
}

.card--buyer {
  background: #f2f8ff;
  border: 1px solid #d9ecff;
}

.card--seller {
  background: #fff6f1;
  border: 1px solid #ffdfd0;
}

.card__img {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 6px;
  background: #f5f7fa;
}

.card__img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #c0c4cc;
}

.card__body {
  flex: 1;
  min-width: 0;
}

.card__title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__no {
  margin-top: 2px;
  font-family: Consolas, Monaco, monospace;
  font-size: 11px;
  color: #909399;
}

.card__price {
  margin: 2px 0 4px;
  font-size: 14px;
  font-weight: 700;
  color: #f56c6c;
}

.cs-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border-top: 1px solid #f0f0f0;
  background: #fdf6ec;
  color: #b88230;
  font-size: 12px;
}
</style>
