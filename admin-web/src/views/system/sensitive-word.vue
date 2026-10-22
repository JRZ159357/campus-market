<!-- 文件路径: admin-web/src/views/system/sensitive-word.vue -->
<template>
  <div class="page-container">
    <div class="page-card">
      <div class="head">
        <div class="head__text">
          <h2 class="head__title">敏感词管理</h2>
          <p class="head__desc">
            词典在服务启动时加载进内存，发布商品、提交评价、发送聊天消息三处共用同一套匹配规则。
            改动后立即生效，无需重启服务。
          </p>
        </div>
        <div class="head__actions">
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          <el-button v-permission="'admin'" :icon="Upload" @click="openBatch">批量添加</el-button>
          <el-button v-permission="'admin'" type="primary" :icon="Plus" @click="openCreate">
            新增敏感词
          </el-button>
        </div>
      </div>

      <!-- 两级策略说明：审核员第一次进这个页面时最需要知道的就是这张卡片 -->
      <el-alert type="info" :closable="false" show-icon class="level-tip">
        <template #title>
          <span class="level-tip__title">两级过滤策略</span>
        </template>
        <div class="level-tip__body">
          <span class="level-tip__item">
            <el-tag size="small" effect="light" color="#fdf6ec" style="color: #e6a23c; border-color: #f5dab1">
              警告
            </el-tag>
            放行内容，但标记「待人工复核」并通知管理员（商品场景）
          </span>
          <span class="level-tip__item">
            <el-tag size="small" effect="light" color="#fef0f0" style="color: #f56c6c; border-color: #fbc4c4">
              拦截
            </el-tag>
            直接拒绝，聊天场景替换为 **
          </span>
        </div>
      </el-alert>

      <!-- 类型 Tab -->
      <el-tabs v-model="activeType" class="type-tabs" @tab-change="handleTypeChange">
        <el-tab-pane v-for="tab in tabs" :key="tab.name" :name="tab.name">
          <template #label>
            <span>{{ tab.label }}</span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 筛选 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            placeholder="搜索敏感词"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 错误态 -->
      <el-result
        v-if="error"
        icon="error"
        title="敏感词数据加载失败"
        sub-title="请检查后端服务是否启动、网络是否正常"
      >
        <template #extra>
          <el-button type="primary" @click="loadData">重新加载</el-button>
        </template>
      </el-result>

      <template v-else>
        <el-table v-loading="loading" :data="records" border style="width: 100%">
          <el-table-column label="敏感词" min-width="180">
            <template #default="{ row }">
              <span class="word-cell">{{ row.word }}</span>
            </template>
          </el-table-column>

          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag
                size="small"
                effect="light"
                :style="{
                  color: typeMeta(row.type).color,
                  borderColor: typeMeta(row.type).color + '66',
                  background: typeMeta(row.type).color + '14'
                }"
              >
                {{ row.typeLabel }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="等级" width="110">
            <template #default="{ row }">
              <el-tag
                size="small"
                effect="dark"
                :style="{
                  background: levelMeta(row.level).color,
                  borderColor: levelMeta(row.level).color
                }"
              >
                {{ row.levelLabel }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                {{ row.statusLabel }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">
              <span class="cell-time">{{ String(row.createTime || '').slice(0, 16) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'admin'" link type="primary" @click="openEdit(row)">
                编辑
              </el-button>
              <el-button v-permission="'admin'" link type="warning" @click="toggleStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button v-permission="'admin'" link type="danger" @click="handleDelete(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty
          v-if="!loading && !records.length"
          :description="emptyText"
          :image-size="90"
          class="empty"
        />

        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[20, 50, 100]"
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
      :title="form.id ? '编辑敏感词' : '新增敏感词'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="86px">
        <el-form-item label="敏感词" prop="word">
          <el-input
            v-model.trim="form.word"
            maxlength="50"
            show-word-limit
            placeholder="例如：加微信"
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
            <el-option
              v-for="t in SENSITIVE_TYPES"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="等级" prop="level">
          <el-radio-group v-model="form.level">
            <el-radio v-for="l in SENSITIVE_LEVELS" :key="l.value" :value="l.value">
              {{ l.label }}
            </el-radio>
          </el-radio-group>
          <div class="form-hint">{{ levelMeta(form.level).hint }}</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量添加 -->
    <el-dialog v-model="batchVisible" title="批量添加敏感词" width="560px" destroy-on-close>
      <el-form label-width="86px">
        <el-form-item label="敏感词">
          <el-input
            v-model="batch.text"
            type="textarea"
            :rows="10"
            maxlength="20000"
            placeholder="一行一个敏感词，例如：&#10;走私&#10;加微信&#10;刷单"
          />
        </el-form-item>
        <el-form-item label="统一类型">
          <el-select v-model="batch.type" style="width: 100%">
            <el-option
              v-for="t in SENSITIVE_TYPES"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="统一等级">
          <el-radio-group v-model="batch.level">
            <el-radio v-for="l in SENSITIVE_LEVELS" :key="l.value" :value="l.value">
              {{ l.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <!-- 实时识别数量：让管理员在提交前就知道会导入多少条 -->
      <div class="batch-stat">
        <span>已识别 <b>{{ batchCount }}</b> 个敏感词</span>
        <span v-if="batchDuplicatedHint" class="batch-stat__hint">
          （已自动去除 {{ batchDuplicatedHint }} 个重复行）
        </span>
      </div>

      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!batchCount"
          @click="submitBatch"
        >
          导入 {{ batchCount }} 条
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量结果 -->
    <el-dialog v-model="resultVisible" title="导入结果" width="480px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="识别">{{ batchResult.total }} 条</el-descriptions-item>
        <el-descriptions-item label="新增">{{ batchResult.inserted }} 条</el-descriptions-item>
        <el-descriptions-item label="已存在跳过">
          {{ batchResult.duplicated }} 条
        </el-descriptions-item>
        <el-descriptions-item label="格式非法">{{ batchResult.invalid }} 条</el-descriptions-item>
      </el-descriptions>
      <div v-if="batchResult.duplicatedWords?.length" class="result-words">
        <div class="result-words__label">已存在：</div>
        <div class="result-words__list">{{ batchResult.duplicatedWords.join('、') }}</div>
      </div>
      <div v-if="batchResult.invalidWords?.length" class="result-words">
        <div class="result-words__label">格式非法（超过50字）：</div>
        <div class="result-words__list">{{ batchResult.invalidWords.join('、') }}</div>
      </div>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, RefreshLeft, Search, Upload } from '@element-plus/icons-vue'
import {
  SENSITIVE_LEVELS,
  SENSITIVE_TYPES,
  batchCreateSensitiveWords,
  createSensitiveWord,
  deleteSensitiveWord,
  levelMeta,
  pageSensitiveWords,
  typeMeta,
  updateSensitiveWord,
  updateSensitiveWordStatus
} from '@/api/sensitiveWord'

const tabs = [
  { name: 'all', label: '全部' },
  { name: '1', label: '违禁品' },
  { name: '2', label: '辱骂' },
  { name: '3', label: '广告' },
  { name: '4', label: '其他' }
]

const activeType = ref('all')
const query = reactive({ pageNum: 1, pageSize: 20, type: null, status: null, keyword: '' })
const records = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref(false)

const emptyText = computed(() => {
  if (query.keyword) return '没有匹配的敏感词'
  return activeType.value === 'all' ? '词典还是空的，点击右上角新增或批量添加' : '该分类下暂无敏感词'
})

const loadData = async () => {
  loading.value = true
  error.value = false
  try {
    const data = await pageSensitiveWords({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      type: query.type ?? undefined,
      status: query.status ?? undefined,
      keyword: query.keyword || undefined
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

const handleTypeChange = name => {
  query.type = name === 'all' ? null : Number(name)
  query.pageNum = 1
  loadData()
}

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.keyword = ''
  query.status = null
  query.pageNum = 1
  loadData()
}

// ---------- 新增 / 编辑 ----------
const formVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ id: null, word: '', type: 4, level: 1, status: 1 })

const formRules = {
  word: [
    { required: true, message: '请输入敏感词', trigger: 'blur' },
    { max: 50, message: '不能超过50个字', trigger: 'blur' }
  ],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  level: [{ required: true, message: '请选择等级', trigger: 'change' }]
}

const openCreate = () => {
  form.id = null
  form.word = ''
  form.type = activeType.value === 'all' ? 4 : Number(activeType.value)
  form.level = 1
  form.status = 1
  formVisible.value = true
}

const openEdit = row => {
  form.id = row.id
  form.word = row.word
  form.type = row.type
  form.level = row.level
  form.status = row.status
  formVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        word: form.word,
        type: form.type,
        level: form.level,
        status: form.status
      }
      if (form.id) {
        await updateSensitiveWord(form.id, payload)
        ElMessage.success('敏感词已更新')
      } else {
        await createSensitiveWord(payload)
        ElMessage.success('敏感词已添加')
      }
      formVisible.value = false
      loadData()
    } catch (e) {
      // 错误提示由 request 拦截器统一弹出
    } finally {
      submitting.value = false
    }
  })
}

const toggleStatus = async row => {
  const next = row.status === 1 ? 0 : 1
  try {
    await updateSensitiveWordStatus(row.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已禁用')
    loadData()
  } catch (e) {
    /* 已由拦截器提示 */
  }
}

const handleDelete = async row => {
  try {
    await ElMessageBox.confirm(
      `确认删除敏感词「${row.word}」吗？删除后该词不再参与过滤，历史内容不受影响。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteSensitiveWord(row.id)
    ElMessage.success('已删除')
    // 删掉当前页最后一条时回退一页，避免停在空白页
    if (records.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    loadData()
  } catch (e) {
    /* 已由拦截器提示 */
  }
}

// ---------- 批量添加 ----------
const batchVisible = ref(false)
const resultVisible = ref(false)
const batchResult = ref({})
const batch = reactive({ text: '', type: 4, level: 1 })

const parsedLines = computed(() => {
  const lines = (batch.text || '')
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
  return lines
})

/** 去重后的有效条数 */
const batchCount = computed(() => new Set(parsedLines.value).size)

/** 同一份文本里重复了几行（与后端「已识别」口径保持一致：先按行去重） */
const batchDuplicatedHint = computed(() => parsedLines.value.length - batchCount.value)

const openBatch = () => {
  batch.text = ''
  batch.type = activeType.value === 'all' ? 4 : Number(activeType.value)
  batch.level = 1
  batchVisible.value = true
}

const submitBatch = async () => {
  submitting.value = true
  try {
    const data = await batchCreateSensitiveWords({
      text: batch.text,
      type: batch.type,
      level: batch.level
    })
    batchVisible.value = false
    batchResult.value = data || {}
    resultVisible.value = true
    loadData()
  } catch (e) {
    /* 已由拦截器提示 */
  } finally {
    submitting.value = false
  }
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

.level-tip {
  margin-bottom: 4px;
  border-radius: 10px;
}

.level-tip__title {
  font-weight: 600;
}

.level-tip__body {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 28px;
  margin-top: 6px;
}

.level-tip__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.type-tabs {
  margin-top: 4px;
}

.filter-form {
  margin-bottom: 4px;
}

.word-cell {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.cell-time {
  font-size: 13px;
  color: #606266;
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

.batch-stat {
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
}

.batch-stat b {
  color: #409eff;
  font-size: 15px;
  padding: 0 2px;
}

.batch-stat__hint {
  color: #909399;
  font-size: 12px;
  margin-left: 6px;
}

.result-words {
  margin-top: 12px;
  font-size: 13px;
}

.result-words__label {
  color: #909399;
  margin-bottom: 4px;
}

.result-words__list {
  color: #606266;
  line-height: 1.7;
  word-break: break-all;
}
</style>
