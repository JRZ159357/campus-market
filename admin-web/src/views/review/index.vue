<!-- 文件路径: admin-web/src/views/review/index.vue -->
<template>
  <div class="page-container">
    <div class="page-card">
      <!-- 快速筛选标签：把最常用的五类筛选做成 Tab，避免每次都拉下拉框 -->
      <el-tabs v-model="activeTab" class="tabs" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="好评 (4-5星)" name="good" />
        <el-tab-pane label="中评 (3星)" name="mid" />
        <el-tab-pane label="差评 (1-2星)" name="bad" />
        <el-tab-pane label="有图" name="image" />
        <el-tab-pane label="追评" name="follow" />
      </el-tabs>

      <!-- 搜索栏 -->
      <el-form :model="query" class="search-bar" inline label-width="76px">
        <el-form-item label="评价人">
          <el-input v-model.trim="query.reviewerKeyword" placeholder="昵称" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="被评价人">
          <el-input v-model.trim="query.revieweeKeyword" placeholder="昵称" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="商品">
          <el-input v-model.trim="query.productKeyword" placeholder="商品标题" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model.trim="query.keyword" placeholder="评价/追评/回复内容" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item label="星级">
          <el-select v-model="query.stars" placeholder="全部" clearable style="width: 120px">
            <el-option label="5 星" value="5" />
            <el-option label="4 星" value="4" />
            <el-option label="3 星" value="3" />
            <el-option label="2 星" value="2" />
            <el-option label="1 星" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.reviewType" placeholder="全部" clearable style="width: 140px">
            <el-option label="买家评卖家" :value="1" />
            <el-option label="卖家评买家" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="query.range"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <span class="toolbar__title">评价列表</span>
        <span class="toolbar__meta">
          共 {{ total }} 条
          <el-tag v-if="isReadOnly" type="warning" size="small" effect="plain" class="ml8">客服只读</el-tag>
        </span>
      </div>

      <el-table v-loading="loading" :data="records" stripe border>
        <el-table-column label="ID" prop="id" width="72" align="center" />
        <el-table-column label="商品" min-width="170">
          <template #default="{ row }">
            <div class="cell-product">
              <el-image v-if="row.productCover" :src="row.productCover" class="cell-product__img" fit="cover" />
              <span class="cell-product__title">{{ row.productTitle || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="评价人 → 被评价人" min-width="190">
          <template #default="{ row }">
            <div class="cell-users">
              <span class="cell-users__name">
                {{ row.reviewerNickname }}
                <el-tag v-if="row.isAnonymous" size="small" type="info" effect="plain">匿名</el-tag>
              </span>
              <span class="cell-users__arrow">→</span>
              <span class="cell-users__name">{{ row.revieweeNickname }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="106" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reviewType === 1 ? 'primary' : 'success'" effect="plain">
              {{ row.reviewTypeLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="星级" width="150" align="center">
          <template #default="{ row }">
            <el-rate :model-value="row.stars" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="内容摘要" min-width="200">
          <template #default="{ row }">
            <span class="cell-content">{{ row.content || '（未填写文字）' }}</span>
            <el-tag v-if="row.followContent" size="small" type="warning" effect="plain" class="ml6">追评</el-tag>
            <el-tag v-if="row.replyContent" size="small" type="success" effect="plain" class="ml6">已回复</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图片" width="120" align="center">
          <template #default="{ row }">
            <div v-if="row.images && row.images.length" class="cell-images">
              <el-image
                :src="row.images[0]"
                :preview-src-list="row.images"
                class="cell-images__item"
                fit="cover"
                preview-teleported
              />
              <span v-if="row.images.length > 1" class="cell-images__more">+{{ row.images.length - 1 }}</span>
            </div>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" prop="createTime" width="160" align="center" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="isAdmin" link type="danger" @click="handleDelete(row)">删除</el-button>
            <span v-else class="muted">只读</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无评价数据" :image-size="90" />
        </template>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="评价详情" width="760px" destroy-on-close>
      <div v-if="detail" class="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="评价 ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="商品">{{ detail.productTitle }}</el-descriptions-item>
          <el-descriptions-item label="评价方向">{{ detail.reviewTypeLabel }}</el-descriptions-item>
          <el-descriptions-item label="评价人">
            {{ detail.reviewerNickname }}
            <el-tag v-if="detail.isAnonymous" size="small" type="info" effect="plain" class="ml6">匿名</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="被评价人">
            {{ detail.revieweeNickname }}
            <el-tag size="small" :color="detail.revieweeLevelColor" effect="dark" class="ml6">
              {{ detail.revieweeLevelLabel }}
            </el-tag>
            <span class="muted ml6">{{ detail.revieweeScore }} 分</span>
          </el-descriptions-item>
          <el-descriptions-item label="评价时间" :span="2">{{ detail.createTime }}</el-descriptions-item>
        </el-descriptions>

        <!-- 维度：按方向只展示该方向存在的维度，不用 0 分占位 -->
        <div class="detail__block">
          <div class="detail__label">评分维度</div>
          <div class="dimension">
            <div v-if="detail.descStars" class="dimension__item">
              <span>商品描述相符</span><el-rate :model-value="detail.descStars" disabled size="small" />
            </div>
            <div v-if="detail.shipStars" class="dimension__item">
              <span>发货速度</span><el-rate :model-value="detail.shipStars" disabled size="small" />
            </div>
            <div v-if="detail.payStars" class="dimension__item">
              <span>付款速度</span><el-rate :model-value="detail.payStars" disabled size="small" />
            </div>
            <div v-if="detail.attitudeStars" class="dimension__item">
              <span>沟通态度</span><el-rate :model-value="detail.attitudeStars" disabled size="small" />
            </div>
            <div class="dimension__item dimension__item--total">
              <span>综合星级</span><el-rate :model-value="detail.stars" disabled />
            </div>
          </div>
        </div>

        <div class="detail__block">
          <div class="detail__label">评价内容</div>
          <div class="detail__text">{{ detail.content || '（未填写文字）' }}</div>
          <div v-if="detail.images && detail.images.length" class="detail__images">
            <el-image
              v-for="(img, index) in detail.images"
              :key="index"
              :src="img"
              :preview-src-list="detail.images"
              :initial-index="index"
              class="detail__image"
              fit="cover"
              preview-teleported
            />
          </div>
        </div>

        <div v-if="detail.followContent || (detail.followImages && detail.followImages.length)" class="detail__block">
          <div class="detail__label detail__label--follow">追评 · {{ detail.followTime }}</div>
          <div class="detail__text">{{ detail.followContent || '（未填写文字）' }}</div>
          <div v-if="detail.followImages && detail.followImages.length" class="detail__images">
            <el-image
              v-for="(img, index) in detail.followImages"
              :key="index"
              :src="img"
              :preview-src-list="detail.followImages"
              :initial-index="index"
              class="detail__image"
              fit="cover"
              preview-teleported
            />
          </div>
        </div>

        <div v-if="detail.replyContent" class="detail__block">
          <div class="detail__label detail__label--reply">卖家回复 · {{ detail.replyTime }}</div>
          <div class="detail__text">{{ detail.replyContent }}</div>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="isAdmin && detail" type="danger" @click="handleDelete(detail)">删除该评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { pageReviews, deleteReview } from '@/api/review'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
const isReadOnly = computed(() => userStore.isReadOnly)

const activeTab = ref('all')
const loading = ref(false)
const records = ref([])
const total = ref(0)
const detailVisible = ref(false)
const detail = ref(null)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  reviewerKeyword: '',
  revieweeKeyword: '',
  productKeyword: '',
  keyword: '',
  stars: '',
  reviewType: '',
  range: [],
  minStars: null,
  maxStars: null,
  hasImage: null,
  hasFollow: null
})

/**
 * Tab → 星级区间 / 布尔条件。
 * 好评中评差评本质就是星级范围，不需要后端再定义一套枚举。
 */
const applyTab = tab => {
  query.minStars = null
  query.maxStars = null
  query.hasImage = null
  query.hasFollow = null
  query.stars = ''
  if (tab === 'good') {
    query.minStars = 4
    query.maxStars = 5
  } else if (tab === 'mid') {
    query.minStars = 3
    query.maxStars = 3
  } else if (tab === 'bad') {
    query.minStars = 1
    query.maxStars = 2
  } else if (tab === 'image') {
    query.hasImage = true
  } else if (tab === 'follow') {
    query.hasFollow = true
  }
}

const buildParams = () => {
  const params = {
    pageNum: query.pageNum,
    pageSize: query.pageSize,
    reviewType: query.reviewType || undefined,
    reviewerKeyword: query.reviewerKeyword || undefined,
    revieweeKeyword: query.revieweeKeyword || undefined,
    productKeyword: query.productKeyword || undefined,
    keyword: query.keyword || undefined,
    minStars: query.minStars ?? undefined,
    maxStars: query.maxStars ?? undefined,
    hasImage: query.hasImage ?? undefined,
    hasFollow: query.hasFollow ?? undefined
  }
  // 星级下拉会覆盖 Tab 的区间：用户明确选了 5 星，就只看 5 星
  if (query.stars) {
    params.minStars = Number(query.stars)
    params.maxStars = Number(query.stars)
  }
  if (query.range && query.range.length === 2) {
    params.startTime = query.range[0]
    params.endTime = query.range[1]
  }
  return params
}

const loadList = async () => {
  loading.value = true
  try {
    const data = await pageReviews(buildParams())
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  applyTab(activeTab.value)
  query.pageNum = 1
  loadList()
}

const handleSearch = () => {
  query.pageNum = 1
  loadList()
}

const handleReset = () => {
  query.reviewerKeyword = ''
  query.revieweeKeyword = ''
  query.productKeyword = ''
  query.keyword = ''
  query.stars = ''
  query.reviewType = ''
  query.range = []
  query.pageNum = 1
  applyTab(activeTab.value)
  loadList()
}

const openDetail = row => {
  detail.value = row
  detailVisible.value = true
}

const handleDelete = async row => {
  await ElMessageBox.confirm(
    `确定删除这条评价吗？删除后不可恢复，且不会回退被评价人的信誉分。`,
    '删除违规评价',
    { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
  )
  await deleteReview(row.id)
  ElMessage.success('评价已删除')
  detailVisible.value = false
  loadList()
}

onMounted(() => {
  applyTab(activeTab.value)
  loadList()
})
</script>

<style scoped>
.tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.toolbar__meta {
  font-size: 13px;
  color: #909399;
}

.ml6 {
  margin-left: 6px;
}

.ml8 {
  margin-left: 8px;
}

.muted {
  color: #c0c4cc;
  font-size: 13px;
}

.cell-product {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cell-product__img {
  width: 34px;
  height: 34px;
  border-radius: 6px;
  flex-shrink: 0;
}

.cell-product__title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-users {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.cell-users__name {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #303133;
}

.cell-users__arrow {
  color: #c0c4cc;
}

.cell-content {
  font-size: 13px;
  color: #606266;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cell-images {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.cell-images__item {
  width: 40px;
  height: 40px;
  border-radius: 6px;
}

.cell-images__more {
  margin-left: 4px;
  font-size: 12px;
  color: #909399;
}

.detail__block {
  margin-top: 16px;
}

.detail__label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
  line-height: 1.2;
}

.detail__label--follow {
  border-left-color: #e6a23c;
}

.detail__label--reply {
  border-left-color: #67c23a;
}

.detail__text {
  font-size: 14px;
  color: #606266;
  line-height: 1.7;
  background: #fafafa;
  border-radius: 8px;
  padding: 10px 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail__images {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.detail__image {
  width: 96px;
  height: 96px;
  border-radius: 8px;
}

.dimension {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  align-items: center;
}

.dimension__item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}

.dimension__item--total {
  padding-left: 12px;
  border-left: 1px dashed #dcdfe6;
  font-weight: 600;
  color: #303133;
}
</style>
