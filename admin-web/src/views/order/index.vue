<!-- 文件路径: admin-web/src/views/order/index.vue -->
<template>
  <div class="page-container">
    <div class="page-card">
      <!-- 1. 状态标签页（带数量角标） -->
      <el-tabs v-model="activeTab" class="status-tabs" @tab-change="handleTabChange">
        <el-tab-pane v-for="tab in tabs" :key="tab.value" :name="tab.value">
          <template #label>
            <span class="tab-label">
              {{ tab.label }}
              <span v-if="countOf(tab.value) > 0" class="tab-badge">{{ countOf(tab.value) }}</span>
            </span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 2. 搜索栏 -->
      <el-form :model="query" inline class="search-bar">
        <el-form-item label="订单号">
          <el-input
            v-model.trim="query.orderNo"
            placeholder="订单号，支持模糊"
            clearable
            style="width: 210px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="买家">
          <el-input
            v-model.trim="query.buyerKeyword"
            placeholder="买家昵称"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="卖家">
          <el-input
            v-model.trim="query.sellerKeyword"
            placeholder="卖家昵称"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="下单时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 250px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 3. 工具栏 -->
      <div class="toolbar">
        <span class="toolbar__title">
          订单列表
          <span class="toolbar__hint">共 {{ total }} 笔</span>
        </span>
        <el-button :icon="Refresh" circle @click="loadAll" />
      </div>

      <!-- 4. 错误态 -->
      <el-result
        v-if="error"
        icon="error"
        title="数据加载失败"
        sub-title="请检查后端服务是否启动、网络是否正常"
      >
        <template #extra>
          <el-button type="primary" @click="loadAll">重新加载</el-button>
        </template>
      </el-result>

      <!-- 5. 表格 -->
      <template v-else>
        <el-table
          v-loading="loading"
          :data="list"
          border
          stripe
          :header-cell-style="{ background: '#fafafa', color: '#606266', fontWeight: 600 }"
          style="width: 100%"
        >
          <el-table-column label="订单号" width="180">
            <template #default="{ row }">
              <div class="order-no">{{ row.orderNo }}</div>
            </template>
          </el-table-column>
          <el-table-column label="商品信息" min-width="170">
            <template #default="{ row }">
              <div class="goods">
                <el-image
                  v-if="row.productCover"
                  :src="row.productCover"
                  fit="cover"
                  class="goods__img"
                  :preview-src-list="[row.productCover]"
                  preview-teleported
                />
                <div v-else class="goods__img goods__img--empty">无图</div>
                <div class="goods__info">
                  <div class="goods__title">{{ row.productTitle || '商品已删除' }}</div>
                  <div class="goods__id">商品ID：{{ row.productId }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="买家" width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.buyerNickname || '—' }}</template>
          </el-table-column>
          <el-table-column label="卖家" width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.sellerNickname || '—' }}</template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">
              <span class="price">¥{{ Number(row.price || 0).toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.status).type" :class="statusMeta(row.status).className" effect="light">
                {{ statusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下单时间" width="160">
            <template #default="{ row }">{{ row.createTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <div class="ops">
                <el-button link type="primary" @click="openDetail(row)">详情</el-button>
                <el-button
                  v-permission="'admin'"
                  v-if="canAdminCancel(row.status)"
                  link
                  type="warning"
                  @click="handleAdminCancel(row)"
                >
                  强制取消
                </el-button>
                <el-button
                  v-permission="'admin'"
                  v-if="canAdminRefund(row.status)"
                  link
                  type="danger"
                  @click="handleAdminRefund(row)"
                >
                  强制退款
                </el-button>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="没有符合条件的订单" :image-size="90" />
          </template>
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="loadData"
            @size-change="handleSearch"
          />
        </div>
      </template>
    </div>

    <!-- 6. 订单详情弹窗 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="820px" top="6vh">
      <div v-if="detail" class="detail">
        <!-- 状态横幅 -->
        <div class="detail__banner" :class="'detail__banner--' + detail.status">
          <div class="detail__banner-main">
            <span class="detail__banner-status">{{ detail.statusLabel }}</span>
            <span class="detail__banner-no">订单号：{{ detail.orderNo }}</span>
          </div>
          <div class="detail__banner-amount">成交金额 ¥{{ Number(detail.price || 0).toFixed(2) }}</div>
        </div>

        <!-- 商品快照 -->
        <div class="section">
          <div class="section__title">商品快照<span class="section__tip">下单时冻结，商品后续修改不影响此处</span></div>
          <div class="snapshot">
            <el-image
              v-if="detail.product && detail.product.cover"
              :src="detail.product.cover"
              fit="cover"
              class="snapshot__img"
              :preview-src-list="[detail.product.cover]"
              preview-teleported
            />
            <div v-else class="snapshot__img snapshot__img--empty">无图</div>
            <div class="snapshot__info">
              <div class="snapshot__title">{{ detail.product?.title || '商品信息缺失' }}</div>
              <div class="snapshot__meta">
                <span>分类：{{ detail.product?.categoryName || '—' }}</span>
                <span v-if="detail.product?.isVerifiedSeller" class="snapshot__badge">学生认证卖家</span>
              </div>
              <div class="snapshot__price">
                ¥{{ Number(detail.product?.price || detail.price || 0).toFixed(2) }}
                <span v-if="detail.product?.originalPrice" class="snapshot__original">
                  原价 ¥{{ Number(detail.product.originalPrice).toFixed(2) }}
                </span>
              </div>
              <div v-if="detail.product?.description" class="snapshot__desc">
                {{ detail.product.description }}
              </div>
            </div>
          </div>
        </div>

        <!-- 交易信息 -->
        <div class="section">
          <div class="section__title">交易信息</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="买家">{{ detail.buyerNickname || '—' }}</el-descriptions-item>
            <el-descriptions-item label="卖家">{{ detail.sellerNickname || '—' }}</el-descriptions-item>
            <el-descriptions-item label="下单时间">{{ detail.createTime || '—' }}</el-descriptions-item>
            <el-descriptions-item label="支付截止">{{ detail.payDeadline || '—' }}</el-descriptions-item>
            <el-descriptions-item label="支付时间">{{ detail.paymentTime || '—' }}</el-descriptions-item>
            <el-descriptions-item label="发货时间">{{ detail.shipTime || '—' }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ detail.finishTime || '—' }}</el-descriptions-item>
            <el-descriptions-item label="取消时间">{{ detail.cancelTime || '—' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.refundTime" label="退款时间" :span="2">
              {{ detail.refundTime }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 地址快照 -->
        <div class="section">
          <div class="section__title">收货 / 交易地址</div>
          <div v-if="detail.address" class="address">
            <div class="address__line">
              <span class="address__name">{{ detail.address.receiverName }}</span>
              <span class="address__phone">{{ detail.address.phone }}</span>
            </div>
            <div class="address__detail">{{ detail.address.address }}</div>
          </div>
          <el-empty v-else description="该订单没有地址信息" :image-size="60" />
        </div>

        <!-- 原因区 -->
        <el-alert
          v-if="detail.cancelReason"
          class="reason"
          type="info"
          :closable="false"
          :title="'取消原因：' + detail.cancelReason"
        />
        <el-alert
          v-if="detail.refundReason"
          class="reason"
          type="warning"
          :closable="false"
          :title="'退款原因：' + detail.refundReason"
        />
        <el-alert
          v-if="detail.rejectRefundReason"
          class="reason"
          type="error"
          :closable="false"
          :title="'拒绝退款原因：' + detail.rejectRefundReason"
        />

        <!-- 状态时间线 -->
        <div class="section">
          <div class="section__title">状态流转时间线</div>
          <el-timeline v-if="timeline.length" class="timeline">
            <el-timeline-item
              v-for="(item, index) in timeline"
              :key="item.id"
              :type="index === 0 ? 'primary' : ''"
              :hollow="index !== 0"
              :timestamp="item.createTime"
              placement="top"
            >
              <div class="timeline__head">
                <span class="timeline__action">{{ item.actionLabel }}</span>
                <el-tag size="small" effect="plain">{{ item.operatorRoleLabel }}</el-tag>
                <span class="timeline__operator">{{ item.operatorName }}</span>
              </div>
              <div v-if="item.remark" class="timeline__remark">{{ item.remark }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无操作日志" :image-size="60" />
        </div>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-permission="'admin'"
          v-if="detail && detail.canAdminCancel"
          type="warning"
          plain
          @click="handleAdminCancel(detail)"
        >
          强制取消
        </el-button>
        <el-button
          v-permission="'admin'"
          v-if="detail && detail.canAdminRefund"
          type="danger"
          @click="handleAdminRefund(detail)"
        >
          强制退款
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  adminCancelOrder,
  adminRefundOrder,
  getOrderCount,
  getOrderDetail,
  pageOrders
} from '@/api/order'

/** 状态元数据：标签文案 + Element 标签类型 + 自定义配色 */
const STATUS_META = {
  0: { label: '待付款', type: 'warning', className: 'tag--pay' },
  1: { label: '待发货', type: 'primary', className: 'tag--ship' },
  2: { label: '待收货', type: 'primary', className: 'tag--receive' },
  3: { label: '已完成', type: 'success', className: 'tag--done' },
  4: { label: '已取消', type: 'info', className: 'tag--cancel' },
  5: { label: '退款中', type: 'danger', className: 'tag--refund' },
  6: { label: '已退款', type: 'info', className: 'tag--refunded' }
}

const statusMeta = status => STATUS_META[status] || { label: '未知', type: 'info', className: '' }

const tabs = [
  { value: 'all', label: '全部' },
  { value: '0', label: '待付款' },
  { value: '1', label: '待发货' },
  { value: '2', label: '待收货' },
  { value: '3', label: '已完成' },
  { value: '4', label: '已取消' },
  { value: '5', label: '退款中' },
  { value: '6', label: '已退款' }
]

const statusOptions = tabs.filter(t => t.value !== 'all').map(t => ({ value: Number(t.value), label: t.label }))

const activeTab = ref('all')
const list = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref(false)
const dateRange = ref([])
const counts = ref({})

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: null,
  orderNo: '',
  buyerKeyword: '',
  sellerKeyword: ''
})

const detailVisible = ref(false)
const detail = ref(null)

/** 时间线倒序：最新操作在最上面，符合"最近发生了什么"的阅读习惯 */
const timeline = computed(() => {
  const logs = detail.value?.logs || []
  return [...logs].reverse()
})

/** 角标数量：tab.value 为 'all' 时取 total */
const countOf = value => {
  if (value === 'all') return counts.value.total || 0
  const map = {
    0: 'pendingPay',
    1: 'pendingShip',
    2: 'pendingReceive',
    3: 'finished',
    4: 'canceled',
    5: 'refunding',
    6: 'refunded'
  }
  return counts.value[map[value]] || 0
}

const canAdminCancel = status => [0, 1, 2, 5].includes(status)
const canAdminRefund = status => [1, 2, 5].includes(status)

const handleTabChange = name => {
  query.status = name === 'all' ? null : Number(name)
  query.pageNum = 1
  loadData()
}

const buildParams = () => {
  const params = {
    pageNum: query.pageNum,
    pageSize: query.pageSize,
    status: query.status,
    orderNo: query.orderNo || undefined,
    buyerKeyword: query.buyerKeyword || undefined,
    sellerKeyword: query.sellerKeyword || undefined
  }
  if (dateRange.value && dateRange.value.length === 2) {
    params.startTime = dateRange.value[0]
    params.endTime = dateRange.value[1]
  }
  return params
}

const loadData = async () => {
  loading.value = true
  error.value = false
  try {
    const page = await pageOrders(buildParams())
    list.value = page.records || []
    total.value = Number(page.total || 0)
  } catch (e) {
    error.value = true
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const loadCounts = async () => {
  try {
    counts.value = await getOrderCount()
  } catch (e) {
    counts.value = {}
  }
}

const loadAll = () => {
  loadData()
  loadCounts()
}

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.status = null
  query.orderNo = ''
  query.buyerKeyword = ''
  query.sellerKeyword = ''
  dateRange.value = []
  activeTab.value = 'all'
  query.pageNum = 1
  loadData()
}

const openDetail = async row => {
  try {
    detail.value = await getOrderDetail(row.orderNo)
    detailVisible.value = true
  } catch (e) {
    // 错误提示由请求封装统一处理
  }
}

/**
 * 强制取消 / 强制退款共用一套「弹窗填原因 → 确认 → 刷新」流程。
 * 原因必填且至少 2 个字，服务端还会再校验一次（前端校验只是体验层）。
 */
const askReason = async (title, tip) => {
  const { value } = await ElMessageBox.prompt(tip, title, {
    confirmButtonText: '确认提交',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '请填写操作原因，将写入订单操作日志',
    inputValidator: val => (val && val.trim().length >= 2 ? true : '原因至少 2 个字')
  })
  return value.trim()
}

const handleAdminCancel = async row => {
  try {
    const reason = await askReason('强制取消订单', `确认强制取消订单 ${row.orderNo}？已付款订单会同步把商品恢复上架。`)
    await adminCancelOrder(row.orderNo, reason)
    ElMessage.success('订单已强制取消')
    detailVisible.value = false
    loadAll()
  } catch (e) {
    // 用户取消弹窗或接口报错
  }
}

const handleAdminRefund = async row => {
  try {
    const reason = await askReason('强制退款', `确认对订单 ${row.orderNo} 强制退款？退款后商品会恢复上架。`)
    await adminRefundOrder(row.orderNo, reason)
    ElMessage.success('已强制退款')
    detailVisible.value = false
    loadAll()
  } catch (e) {
    // 用户取消弹窗或接口报错
  }
}

onMounted(loadAll)
</script>

<style scoped>
.status-tabs {
  margin-bottom: 4px;
}

.status-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.tab-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  font-weight: 500;
}

.toolbar__hint {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.order-no {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
}

.goods {
  display: flex;
  align-items: center;
  gap: 10px;
}

.goods__img {
  width: 44px;
  height: 44px;
  flex: none;
  border-radius: 6px;
  background: #f5f7fa;
}

.goods__img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #c0c4cc;
}

.goods__info {
  min-width: 0;
}

.goods__title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods__id {
  margin-top: 2px;
  font-size: 12px;
  color: #a8abb2;
}

.price {
  font-weight: 600;
  color: #f56c6c;
}

/* 操作列：三个链接按钮不换行，避免行高被撑成两行 */
.ops {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}

/* 状态标签：待付款橙、待发货蓝、待收货紫、已完成绿、已取消灰、退款红 */
.tag--pay {
  color: #e6a23c;
  background: #fdf6ec;
  border-color: #f5dab1;
}

.tag--ship {
  color: #409eff;
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.tag--receive {
  color: #8b5cf6;
  background: #f4f0ff;
  border-color: #d9ccff;
}

.tag--done {
  color: #67c23a;
  background: #f0f9eb;
  border-color: #c2e7b0;
}

.tag--cancel {
  color: #909399;
  background: #f4f4f5;
  border-color: #d3d4d6;
}

.tag--refund {
  color: #f56c6c;
  background: #fef0f0;
  border-color: #fbc4c4;
}

.tag--refunded {
  color: #b1b3b8;
  background: #f4f4f5;
  border-color: #e0e0e2;
}

.detail__banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-radius: 10px;
  margin-bottom: 18px;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #6f7bf7);
}

.detail__banner--0 {
  background: linear-gradient(135deg, #e6a23c, #f0b95c);
}

.detail__banner--2 {
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
}

.detail__banner--3 {
  background: linear-gradient(135deg, #67c23a, #85ce61);
}

.detail__banner--4 {
  background: linear-gradient(135deg, #909399, #b1b3b8);
}

.detail__banner--5,
.detail__banner--6 {
  background: linear-gradient(135deg, #f56c6c, #f89898);
}

.detail__banner-status {
  font-size: 18px;
  font-weight: 600;
}

.detail__banner-no {
  margin-left: 12px;
  font-size: 12px;
  opacity: 0.9;
  font-family: Consolas, Monaco, monospace;
}

.detail__banner-amount {
  font-size: 16px;
  font-weight: 600;
}

.section {
  margin-bottom: 18px;
}

.section__title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.section__tip {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #a8abb2;
}

.snapshot {
  display: flex;
  gap: 14px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafcff;
}

.snapshot__img {
  width: 88px;
  height: 88px;
  flex: none;
  border-radius: 8px;
  background: #f0f2f5;
}

.snapshot__img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 12px;
}

.snapshot__info {
  min-width: 0;
  flex: 1;
}

.snapshot__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.snapshot__meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #909399;
}

.snapshot__badge {
  padding: 1px 8px;
  border-radius: 9px;
  background: #fdf6ec;
  color: #e6a23c;
}

.snapshot__price {
  margin-top: 6px;
  font-size: 16px;
  font-weight: 600;
  color: #f56c6c;
}

.snapshot__original {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #a8abb2;
  text-decoration: line-through;
}

.snapshot__desc {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

.address {
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}

.address__name {
  font-weight: 600;
  color: #303133;
}

.address__phone {
  margin-left: 12px;
  color: #606266;
}

.address__detail {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.reason {
  margin-bottom: 10px;
}

.timeline {
  padding-left: 4px;
  max-height: 260px;
  overflow-y: auto;
}

.timeline__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.timeline__action {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.timeline__operator {
  font-size: 12px;
  color: #909399;
}

.timeline__remark {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
