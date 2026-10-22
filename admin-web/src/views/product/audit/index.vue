<!-- 文件路径: admin-web/src/views/product/audit/index.vue -->
<template>
  <div class="page-container">
    <div class="page-card">
      <!-- 状态标签页 -->
      <el-tabs v-model="activeTab" class="status-tabs" @tab-change="handleTabChange">
        <el-tab-pane name="all">
          <template #label>
            <span>全部</span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="0">
          <template #label>
            <span>
              待审核
              <el-badge v-if="pendingCount > 0" :value="pendingCount" :max="99" class="tab-badge" />
            </span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="review">
          <template #label>
            <span>
              待人工复核
              <el-badge v-if="manualReviewCount > 0" :value="manualReviewCount" :max="99" class="tab-badge tab-badge--danger" />
            </span>
          </template>
        </el-tab-pane>
        <el-tab-pane label="已上架" name="1" />
        <el-tab-pane label="已下架" name="2" />
        <el-tab-pane label="违规下架" name="4" />
      </el-tabs>

      <!-- 搜索筛选 -->
      <el-form :model="query" inline class="search-bar">
        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            placeholder="商品标题 / 描述"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.categoryId" placeholder="全部分类" clearable style="width: 140px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number
            v-model="query.minPrice"
            :min="0"
            :controls="false"
            placeholder="最低"
            style="width: 92px"
          />
          <span class="range-sep">-</span>
          <el-input-number
            v-model="query.maxPrice"
            :min="0"
            :controls="false"
            placeholder="最高"
            style="width: 92px"
          />
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 错误态 -->
      <el-result
        v-if="error"
        icon="error"
        title="商品数据加载失败"
        sub-title="请检查后端服务是否启动、网络是否正常"
      >
        <template #extra>
          <el-button type="primary" @click="loadData">重新加载</el-button>
        </template>
      </el-result>

      <template v-else>
        <div class="toolbar">
          <span class="toolbar__title">
            {{ currentTabLabel }}
            <span class="toolbar__count">共 {{ total }} 件</span>
          </span>
          <el-button :icon="Refresh" circle @click="loadData" />
        </div>

        <el-table
          v-loading="loading"
          :data="list"
          border
          stripe
          highlight-current-row
          :header-cell-style="{ background: '#fafafa', color: '#606266', fontWeight: 600 }"
          style="width: 100%"
          @row-dblclick="openDetail"
        >
          <el-table-column label="主图" width="88" align="center">
            <template #default="{ row }">
              <el-image
                v-if="row.cover"
                :src="row.cover"
                :preview-src-list="[row.cover]"
                :preview-teleported="true"
                fit="cover"
                class="cover-thumb"
              >
                <template #error>
                  <div class="cover-thumb__error">无图</div>
                </template>
              </el-image>
              <div v-else class="cover-thumb cover-thumb__error">无图</div>
            </template>
          </el-table-column>
          <el-table-column label="商品" min-width="220">
            <template #default="{ row }">
              <div class="product-cell">
                <div class="product-cell__title">{{ row.title }}</div>
                <div class="product-cell__meta">
                  <el-tag v-if="row.isVerifiedSeller === 1" size="small" effect="plain" class="badge-student">
                    学生认证
                  </el-tag>
                  <!-- 命中敏感词的商品直接打标：审核员不用打开详情就知道要重点看什么 -->
                  <el-tag
                    v-if="row.needManualReview === 1"
                    size="small"
                    type="danger"
                    effect="light"
                    class="badge-sensitive"
                    :title="'命中敏感词：' + (row.sensitiveHit || '')"
                  >
                    待复核
                  </el-tag>
                  <span v-if="row.needManualReview === 1 && row.sensitiveHit" class="sensitive-words">
                    命中：{{ row.sensitiveHit }}
                  </span>
                  <span>ID {{ row.id }}</span>
                  <span>浏览 {{ row.viewCount || 0 }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="卖家" width="150">
            <template #default="{ row }">
              <div class="seller-cell">
                <el-avatar :size="26" :src="row.sellerAvatar">
                  {{ (row.sellerNickname || 'U').slice(0, 1) }}
                </el-avatar>
                <span class="seller-cell__name">{{ row.sellerNickname || '—' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="categoryName" label="分类" width="110" align="center">
            <template #default="{ row }">{{ row.categoryName || '—' }}</template>
          </el-table-column>
          <el-table-column label="价格" width="130" align="right">
            <template #default="{ row }">
              <div class="price">¥{{ formatMoney(row.price) }}</div>
              <div v-if="row.originalPrice" class="price-original">¥{{ formatMoney(row.originalPrice) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="105" align="center">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.status).type" effect="light">
                {{ statusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="发布时间" width="165" align="center" />
          <el-table-column label="操作" width="190" align="center" fixed="right">
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
              <el-button
                v-if="row.status === 1"
                v-permission="'admin'"
                link
                type="danger"
                @click="openForceOffline(row)"
              >
                强制下架
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :description="emptyText" />
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
            @size-change="handleSizeChange"
            @current-change="loadData"
          />
        </div>
      </template>
    </div>

    <!-- 商品详情 / 审核预览 -->
    <el-dialog v-model="detailVisible" title="商品详情" width="760px" destroy-on-close>
      <div v-if="detail" class="detail">
        <el-carousel v-if="detail.images && detail.images.length" height="300px" :autoplay="false" indicator-position="outside">
          <el-carousel-item v-for="(img, index) in detail.images" :key="index">
            <el-image :src="img" fit="contain" class="detail__img" :preview-src-list="detail.images" :initial-index="index" :preview-teleported="true" />
          </el-carousel-item>
        </el-carousel>
        <div v-else class="detail__noimg">该商品没有图片</div>

        <div class="detail__head">
          <div class="detail__price">
            ¥{{ formatMoney(detail.price) }}
            <span v-if="detail.originalPrice" class="detail__original">¥{{ formatMoney(detail.originalPrice) }}</span>
          </div>
          <el-tag :type="statusMeta(detail.status).type" effect="dark">
            {{ statusMeta(detail.status).label }}
          </el-tag>
        </div>
        <h3 class="detail__title">{{ detail.title }}</h3>
        <div class="detail__meta">
          <span>分类：{{ detail.categoryName || '—' }}</span>
          <span>发布：{{ detail.createTime }}</span>
          <span>浏览：{{ detail.viewCount || 0 }}</span>
        </div>

        <el-descriptions :column="1" border class="detail__desc">
          <el-descriptions-item label="商品描述">
            <div class="detail__text">{{ detail.description || '卖家未填写描述' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="卖家">
            <div class="seller-cell">
              <el-avatar :size="26" :src="detail.seller?.avatar">
                {{ (detail.seller?.nickname || 'U').slice(0, 1) }}
              </el-avatar>
              <span class="seller-cell__name">{{ detail.seller?.nickname || '—' }}</span>
              <el-tag v-if="detail.seller?.studentVerified" size="small" class="badge-student" effect="plain">
                学生认证
              </el-tag>
              <span class="detail__credit">信誉分 {{ detail.seller?.creditScore ?? '—' }}</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.auditReason" label="审核/下架原因">
            <span class="danger-text">{{ detail.auditReason }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.needManualReview === 1" label="敏感词提示">
            <div class="sensitive-box">
              <el-tag size="small" type="danger" effect="light">待人工复核</el-tag>
              <span class="sensitive-box__text">
                发布时命中：{{ detail.sensitiveHit || '—' }}
                <br />
                命中「警告级」词的商品允许发布，但需要人工确认语境是否正常
                （例如「加微信」可能真的是约线下验货）。
              </span>
            </div>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detail?.status === 0"
          v-permission="'admin'"
          type="success"
          @click="openAudit(detail)"
        >
          去审核
        </el-button>
        <el-button
          v-if="detail?.status === 1"
          v-permission="'admin'"
          type="danger"
          plain
          @click="openForceOffline(detail)"
        >
          强制下架
        </el-button>
      </template>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog v-model="auditVisible" title="审核商品" width="540px" destroy-on-close>
      <el-alert
        v-if="auditTarget"
        type="info"
        :closable="false"
        show-icon
        class="audit-alert"
        :title="auditTarget.title"
        description="审核通过后商品立即上架；驳回后卖家可修改内容重新提交审核。"
      />

      <!-- 审核前的商品预览 -->
      <div v-if="auditPreview" class="audit-preview">
        <el-image
          :src="auditPreview.images && auditPreview.images[0]"
          :preview-src-list="auditPreview.images || []"
          :preview-teleported="true"
          fit="cover"
          class="audit-preview__img"
        />
        <div class="audit-preview__body">
          <div class="audit-preview__price">¥{{ formatMoney(auditPreview.price) }}</div>
          <div class="audit-preview__meta">
            {{ auditPreview.categoryName || '未分类' }} · {{ auditPreview.seller?.nickname || '未知卖家' }}
            <el-tag v-if="auditPreview.seller?.studentVerified" size="small" class="badge-student" effect="plain">
              学生认证
            </el-tag>
          </div>
          <div class="audit-preview__desc">{{ auditPreview.description || '卖家未填写描述' }}</div>
        </div>
      </div>

      <el-form ref="auditFormRef" :model="auditForm" :rules="auditRules" label-width="90px">
        <el-form-item label="审核结果" prop="status">
          <el-radio-group v-model="auditForm.status">
            <el-radio :value="1">通过并上架</el-radio>
            <el-radio :value="2">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditForm.status === 2" label="驳回原因" prop="auditReason">
          <el-input
            v-model.trim="auditForm.auditReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请说明驳回原因，将展示给卖家"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAuditSubmit">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 强制下架 -->
    <el-dialog v-model="offlineVisible" title="强制下架商品" width="520px" destroy-on-close>
      <el-alert
        v-if="offlineTarget"
        type="warning"
        :closable="false"
        show-icon
        class="audit-alert"
        :title="offlineTarget.title"
        description="强制下架后商品状态变为「违规下架」，卖家无法自行重新上架。"
      />
      <el-form ref="offlineFormRef" :model="offlineForm" :rules="offlineRules" label-width="90px">
        <el-form-item label="下架原因" prop="reason">
          <el-input
            v-model.trim="offlineForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="如：涉嫌售卖违禁物品"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="offlineVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="handleOfflineSubmit">确认下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { listCategories } from '@/api/category'
import { auditProduct, forceOfflineProduct, getProduct, pageAuditProducts } from '@/api/product'

// ---------- 字典 ----------
const TABS = [
  { name: 'all', label: '全部商品' },
  { name: '0', label: '待审核商品' },
  { name: 'review', label: '待人工复核' },
  { name: '1', label: '已上架商品' },
  { name: '2', label: '已下架商品' },
  { name: '4', label: '违规下架商品' }
]

const statusMeta = status => {
  switch (status) {
    case 0:
      return { label: '待审核', type: 'warning' }
    case 1:
      return { label: '已上架', type: 'success' }
    case 2:
      return { label: '已下架', type: 'info' }
    case 3:
      return { label: '已售出', type: 'primary' }
    case 4:
      return { label: '违规下架', type: 'danger' }
    default:
      return { label: '未知', type: 'info' }
  }
}

const formatMoney = value => {
  if (value === null || value === undefined || value === '') return '0.00'
  return Number(value).toFixed(2)
}

// ---------- 查询 ----------
const route = useRoute()
const activeTab = ref('all')
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: null,
  // 1 表示只看「发布时命中警告级敏感词」的商品（待人工复核 Tab）
  needManualReview: null,
  keyword: '',
  categoryId: null,
  minPrice: undefined,
  maxPrice: undefined
})
const dateRange = ref([])
const list = ref([])
const total = ref(0)
const pendingCount = ref(0)
// 待人工复核数量。与待审核数量分开统计：前者是「这次发布里有人担心的」，
// 后者是「全部还没审的」，两个数字一起看才知道队列里有多少要花时间
const manualReviewCount = ref(0)
const categories = ref([])
const loading = ref(false)
const error = ref(false)

const currentTabLabel = computed(
  () => TABS.find(tab => tab.name === activeTab.value)?.label || '全部商品'
)

const emptyText = computed(() => {
  const hasFilter =
    query.keyword || query.categoryId || query.minPrice != null || query.maxPrice != null
  if (hasFilter) return '没有符合条件的商品'
  if (activeTab.value === 'review') return '暂无待人工复核的商品，说明最近发布的内容都很干净'
  return activeTab.value === '0' ? '暂无待审核商品' : '暂无商品'
})

const buildParams = () => ({
  pageNum: query.pageNum,
  pageSize: query.pageSize,
  status: query.status,
  needManualReview: query.needManualReview ?? undefined,
  keyword: query.keyword || undefined,
  categoryId: query.categoryId || undefined,
  minPrice: query.minPrice ?? undefined,
  maxPrice: query.maxPrice ?? undefined,
  startTime: dateRange.value?.[0] || undefined,
  endTime: dateRange.value?.[1] || undefined
})

const loadData = async () => {
  loading.value = true
  error.value = false
  try {
    const data = await pageAuditProducts(buildParams())
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) {
    error.value = true
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const loadPendingCount = async () => {
  try {
    const [pending, manual] = await Promise.all([
      pageAuditProducts({ pageNum: 1, pageSize: 1, status: 0 }),
      pageAuditProducts({ pageNum: 1, pageSize: 1, status: 0, needManualReview: 1 })
    ])
    pendingCount.value = Number(pending.total || 0)
    manualReviewCount.value = Number(manual.total || 0)
  } catch (e) {
    pendingCount.value = 0
    manualReviewCount.value = 0
  }
}

const loadCategories = async () => {
  try {
    categories.value = (await listCategories({ all: true })) || []
  } catch (e) {
    categories.value = []
  }
}

const handleTabChange = name => {
  // 「待人工复核」不是商品状态，而是审核侧的工作标记：
  // 命中警告级敏感词的商品仍然是待审核(0)，只是需要人重点看一眼。
  // 因此这个 Tab 要同时带 status=0 与 needManualReview=1 两个条件。
  if (name === 'review') {
    query.status = 0
    query.needManualReview = 1
  } else {
    query.status = name === 'all' ? null : Number(name)
    query.needManualReview = null
  }
  query.pageNum = 1
  loadData()
}

const handleSearch = () => {
  if (query.minPrice != null && query.maxPrice != null && query.minPrice > query.maxPrice) {
    ElMessage.warning('最低价格不能高于最高价格')
    return
  }
  query.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.keyword = ''
  query.categoryId = null
  query.minPrice = undefined
  query.maxPrice = undefined
  dateRange.value = []
  query.pageNum = 1
  loadData()
}

const handleSizeChange = () => {
  query.pageNum = 1
  loadData()
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detail = ref(null)

const openDetail = async row => {
  detailVisible.value = true
  detail.value = null
  try {
    detail.value = await getProduct(row.id)
  } catch (e) {
    detailVisible.value = false
  }
}

// ---------- 审核 ----------
const auditVisible = ref(false)
const auditTarget = ref(null)
const auditPreview = ref(null)
const auditFormRef = ref()
const submitting = ref(false)
const auditForm = reactive({ status: 1, auditReason: '' })

const auditRules = {
  status: [{ required: true, message: '请选择审核结果', trigger: 'change' }],
  auditReason: [
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

const openAudit = async row => {
  auditTarget.value = row
  auditPreview.value = null
  auditForm.status = 1
  auditForm.auditReason = ''
  detailVisible.value = false
  auditVisible.value = true
  // 审核弹窗内展示完整商品信息，避免管理员在列表与详情间反复切换
  try {
    auditPreview.value = await getProduct(row.id)
  } catch (e) {
    // 预览加载失败时仍可审核，列表数据已足够判断
  }
}

const handleAuditSubmit = async () => {
  const valid = await auditFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await auditProduct(auditTarget.value.id, {
      status: auditForm.status,
      auditReason: auditForm.status === 2 ? auditForm.auditReason : undefined
    })
    ElMessage.success(auditForm.status === 1 ? '已通过，商品已上架' : '已驳回')
    auditVisible.value = false
    auditPreview.value = null
    await Promise.all([loadData(), loadPendingCount()])
  } finally {
    submitting.value = false
  }
}

// ---------- 强制下架 ----------
const offlineVisible = ref(false)
const offlineTarget = ref(null)
const offlineFormRef = ref()
const offlineForm = reactive({ reason: '' })

const offlineRules = {
  reason: [
    { required: true, message: '请填写强制下架原因', trigger: 'blur' },
    { max: 200, message: '原因不能超过200个字', trigger: 'blur' }
  ]
}

const openForceOffline = row => {
  offlineTarget.value = row
  offlineForm.reason = ''
  detailVisible.value = false
  offlineVisible.value = true
}

const handleOfflineSubmit = async () => {
  const valid = await offlineFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await forceOfflineProduct(offlineTarget.value.id, offlineForm.reason)
    ElMessage.success('已强制下架')
    offlineVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadCategories()
  // 支持 /product/audit?tab=review 直达某个 Tab：
  // 「待人工复核」需要被通知点进来（管理员收到敏感词提醒后跳这里），
  // 让他自己再找一遍 Tab 是多余的一步
  const tab = String(route.query.tab || '')
  if (tab && TABS.some(t => t.name === tab)) {
    activeTab.value = tab
    handleTabChange(tab)
  } else {
    loadData()
  }
  loadPendingCount()
})
</script>

<style scoped>
.status-tabs {
  margin-bottom: 4px;
}

.status-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.tab-badge {
  margin-left: 4px;
  transform: translateY(-2px);
}

.tab-badge :deep(.el-badge__content) {
  border: none;
}

.range-sep {
  margin: 0 6px;
  color: #c0c4cc;
}

.toolbar__count {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.cover-thumb {
  width: 62px;
  height: 62px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  cursor: zoom-in;
}

.cover-thumb__error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #c0c4cc;
  background: #f5f7fa;
}

.product-cell__title {
  font-size: 14px;
  color: #303133;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-cell__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  font-size: 12px;
  color: #a8abb2;
}

.badge-student {
  color: #ff6b35;
  border-color: #ffd0bf;
  background: #fff6f1;
}

/* 待人工复核用红色：它是「需要人花时间看」的信号，
   与「学生认证」这类中性标记必须一眼区分开 */
.badge-sensitive {
  color: #f56c6c;
  border-color: #fbc4c4;
  background: #fef0f0;
}

.sensitive-words {
  color: #f56c6c;
  font-size: 12px;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-badge--danger :deep(.el-badge__content) {
  background-color: #f56c6c;
}

.sensitive-box {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.sensitive-box__text {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}

.seller-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.seller-cell__name {
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price {
  font-size: 14px;
  font-weight: 600;
  color: #f56c6c;
}

.price-original {
  font-size: 12px;
  color: #c0c4cc;
  text-decoration: line-through;
}

.detail__img {
  width: 100%;
  height: 300px;
  background: #fafafa;
}

.detail__noimg {
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #fafafa;
  border-radius: 8px;
}

.detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.detail__price {
  font-size: 24px;
  font-weight: 700;
  color: #f56c6c;
}

.detail__original {
  margin-left: 10px;
  font-size: 13px;
  font-weight: 400;
  color: #c0c4cc;
  text-decoration: line-through;
}

.detail__title {
  margin: 8px 0;
  font-size: 17px;
  color: #303133;
}

.detail__meta {
  display: flex;
  gap: 18px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 14px;
}

.detail__desc {
  margin-top: 6px;
}

.detail__text {
  white-space: pre-wrap;
  line-height: 1.7;
  color: #606266;
}

.detail__credit {
  font-size: 12px;
  color: #909399;
}

.audit-alert {
  margin-bottom: 18px;
}

.audit-preview {
  display: flex;
  gap: 14px;
  padding: 12px;
  margin-bottom: 18px;
  background: #fafafa;
  border: 1px solid #f0f2f5;
  border-radius: 8px;
}

.audit-preview__img {
  width: 88px;
  height: 88px;
  border-radius: 6px;
  flex: none;
  background: #f0f2f5;
}

.audit-preview__body {
  flex: 1;
  min-width: 0;
}

.audit-preview__price {
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
}

.audit-preview__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0;
  font-size: 12px;
  color: #909399;
}

.audit-preview__desc {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.danger-text {
  color: #f56c6c;
}
</style>
